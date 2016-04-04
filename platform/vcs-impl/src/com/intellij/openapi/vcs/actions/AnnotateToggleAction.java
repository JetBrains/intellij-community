/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.BackgroundableActionLock;
import com.intellij.openapi.vcs.impl.UpToDateLineNumberProviderImpl;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 * @author: lesya
 */
public class AnnotateToggleAction extends ToggleAction implements DumbAware, AnnotationColors {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.actions.AnnotateToggleAction");

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    final boolean enabled = isEnabled(VcsContextFactory.SERVICE.getInstance().createContextOn(e)) ||
                            AnnotateDiffViewerAction.isEnabled(e);
    e.getPresentation().setEnabled(enabled);
  }

  private static boolean isEnabled(final VcsContext context) {
    VirtualFile[] selectedFiles = context.getSelectedFiles();
    if (selectedFiles.length != 1) {
      return false;
    }
    VirtualFile file = selectedFiles[0];
    if (file.isDirectory()) return false;
    Project project = context.getProject();
    if (project == null || project.isDisposed()) return false;

    if (getBackgroundableLock(project, file).isLocked()) return false;

    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    if (vcs == null) return false;
    final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
    if (annotationProvider == null) return false;
    final FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(file);
    if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED || fileStatus == FileStatus.IGNORED) {
      return false;
    }
    return hasTextEditor(file);
  }

  private static boolean hasTextEditor(@NotNull VirtualFile selectedFile) {
    return !selectedFile.getFileType().isBinary();
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    VcsContext context = VcsContextFactory.SERVICE.getInstance().createContextOn(e);
    Editor editor = context.getEditor();
    if (editor != null) {
      return isAnnotated(editor);
    }
    VirtualFile selectedFile = context.getSelectedFile();
    if (selectedFile == null) {
      return false;
    }

    Project project = context.getProject();
    if (project == null) return false;

    for (FileEditor fileEditor : FileEditorManager.getInstance(project).getEditors(selectedFile)) {
      if (fileEditor instanceof TextEditor) {
        if (isAnnotated(((TextEditor)fileEditor).getEditor())) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isAnnotated(@NotNull Editor editor) {
    return editor.getGutter().isAnnotationsShown();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean selected) {
    if (AnnotateDiffViewerAction.isEnabled(e)) {
      AnnotateDiffViewerAction.perform(e);
      return;
    }

    final VcsContext context = VcsContextFactory.SERVICE.getInstance().createContextOn(e);
    Editor editor = context.getEditor();
    VirtualFile selectedFile = context.getSelectedFile();
    if (selectedFile == null) return;

    Project project = context.getProject();
    if (project == null) return;
    if (!selected) {
      for (FileEditor fileEditor : FileEditorManager.getInstance(project).getEditors(selectedFile)) {
        if (fileEditor instanceof TextEditor) {
          ((TextEditor)fileEditor).getEditor().getGutter().closeAllAnnotations();
        }
      }
    }
    else {
      if (editor == null) {
        FileEditor[] fileEditors = FileEditorManager.getInstance(project).openFile(selectedFile, false);
        for (FileEditor fileEditor : fileEditors) {
          if (fileEditor instanceof TextEditor) {
            editor = ((TextEditor)fileEditor).getEditor();
          }
        }
      }
      LOG.assertTrue(editor != null);
      doAnnotate(editor, project);
    }
  }

  private static void doAnnotate(final Editor editor, final Project project) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    if (project == null || file == null) {
      return;
    }
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);

    if (vcs == null) return;

    final AnnotationProvider annotationProvider = vcs.getCachingAnnotationProvider();
    assert annotationProvider != null;

    final Ref<FileAnnotation> fileAnnotationRef = new Ref<FileAnnotation>();
    final Ref<VcsException> exceptionRef = new Ref<VcsException>();

    getBackgroundableLock(project, file).lock();

    final Task.Backgroundable annotateTask = new Task.Backgroundable(project,
                                                                     VcsBundle.message("retrieving.annotations"),
                                                                     true) {
      @Override
      public void run(final @NotNull ProgressIndicator indicator) {
        try {
          fileAnnotationRef.set(annotationProvider.annotate(file));
        }
        catch (VcsException e) {
          exceptionRef.set(e);
        }
        catch (ProcessCanceledException pce) {
          throw pce;
        }
        catch (Throwable t) {
          exceptionRef.set(new VcsException(t));
        }
      }

      @Override
      public void onCancel() {
        onSuccess();
      }

      @Override
      public void onSuccess() {
        getBackgroundableLock(project, file).unlock();

        if (!exceptionRef.isNull()) {
          LOG.warn(exceptionRef.get());
          AbstractVcsHelper.getInstance(project).showErrors(Collections.singletonList(exceptionRef.get()), VcsBundle.message("message.title.annotate"));
        }

        if (!fileAnnotationRef.isNull()) {
          doAnnotate(editor, project, file, fileAnnotationRef.get(), vcs);
        }
      }
    };
    ProgressManager.getInstance().run(annotateTask);
  }

  public static void doAnnotate(@NotNull final Editor editor,
                                @NotNull final Project project,
                                @Nullable final VirtualFile currentFile,
                                @NotNull final FileAnnotation fileAnnotation,
                                @NotNull final AbstractVcs vcs) {
    doAnnotate(editor, project, currentFile, fileAnnotation, vcs, null);
  }

  public static void doAnnotate(@NotNull final Editor editor,
                                @NotNull final Project project,
                                @Nullable final VirtualFile currentFile,
                                @NotNull final FileAnnotation fileAnnotation,
                                @NotNull final AbstractVcs vcs,
                                @Nullable UpToDateLineNumberProvider getUpToDateLineNumber) {
    if (fileAnnotation.getFile() != null && fileAnnotation.getFile().isInLocalFileSystem()) {
      ProjectLevelVcsManager.getInstance(project).getAnnotationLocalChangesListener().registerAnnotation(fileAnnotation.getFile(), fileAnnotation);
    }

    editor.getGutter().closeAllAnnotations();

    fileAnnotation.setCloser(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed()) return;
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (project.isDisposed()) return;
            editor.getGutter().closeAllAnnotations();
          }
        });
      }
    });


    final EditorGutterComponentEx editorGutter = (EditorGutterComponentEx)editor.getGutter();
    final List<AnnotationFieldGutter> gutters = new ArrayList<AnnotationFieldGutter>();
    final AnnotationSourceSwitcher switcher = fileAnnotation.getAnnotationSourceSwitcher();
    if (getUpToDateLineNumber == null) getUpToDateLineNumber = new UpToDateLineNumberProviderImpl(editor.getDocument(), project);

    final AnnotationPresentation presentation = new AnnotationPresentation(fileAnnotation, getUpToDateLineNumber, switcher);
    if (currentFile != null && vcs.getCommittedChangesProvider() != null) {
      presentation.addAction(new ShowDiffFromAnnotation(fileAnnotation, vcs, currentFile));
    }
    presentation.addAction(new CopyRevisionNumberFromAnnotateAction(fileAnnotation));
    presentation.addAction(Separator.getInstance());

    final Couple<Map<VcsRevisionNumber, Color>> bgColorMap =
      Registry.is("vcs.show.colored.annotations") ? computeBgColors(fileAnnotation) : null;
    final Map<VcsRevisionNumber, Integer> historyIds = Registry.is("vcs.show.history.numbers") ? computeLineNumbers(fileAnnotation) : null;

    if (switcher != null) {
      switcher.switchTo(switcher.getDefaultSource());
      final LineAnnotationAspect revisionAspect = switcher.getRevisionAspect();
      final CurrentRevisionAnnotationFieldGutter currentRevisionGutter =
        new CurrentRevisionAnnotationFieldGutter(fileAnnotation, revisionAspect, presentation, bgColorMap);
      final MergeSourceAvailableMarkerGutter mergeSourceGutter =
        new MergeSourceAvailableMarkerGutter(fileAnnotation, null, presentation, bgColorMap);

      SwitchAnnotationSourceAction switchAction = new SwitchAnnotationSourceAction(switcher, editorGutter);
      presentation.addAction(switchAction);
      switchAction.addSourceSwitchListener(currentRevisionGutter);
      switchAction.addSourceSwitchListener(mergeSourceGutter);

      currentRevisionGutter.consume(switcher.getDefaultSource());
      mergeSourceGutter.consume(switcher.getDefaultSource());

      gutters.add(currentRevisionGutter);
      gutters.add(mergeSourceGutter);
    }

    final LineAnnotationAspect[] aspects = fileAnnotation.getAspects();
    for (LineAnnotationAspect aspect : aspects) {
      gutters.add(new AnnotationFieldGutter(fileAnnotation, aspect, presentation, bgColorMap));
    }


    if (historyIds != null) {
      gutters.add(new HistoryIdColumn(fileAnnotation, presentation, bgColorMap, historyIds));
    }
    gutters.add(new HighlightedAdditionalColumn(fileAnnotation, null, presentation, bgColorMap));
    final AnnotateActionGroup actionGroup = new AnnotateActionGroup(gutters, editorGutter);
    presentation.addAction(actionGroup, 1);
    gutters.add(new ExtraFieldGutter(fileAnnotation, presentation, bgColorMap, actionGroup));

    presentation.addAction(new AnnotateCurrentRevisionAction(fileAnnotation, vcs));
    presentation.addAction(new AnnotatePreviousRevisionAction(fileAnnotation, vcs));
    addActionsFromExtensions(presentation, fileAnnotation);

    for (AnnotationFieldGutter gutter : gutters) {
      final AnnotationGutterLineConvertorProxy proxy = new AnnotationGutterLineConvertorProxy(getUpToDateLineNumber, gutter);
      if (gutter.isGutterAction()) {
        editor.getGutter().registerTextAnnotation(proxy, proxy);
      }
      else {
        editor.getGutter().registerTextAnnotation(proxy);
      }
    }
  }

  private static void addActionsFromExtensions(@NotNull AnnotationPresentation presentation, @NotNull FileAnnotation fileAnnotation) {
    AnnotationGutterActionProvider[] extensions = AnnotationGutterActionProvider.EP_NAME.getExtensions();
    if (extensions.length > 0) {
      presentation.addAction(new Separator());
    }
    for (AnnotationGutterActionProvider provider : extensions) {
      presentation.addAction(provider.createAction(fileAnnotation));
    }
  }

  @Nullable
  private static Map<VcsRevisionNumber, Integer> computeLineNumbers(@NotNull FileAnnotation fileAnnotation) {
    final Map<VcsRevisionNumber, Integer> numbers = new HashMap<VcsRevisionNumber, Integer>();
    final List<VcsFileRevision> fileRevisionList = fileAnnotation.getRevisions();
    if (fileRevisionList != null) {
      int size = fileRevisionList.size();
      for (int i = 0; i < size; i++) {
        VcsFileRevision revision = fileRevisionList.get(i);
        final VcsRevisionNumber number = revision.getRevisionNumber();

        numbers.put(number, size - i);
      }
    }
    return numbers.size() < 2 ? null : numbers;
  }

  @NotNull
  private static Couple<Map<VcsRevisionNumber, Color>> computeBgColors(@NotNull FileAnnotation fileAnnotation) {
    final Map<VcsRevisionNumber, Color> commitOrderColors = new HashMap<VcsRevisionNumber, Color>();
    final Map<VcsRevisionNumber, Color> commitAuthorColors = new HashMap<VcsRevisionNumber, Color>();
    final Map<String, Color> authorColors = new HashMap<String, Color>();
    final List<VcsFileRevision> fileRevisionList = fileAnnotation.getRevisions();
    if (fileRevisionList != null) {
      final int colorsCount = BG_COLORS.length;
      final int revisionsCount = fileRevisionList.size();

      for (int i = 0; i < fileRevisionList.size(); i++) {
        VcsFileRevision revision = fileRevisionList.get(i);
        final VcsRevisionNumber number = revision.getRevisionNumber();
        final String author = revision.getAuthor();
        if (number == null) continue;

        if (!commitAuthorColors.containsKey(number)) {
          if (author != null && !authorColors.containsKey(author)) {
            final int index = authorColors.size();
            Color color = BG_COLORS[index * BG_COLORS_PRIME % colorsCount];
            authorColors.put(author, color);
          }

          commitAuthorColors.put(number, authorColors.get(author));
        }
        if (!commitOrderColors.containsKey(number)) {
          Color color = BG_COLORS[colorsCount * i / revisionsCount];
          commitOrderColors.put(number, color);
        }
      }
    }
    return Couple.of(commitOrderColors.size() > 1 ? commitOrderColors : null,
                     commitAuthorColors.size() > 1 ? commitAuthorColors : null);
  }

  @NotNull
  public static BackgroundableActionLock getBackgroundableLock(@NotNull Project project, @NotNull VirtualFile file) {
    return BackgroundableActionLock.getLock(project, VcsBackgroundableActions.ANNOTATE, file.getPath());
  }
}
