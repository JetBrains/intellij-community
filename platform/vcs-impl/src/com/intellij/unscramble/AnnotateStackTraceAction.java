// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble;

import com.intellij.execution.filters.FileHyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutterAction;
import com.intellij.openapi.editor.TextAnnotationGutterProvider;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.annotate.AnnotationSource;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.xml.util.XmlStringUtil;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.*;

public final class AnnotateStackTraceAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(AnnotateStackTraceAction.class);

  private boolean myIsLoading = false;

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    ConsoleViewImpl consoleView = ObjectUtils.tryCast(e.getData(LangDataKeys.CONSOLE_VIEW), ConsoleViewImpl.class);
    Editor editor = consoleView != null ? consoleView.getEditor() : null;
    boolean isShown = editor != null && editor.getGutter().isAnnotationsShown();
    e.getPresentation().setEnabled(editor != null && !isShown && !myIsLoading);
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    myIsLoading = true;
    Project project = e.getProject();
    ConsoleViewImpl consoleView = (ConsoleViewImpl)e.getData(LangDataKeys.CONSOLE_VIEW);
    Editor editor = consoleView != null ? consoleView.getEditor() : null;
    if (project == null || editor == null) return;
    EditorHyperlinkSupport hyperlinks = consoleView.getHyperlinks();

    ProgressManager.getInstance().run(new Task.Backgroundable(
      project, LangBundle.message("progress.title.getting.file.history"), true) {
      private final Object LOCK = new Object();
      private final MergingUpdateQueue myUpdateQueue = new MergingUpdateQueue("AnnotateStackTraceAction", 200, true, null);

      private MyActiveAnnotationGutter myGutter;

      @Override
      public void onCancel() {
        editor.getGutter().closeAllAnnotations();
      }

      @Override
      public void onFinished() {
        myIsLoading = false;
        Disposer.dispose(myUpdateQueue);
      }

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        Map<VirtualFile, IntList> files2lines = CollectionFactory.createSmallMemoryFootprintMap();
        Int2ObjectMap<LastRevision> revisions = new Int2ObjectOpenHashMap<>();

        ApplicationManager.getApplication().runReadAction(() -> {
          for (int line = 0; line < editor.getDocument().getLineCount(); line++) {
            indicator.checkCanceled();
            VirtualFile file = getHyperlinkVirtualFile(hyperlinks.findAllHyperlinksOnLine(line));
            if (file == null) continue;

            files2lines.computeIfAbsent(file, __ -> new IntArrayList()).add(line);
          }
        });

        for (Map.Entry<VirtualFile, IntList> entry : files2lines.entrySet()) {
          VirtualFile file = entry.getKey();
          IntList value = entry.getValue();
          indicator.checkCanceled();
          LastRevision revision = getLastRevision(file);
          if (revision == null) {
            continue;
          }
          synchronized (LOCK) {
            for (IntListIterator iterator = value.iterator(); iterator.hasNext(); ) {
              revisions.put(iterator.nextInt(), revision);
            }
          }

          myUpdateQueue.queue(new Update("update") {
            @Override
            public void run() {
              updateGutter(indicator, revisions);
            }
          });
        }

        // myUpdateQueue can be disposed before the last revisions are passed to the gutter
        ApplicationManager.getApplication().invokeLater(() -> updateGutter(indicator, revisions));
      }

      @RequiresEdt
      private void updateGutter(@NotNull ProgressIndicator indicator, @NotNull Map<Integer, LastRevision> revisions) {
        if (indicator.isCanceled()) return;

        if (myGutter == null) {
          myGutter = new MyActiveAnnotationGutter(getProject(), hyperlinks, indicator);
          editor.getGutter().registerTextAnnotation(myGutter, myGutter);
        }

        Map<Integer, LastRevision> revisionsCopy;
        synchronized (LOCK) {
          revisionsCopy = new HashMap<>(revisions);
        }

        myGutter.updateData(revisionsCopy);
        ((EditorGutterComponentEx)editor.getGutter()).revalidateMarkup();
      }

      @Nullable
      private LastRevision getLastRevision(@NotNull VirtualFile file) {
        try {
          AbstractVcs vcs = VcsUtil.getVcsFor(project, file);
          if (vcs == null) return null;

          VcsHistoryProvider historyProvider = vcs.getVcsHistoryProvider();
          if (historyProvider == null) return null;

          FilePath filePath = VcsContextFactory.getInstance().createFilePathOn(file);

          if (historyProvider instanceof VcsHistoryProviderEx) {
            VcsFileRevision revision = ((VcsHistoryProviderEx)historyProvider).getLastRevision(filePath);
            if (revision == null) return null;
            return LastRevision.create(revision);
          }
          else {
            VcsHistorySession session = historyProvider.createSessionFor(filePath);
            if (session == null) return null;

            List<VcsFileRevision> list = session.getRevisionList();
            if (list == null || list.isEmpty()) return null;

            return LastRevision.create(list.get(0));
          }
        }
        catch (VcsException e) {
          LOG.warn(e);
          return null;
        }
      }
    });
  }

  @Nullable
  @RequiresReadLock
  private static VirtualFile getHyperlinkVirtualFile(@NotNull List<? extends RangeHighlighter> links) {
    RangeHighlighter key = ContainerUtil.getLastItem(links);
    if (key == null) return null;
    HyperlinkInfo info = EditorHyperlinkSupport.getHyperlinkInfo(key);
    if (!(info instanceof FileHyperlinkInfo)) return null;
    OpenFileDescriptor descriptor = ((FileHyperlinkInfo)info).getDescriptor();
    return descriptor != null ? descriptor.getFile() : null;
  }

  private static class LastRevision {
    @NotNull private final VcsRevisionNumber myNumber;
    @NotNull private final String myAuthor;
    @NotNull private final Date myDate;
    @NotNull private final String myMessage;

    LastRevision(@NotNull VcsRevisionNumber number, @NotNull String author, @NotNull Date date, @NotNull String message) {
      myNumber = number;
      myAuthor = author;
      myDate = date;
      myMessage = message;
    }

    @NotNull
    public static LastRevision create(@NotNull VcsFileRevision revision) {
      VcsRevisionNumber number = revision.getRevisionNumber();
      String author = StringUtil.notNullize(revision.getAuthor(), VcsBundle.message("vfs.revision.author.unknown"));
      Date date = revision.getRevisionDate();
      String message = StringUtil.notNullize(revision.getCommitMessage());
      return new LastRevision(number, author, date, message);
    }

    @NotNull
    public VcsRevisionNumber getNumber() {
      return myNumber;
    }

    @NotNull
    @NlsSafe
    public String getAuthor() {
      return myAuthor;
    }

    @NotNull
    public Date getDate() {
      return myDate;
    }

    @NotNull
    public String getMessage() {
      return myMessage;
    }
  }

  private static class MyActiveAnnotationGutter implements TextAnnotationGutterProvider, EditorGutterAction {
    @NotNull private final Project myProject;
    @NotNull private final EditorHyperlinkSupport myHyperlinks;
    @NotNull private final ProgressIndicator myIndicator;

    @NotNull private Map<Integer, LastRevision> myRevisions = Collections.emptyMap();
    private Date myNewestDate = null;
    private int myMaxDateLength = 0;

    MyActiveAnnotationGutter(@NotNull Project project,
                             @NotNull EditorHyperlinkSupport hyperlinks,
                             @NotNull ProgressIndicator indicator) {
      myProject = project;
      myHyperlinks = hyperlinks;
      myIndicator = indicator;
    }

    @Override
    public void doAction(int lineNum) {
      LastRevision revision = myRevisions.get(lineNum);
      if (revision == null) return;

      VirtualFile file = getHyperlinkVirtualFile(myHyperlinks.findAllHyperlinksOnLine(lineNum));
      if (file == null) return;

      AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
      if (vcs != null) {
        VcsRevisionNumber number = revision.getNumber();
        VcsKey vcsKey = vcs.getKeyInstanceMethod();
        ShowAllAffectedGenericAction.showSubmittedFiles(myProject, number, file, vcsKey);
      }
    }

    @Override
    public Cursor getCursor(int lineNum) {
      return myRevisions.containsKey(lineNum) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor();
    }

    @Override
    public String getLineText(int line, Editor editor) {
      LastRevision revision = myRevisions.get(line);
      if (revision != null) {
        return String.format("%" + myMaxDateLength + "s", FileAnnotation.formatDate(revision.getDate())) + " " + revision.getAuthor();
      }
      return "";
    }

    @Override
    public String getToolTip(int line, Editor editor) {
      LastRevision revision = myRevisions.get(line);
      if (revision != null) {
        return XmlStringUtil.escapeString(
          revision.getAuthor() + " " + DateFormatUtil.formatDateTime(revision.getDate()) + "\n" +
          VcsUtil.trimCommitMessageToSaneSize(revision.getMessage())
        );
      }
      return null;
    }

    @Override
    public EditorFontType getStyle(int line, Editor editor) {
      LastRevision revision = myRevisions.get(line);
      return revision != null && revision.getDate().equals(myNewestDate) ? EditorFontType.BOLD : EditorFontType.PLAIN;
    }

    @Override
    public ColorKey getColor(int line, Editor editor) {
      return AnnotationSource.LOCAL.getColor();
    }

    @Override
    public Color getBgColor(int line, Editor editor) {
      return null;
    }

    @Override
    public List<AnAction> getPopupActions(int line, Editor editor) {
      return Collections.emptyList();
    }

    @Override
    public void gutterClosed() {
      myIndicator.cancel();
    }

    @RequiresEdt
    public void updateData(@NotNull Map<Integer, LastRevision> revisions) {
      myRevisions = revisions;

      Date newestDate = null;
      int maxDateLength = 0;

      for (LastRevision revision : myRevisions.values()) {
        Date date = revision.getDate();
        if (newestDate == null || date.after(newestDate)) {
          newestDate = date;
        }
        int length = DateFormatUtil.formatPrettyDate(date).length();
        if (length > maxDateLength) {
          maxDateLength = length;
        }
      }

      myNewestDate = newestDate;
      myMaxDateLength = maxDateLength;
    }
  }
}
