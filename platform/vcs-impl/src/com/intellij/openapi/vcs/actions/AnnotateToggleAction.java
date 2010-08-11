/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vcs.changes.ui.ChangesComparator;
import com.intellij.openapi.vcs.changes.ui.ChangesViewBalloonProblemNotifier;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.BackgroundableActionEnabledHandler;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.UpToDateLineNumberProviderImpl;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author: lesya
 * @author Konstantin Bulenkov
 */
public class AnnotateToggleAction extends ToggleAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.actions.AnnotateToggleAction");
  protected static final Key<Collection<ActiveAnnotationGutter>> KEY_IN_EDITOR = Key.create("Annotations");
  private final static Color[] BG_COLORS = {
    new Color(222, 241, 229),
    new Color(234, 255, 226),
    new Color(208, 229, 229),
    new Color(255, 226, 199),
    new Color(227, 226, 223),
    new Color(255, 213, 203),
    new Color(220, 204, 236),
    new Color(255, 191, 195),
    new Color(243, 223, 243),
    new Color(217, 228, 249),
    new Color(255, 251, 207),
    new Color(217, 222, 229),
    new Color(255, 204, 238),
    new Color(236, 236, 236)};

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(VcsContextFactory.SERVICE.getInstance().createContextOn(e)));
  }

  private static boolean isEnabled(final VcsContext context) {
    VirtualFile[] selectedFiles = context.getSelectedFiles();
    if (selectedFiles == null) return false;
    if (selectedFiles.length != 1) return false;
    VirtualFile file = selectedFiles[0];
    if (file.isDirectory()) return false;
    Project project = context.getProject();
    if (project == null || project.isDisposed()) return false;

    final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(project);
    final BackgroundableActionEnabledHandler handler = ((ProjectLevelVcsManagerImpl)plVcsManager)
      .getBackgroundableActionHandler(VcsBackgroundableActions.ANNOTATE);
    if (handler.isInProgress(file.getPath())) return false;

    AbstractVcs vcs = plVcsManager.getVcsFor(file);
    if (vcs == null) return false;
    final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
    if (annotationProvider == null) return false;
    final FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(file);
    if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED) {
      return false;
    }
    return hasTextEditor(file);
  }

  private static boolean hasTextEditor(VirtualFile selectedFile) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType fileType = fileTypeManager.getFileTypeByFile(selectedFile);
    return !fileType.isBinary() && fileType != StdFileTypes.GUI_DESIGNER_FORM;
  }

  public boolean isSelected(AnActionEvent e) {
    VcsContext context = VcsContextFactory.SERVICE.getInstance().createContextOn(e);
    Editor editor = context.getEditor();
    if (editor == null) return false;
    Collection annotations = editor.getUserData(KEY_IN_EDITOR);
    return annotations != null && !annotations.isEmpty();
  }

  public void setSelected(AnActionEvent e, boolean state) {
    VcsContext context = VcsContextFactory.SERVICE.getInstance().createContextOn(e);
    Editor editor = context.getEditor();
    if (!state) {
      if (editor != null) {
        editor.getGutter().closeAllAnnotations();
      }
    }
    else {
      if (editor == null) {
        VirtualFile selectedFile = context.getSelectedFile();
        FileEditor[] fileEditors = FileEditorManager.getInstance(context.getProject()).openFile(selectedFile, false);
        for (FileEditor fileEditor : fileEditors) {
          if (fileEditor instanceof TextEditor) {
            editor = ((TextEditor)fileEditor).getEditor();
          }
        }
      }

      LOG.assertTrue(editor != null);

      doAnnotate(editor, context.getProject());

    }
  }

  private static void doAnnotate(final Editor editor, final Project project) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    if (project == null) return;
    final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(project);
    final AbstractVcs vcs = plVcsManager.getVcsFor(file);
    if (vcs == null) return;
    final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();

    final Ref<FileAnnotation> fileAnnotationRef = new Ref<FileAnnotation>();
    final Ref<VcsException> exceptionRef = new Ref<VcsException>();

    final BackgroundableActionEnabledHandler handler = ((ProjectLevelVcsManagerImpl)plVcsManager).getBackgroundableActionHandler(
      VcsBackgroundableActions.ANNOTATE);
    handler.register(file.getPath());

    ProgressManager.getInstance().run(new Task.Backgroundable(project, VcsBundle.message("retrieving.annotations"), true,
        BackgroundFromStartOption.getInstance()) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          fileAnnotationRef.set(annotationProvider.annotate(file));
        }
        catch (VcsException e) {
          exceptionRef.set(e);
        }
      }

      @Override
      public void onCancel() {
        onSuccess();
      }

      @Override
      public void onSuccess() {
        handler.completed(file.getPath());
        
        if (! exceptionRef.isNull()) {
          AbstractVcsHelper.getInstance(project).showErrors(Arrays.asList(exceptionRef.get()), VcsBundle.message("message.title.annotate"));
        }
        if (fileAnnotationRef.isNull()) return;

        doAnnotate(editor, project, file, fileAnnotationRef.get(), vcs);
      }
    });
  }

  public static void doAnnotate(final Editor editor,
                                final Project project,
                                final VirtualFile file,
                                final FileAnnotation fileAnnotation,
                                final AbstractVcs vcs) {
    String upToDateContent = fileAnnotation.getAnnotatedContent();

    final UpToDateLineNumberProvider getUpToDateLineNumber = new UpToDateLineNumberProviderImpl(
      editor.getDocument(),
      project,
      upToDateContent);

    editor.getGutter().closeAllAnnotations();

    // be careful, not proxies but original items are put there (since only their presence not behaviour is important)
    Collection<ActiveAnnotationGutter> annotations = editor.getUserData(KEY_IN_EDITOR);
    if (annotations == null) {
      annotations = new HashSet<ActiveAnnotationGutter>();
      editor.putUserData(KEY_IN_EDITOR, annotations);
    }

    final EditorGutterComponentEx editorGutterComponentEx = (EditorGutterComponentEx)editor.getGutter();
    final HighlightAnnotationsActions highlighting = new HighlightAnnotationsActions(project, file, fileAnnotation, editorGutterComponentEx);
    final List<AnnotationFieldGutter> gutters = new ArrayList<AnnotationFieldGutter>();
    final AnnotationSourceSwitcher switcher = fileAnnotation.getAnnotationSourceSwitcher();
    final MyAnnotationPresentation presentation;
    if (vcs.getCommittedChangesProvider() != null) {
      final ShowDiffFromAnnotation showDiff = new ShowDiffFromAnnotation(getUpToDateLineNumber, fileAnnotation, vcs, file);
      presentation = new MyAnnotationPresentation(highlighting, switcher, editorGutterComponentEx, gutters, showDiff);
      presentation.addLineNumberListener(showDiff);
    } else {
      presentation = new MyAnnotationPresentation(highlighting, switcher, editorGutterComponentEx, gutters);
    }

    if (switcher != null) {

      switcher.switchTo(switcher.getDefaultSource());
      final LineAnnotationAspect revisonAspect = switcher.getRevisionAspect();
      final MyCurrentRevisionAnnotationFieldGutter currentRevisionGutter =
        new MyCurrentRevisionAnnotationFieldGutter(fileAnnotation, editor, revisonAspect, presentation);
      final MyMergeSourceAvailableMarkerGutter mergeSourceGutter =
        new MyMergeSourceAvailableMarkerGutter(fileAnnotation, editor, null, presentation);

      presentation.addSourceSwitchListener(currentRevisionGutter);
      presentation.addSourceSwitchListener(mergeSourceGutter);

      currentRevisionGutter.consume(switcher.getDefaultSource());
      mergeSourceGutter.consume(switcher.getDefaultSource());

      gutters.add(currentRevisionGutter);
      gutters.add(mergeSourceGutter);
    }

    final Map<String, Color> revNumbers = Registry.is("vcs.show.colored.annotations") ? computeBgColors(fileAnnotation) : null;
    
    final LineAnnotationAspect[] aspects = fileAnnotation.getAspects();
    for (LineAnnotationAspect aspect : aspects) {
      final AnnotationFieldGutter gutter = new AnnotationFieldGutter(fileAnnotation, editor, aspect, presentation);
      gutters.add(gutter);
      gutter.setAspectValueToBgColorMap(revNumbers);
    }
    gutters.add(new MyHighlightedAdditionalColumn(fileAnnotation, editor, null, presentation, highlighting));

    for (AnnotationFieldGutter gutter : gutters) {
      final AnnotationGutterLineConvertorProxy proxy = new AnnotationGutterLineConvertorProxy(getUpToDateLineNumber, gutter);
      if (gutter.isGutterAction()) {
        editor.getGutter().registerTextAnnotation(proxy, proxy);
      }
      else {
        editor.getGutter().registerTextAnnotation(proxy);
      }
      annotations.add(gutter);
    }
  }

  @Nullable
  private static Map<String, Color> computeBgColors(FileAnnotation fileAnnotation) {
    final Map<String, Color> bgColors = new HashMap<String, Color>();
    final Map<String, Color> revNumbers = new HashMap<String, Color>();
    final int length = BG_COLORS.length;
    final List<VcsFileRevision> fileRevisionList = fileAnnotation.getRevisions();
    if (fileRevisionList != null) {
      for (VcsFileRevision revision : fileRevisionList) {
        final String author = revision.getAuthor();
        final String revNumber = revision.getRevisionNumber().asString();
        if (author != null && !bgColors.containsKey(author)) {
          final int size = bgColors.size();
          bgColors.put(author, BG_COLORS[size < length ? size : size % length]);
        }
        if (!revNumbers.containsKey(revNumber)) {
          revNumbers.put(revNumber, bgColors.get(author));          
        }
    }
    }
    return bgColors.size() < 2 ? null : revNumbers;
  }

  private static class MyHighlightedAdditionalColumn extends AnnotationFieldGutter {
    private final HighlightAnnotationsActions myHighlighting;

    private MyHighlightedAdditionalColumn(FileAnnotation annotation,
                                          Editor editor,
                                          LineAnnotationAspect aspect,
                                          TextAnnotationPresentation presentation,
                                          final HighlightAnnotationsActions highlighting) {
      super(annotation, editor, aspect, presentation);
      myHighlighting = highlighting;
    }

    @Override
    public String getLineText(int line, Editor editor) {
      return myHighlighting.isLineBold(line) ? "*" : "";
    }
  }

  // !! shown additionally only when merge
  private static class MyCurrentRevisionAnnotationFieldGutter extends AnnotationFieldGutter implements Consumer<AnnotationSource> {
    // merge source showing is turned on
    private boolean myTurnedOn;

    private MyCurrentRevisionAnnotationFieldGutter(FileAnnotation annotation,
                                                   Editor editor,
                                                   LineAnnotationAspect aspect,
                                                   TextAnnotationPresentation highlighting) {
      super(annotation, editor, aspect, highlighting);
    }

    @Override
    public ColorKey getColor(int line, Editor editor) {
      return AnnotationSource.LOCAL.getColor();
    }

    @Override
    public String getLineText(int line, Editor editor) {
      final String value = myAspect.getValue(line);
      if (String.valueOf(myAnnotation.getLineRevisionNumber(line)).equals(value)) {
        return "";
      }
      // shown in merge sources mode
      return myTurnedOn ? value : "";
    }

    @Override
    public String getToolTip(int line, Editor editor) {
      final String aspectTooltip = myAspect.getTooltipText(line);
      if (aspectTooltip != null) {
        return aspectTooltip;
      }
      final String text = getLineText(line, editor);
      return ((text == null) || (text.length() == 0)) ? "" : VcsBundle.message("annotation.original.revision.text", text);
    }

    public void consume(final AnnotationSource annotationSource) {
      myTurnedOn = annotationSource.showMerged();
    }
  }

  private static class MyMergeSourceAvailableMarkerGutter extends AnnotationFieldGutter implements Consumer<AnnotationSource> {
    // merge source showing is turned on
    private boolean myTurnedOn;

    private MyMergeSourceAvailableMarkerGutter(FileAnnotation annotation,
                                               Editor editor,
                                               LineAnnotationAspect aspect,
                                               TextAnnotationPresentation highlighting) {
      super(annotation, editor, aspect, highlighting);
    }

    @Override
    public ColorKey getColor(int line, Editor editor) {
      return AnnotationSource.LOCAL.getColor();
    }

    @Override
    public String getLineText(int line, Editor editor) {
      if (myTurnedOn) return "";
      final AnnotationSourceSwitcher switcher = myAnnotation.getAnnotationSourceSwitcher();
      if (switcher == null) return "";
      return switcher.mergeSourceAvailable(line) ? "M" : "";
    }

    public void consume(final AnnotationSource annotationSource) {
      myTurnedOn = annotationSource.showMerged();
    }
  }

  private static class MyAnnotationPresentation implements TextAnnotationPresentation {
    private final HighlightAnnotationsActions myHighlighting;
    @Nullable
    private final AnnotationSourceSwitcher mySwitcher;
    private final List<AnnotationFieldGutter> myGutters;
    private final List<AnAction> myActions;
    private MySwitchAnnotationSourceAction mySwitchAction;
    private final List<Consumer<Integer>> myPopupLineNumberListeners;

    public MyAnnotationPresentation(@NotNull final HighlightAnnotationsActions highlighting, @Nullable final AnnotationSourceSwitcher switcher,
                                    final EditorGutterComponentEx gutter, final List<AnnotationFieldGutter> gutters, final AnAction... actions) {
      myHighlighting = highlighting;
      mySwitcher = switcher;
      myGutters = gutters;
      myPopupLineNumberListeners = new LinkedList<Consumer<Integer>>();

      myActions = new ArrayList<AnAction>();
      myActions.add(Separator.getInstance());
      if (actions != null) {
        final List<AnAction> actionsList = Arrays.<AnAction>asList(actions);
        if (! actionsList.isEmpty()) {
          myActions.addAll(actionsList);
          myActions.add(new Separator());
        }
      }
      myActions.addAll(myHighlighting.getList());
      if (mySwitcher != null) {
        mySwitchAction = new MySwitchAnnotationSourceAction(mySwitcher, gutter);
        myActions.add(mySwitchAction);
      }
      myActions.add(new ShowHideColorsAction(myGutters, gutter));
    }

    public void addLineNumberListener(final Consumer<Integer> listener) {
      myPopupLineNumberListeners.add(listener);
    }

    public EditorFontType getFontType(final int line) {
      return myHighlighting.isLineBold(line) ? EditorFontType.BOLD : EditorFontType.PLAIN;
    }

    public ColorKey getColor(final int line) {
      if (mySwitcher == null) return AnnotationSource.LOCAL.getColor();
      return mySwitcher.getAnnotationSource(line).getColor();
    }

    public List<AnAction> getActions(int line) {
      for (Consumer<Integer> listener : myPopupLineNumberListeners) {
        listener.consume(line);
      }
      return myActions;
    }

    public void addSourceSwitchListener(final Consumer<AnnotationSource> listener) {
      mySwitchAction.addSourceSwitchListener(listener);
    }
  }

  private static class MySwitchAnnotationSourceAction extends AnAction {
    private final static String ourShowMerged = VcsBundle.message("annotation.switch.to.merged.text");
    private final static String ourHideMerged = VcsBundle.message("annotation.switch.to.original.text");
    private final AnnotationSourceSwitcher mySwitcher;
    private final EditorGutterComponentEx myGutter;
    private final List<Consumer<AnnotationSource>> myListeners;
    private boolean myShowMerged;

    private MySwitchAnnotationSourceAction(final AnnotationSourceSwitcher switcher, final EditorGutterComponentEx gutter) {
      mySwitcher = switcher;
      myGutter = gutter;
      myListeners = new ArrayList<Consumer<AnnotationSource>>();
      myShowMerged = mySwitcher.getDefaultSource().showMerged();
    }

    public void addSourceSwitchListener(final Consumer<AnnotationSource> listener) {
      myListeners.add(listener);
    }

    @Override
    public void update(final AnActionEvent e) {
      e.getPresentation().setText(myShowMerged ? ourHideMerged : ourShowMerged);
    }

    public void actionPerformed(AnActionEvent e) {
      myShowMerged = ! myShowMerged;
      final AnnotationSource newSource = AnnotationSource.getInstance(myShowMerged);
      mySwitcher.switchTo(newSource);
      for (Consumer<AnnotationSource> listener : myListeners) {
        listener.consume(newSource);
      }
      myGutter.revalidateMarkup();
    }
  }

  private static class ShowDiffFromAnnotation extends AnAction implements Consumer<Integer> {
    private final UpToDateLineNumberProvider myLineNumberProvider;
    private final FileAnnotation myFileAnnotation;
    private final AbstractVcs myVcs;
    private final VirtualFile myFile;
    private RepositoryLocation myLocationFor;
    private int currentLine;

    private ShowDiffFromAnnotation(final UpToDateLineNumberProvider lineNumberProvider,
                                   final FileAnnotation fileAnnotation, final AbstractVcs vcs, final VirtualFile file) {
      super(VcsBundle.message("updated.info.tree.show.diff.text"), VcsBundle.message("updated.info.tree.show.diff.text"), IconLoader.getIcon("/actions/diff.png"));
      myLineNumberProvider = lineNumberProvider;
      myFileAnnotation = fileAnnotation;
      myVcs = vcs;
      myFile = file;
      final CommittedChangesProvider provider = myVcs.getCommittedChangesProvider();
      final VirtualFile root = ProjectLevelVcsManager.getInstance(vcs.getProject()).getVcsRootFor(file);
      myLocationFor = provider.getLocationFor(new FilePathImpl(root));
      currentLine = -1;
    }

    @Override
    public void consume(Integer integer) {
      currentLine = integer;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setVisible(getActualLineNumber(e) >= 0);
    }

    private int getActualLineNumber(final AnActionEvent e) {
      final DataContext dc = e.getDataContext();
      if (currentLine < 0) return -1;
      return myLineNumberProvider.getLineNumber(currentLine);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final int actualNumber = getActualLineNumber(e);
      if (actualNumber < 0) return;

      final VcsRevisionNumber revisionNumber = myFileAnnotation.getLineRevisionNumber(actualNumber);
      if (revisionNumber != null) {
        final VcsException[] exc = new VcsException[1];
        final List<Change> changes = new LinkedList<Change>();
        ProgressManager.getInstance().run(new Task.Backgroundable(myVcs.getProject(),
                                                                  "Loading revision " + revisionNumber.asString() + " contents", true, BackgroundFromStartOption.getInstance()) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            final CommittedChangesProvider provider = myVcs.getCommittedChangesProvider();
            try {
              final CommittedChangeList cl = provider.getOneList(myLocationFor, revisionNumber);
              if (cl == null) {
                ChangesViewBalloonProblemNotifier.showMe(myVcs.getProject(), "Can not load data for show diff", MessageType.ERROR);
                return;
              }
              changes.addAll(cl.getChanges());
              Collections.sort(changes, ChangesComparator.getInstance());
            }
            catch (VcsException e1) {
              exc[0] = e1;
            }
          }

          @Override
          public void onSuccess() {
            if (exc[0] != null) {
              ChangesViewBalloonProblemNotifier.showMe(myVcs.getProject(), "Can not show diff: " + exc[0].getMessage(), MessageType.ERROR);
            } else if (! changes.isEmpty()) {
              int idx = findSelfInList(changes);
              ShowDiffAction.showDiffForChange(changes.toArray(new Change[changes.size()]), idx, myVcs.getProject());
            }
          }
        });
      }
    }

    private int findSelfInList(List<Change> changes) {
      int idx = -1;
      final File ioFile = new File(myFile.getPath());
      for (int i = 0; i < changes.size(); i++) {
        final Change change = changes.get(i);
        if ((change.getAfterRevision() != null) && (change.getAfterRevision().getFile().getIOFile().equals(ioFile))) {
          idx = i;
          break;
        }
      }
      if (idx >= 0) return idx;
      idx = 0;
      // try to use name only
      final String name = ioFile.getName();
      for (int i = 0; i < changes.size(); i++) {
        final Change change = changes.get(i);
        if ((change.getAfterRevision() != null) && (change.getAfterRevision().getFile().getName().equals(name))) {
          idx = i;
          break;
        }
      }

      return idx;
    }
  }
}
