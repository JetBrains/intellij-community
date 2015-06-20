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

import com.intellij.diff.FrameDiffTool.DiffViewer;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.base.DiffViewerBase;
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer;
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer;
import com.intellij.diff.util.Side;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.impl.BackgroundableActionEnabledHandler;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.UpToDateLineNumberProviderImpl;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.vcs.AnnotationProviderEx;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AnnotateDiffViewerAction extends DumbAwareAction {
  private static final ViewerAnnotator[] ANNOTATORS = new ViewerAnnotator[]{
    new TwosideAnnotator(), new OnesideAnnotator(), new UnifiedAnnotator()
  };

  public AnnotateDiffViewerAction() {
    super("Annotate", null, AllIcons.Actions.Annotate);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isEnabled(e));
  }

  @Nullable
  private static ViewerAnnotator getAnnotator(@NotNull DiffViewerBase viewer) {
    for (ViewerAnnotator annotator : ANNOTATORS) {
      if (annotator.getViewerClass().isInstance(viewer)) return annotator;
    }
    return null;
  }

  private static boolean isEnabled(AnActionEvent e) {
    DiffViewerBase viewer = ObjectUtils.tryCast(e.getData(DiffDataKeys.DIFF_VIEWER), DiffViewerBase.class);
    if (viewer == null) return false;
    if (viewer.getProject() == null) return false;
    if (viewer.isDisposed()) return false;

    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) return false;

    ViewerAnnotator annotator = getAnnotator(viewer);
    if (annotator == null) return false;

    //noinspection unchecked
    Side side = annotator.getCurrentSide(viewer, editor);
    if (side == null) return false;

    //noinspection unchecked
    if (annotator.isAnnotationShown(viewer, side)) return false;
    if (checkRunningProgress(viewer, side)) return false;
    return createAnnotationsLoader(viewer.getProject(), viewer.getRequest(), side) != null;
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    DiffViewerBase viewer = (DiffViewerBase)e.getRequiredData(DiffDataKeys.DIFF_VIEWER);
    Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);

    ViewerAnnotator annotator = getAnnotator(viewer);
    assert annotator != null;

    //noinspection unchecked
    Side side = annotator.getCurrentSide(viewer, editor);
    assert side != null;

    doAnnotate(annotator, viewer, side);
  }

  public static <T extends DiffViewerBase> void doAnnotate(@NotNull final ViewerAnnotator<T> annotator,
                                                           @NotNull final T viewer,
                                                           @NotNull final Side side) {
    final Project project = viewer.getProject();
    assert project != null;

    final FileAnnotationLoader loader = createAnnotationsLoader(project, viewer.getRequest(), side);
    assert loader != null;

    markRunningProgress(viewer, side, true);

    // TODO: show progress in diff viewer
    ProgressManager.getInstance().run(new Task.Backgroundable(project, VcsBundle.message("retrieving.annotations"), true,
                                                              BackgroundFromStartOption.getInstance()) {
      public void run(@NotNull ProgressIndicator indicator) {
        loader.run();
      }

      @Override
      public void onCancel() {
        onSuccess();
      }

      @Override
      public void onSuccess() {
        markRunningProgress(viewer, side, false);

        if (loader.getException() != null) {
          AbstractVcsHelper.getInstance(myProject).showError(loader.getException(), VcsBundle.message("operation.name.annotate"));
        }
        if (loader.getResult() != null) {
          if (viewer.isDisposed()) return;
          annotator.showAnnotation(viewer, side, loader.getResult());
        }
      }
    });
  }

  @Nullable
  private static FileAnnotationLoader createAnnotationsLoader(@NotNull Project project, @NotNull DiffRequest request, @NotNull Side side) {
    Change change = request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY);
    if (change != null) {
      final ContentRevision revision = side.select(change.getBeforeRevision(), change.getAfterRevision());
      if (revision == null) return null;
      AbstractVcs vcs = ChangesUtil.getVcsForChange(change, project);
      if (vcs == null) return null;

      final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
      if (annotationProvider == null) return null;

      if (revision instanceof CurrentContentRevision) {
        return new FileAnnotationLoader(vcs) {
          @Override
          public FileAnnotation compute() throws VcsException {
            final VirtualFile file = ((CurrentContentRevision)revision).getVirtualFile();
            if (file == null) throw new VcsException("Failed to annotate: file not found");
            return annotationProvider.annotate(file);
          }
        };
      }
      else {
        if (!(annotationProvider instanceof AnnotationProviderEx)) return null;
        return new FileAnnotationLoader(vcs) {
          @Override
          public FileAnnotation compute() throws VcsException {
            return ((AnnotationProviderEx)annotationProvider).annotate(revision.getFile(), revision.getRevisionNumber());
          }
        };
      }
    }

    if (request instanceof ContentDiffRequest) {
      ContentDiffRequest requestEx = (ContentDiffRequest)request;
      if (requestEx.getContents().size() != 2) return null;
      DiffContent content = side.select(requestEx.getContents());
      if (content instanceof FileContent) {
        final VirtualFile file = ((FileContent)content).getFile();
        AbstractVcs vcs = VcsUtil.getVcsFor(project, file);
        if (vcs == null) return null;

        final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
        if (annotationProvider == null) return null;

        return new FileAnnotationLoader(vcs) {
          @Override
          public FileAnnotation compute() throws VcsException {
            return annotationProvider.annotate(file);
          }
        };
      }
    }

    return null;
  }

  private static class TwosideAnnotator extends ViewerAnnotator<TwosideTextDiffViewer> {
    @Override
    @NotNull
    public Class<TwosideTextDiffViewer> getViewerClass() {
      return TwosideTextDiffViewer.class;
    }

    @Override
    @Nullable
    public Side getCurrentSide(@NotNull TwosideTextDiffViewer viewer, @NotNull Editor editor) {
      Side side = null; // we can't just use getCurrentSide() here, popup can be called on unfocused editor
      if (viewer.getEditor(Side.LEFT) == editor) side = Side.LEFT;
      if (viewer.getEditor(Side.RIGHT) == editor) side = Side.RIGHT;
      return side;
    }

    @Override
    public boolean isAnnotationShown(@NotNull TwosideTextDiffViewer viewer, @NotNull Side side) {
      return viewer.getEditor(side).getGutter().isAnnotationsShown();
    }

    @Override
    public void showAnnotation(@NotNull TwosideTextDiffViewer viewer, @NotNull Side side, @NotNull AnnotationData data) {
      AnnotateToggleAction.doAnnotate(viewer.getEditor(side), viewer.getProject(), null, data.annotation, data.vcs, null);
    }
  }

  private static class OnesideAnnotator extends ViewerAnnotator<OnesideTextDiffViewer> {
    @Override
    @NotNull
    public Class<OnesideTextDiffViewer> getViewerClass() {
      return OnesideTextDiffViewer.class;
    }

    @Override
    @Nullable
    public Side getCurrentSide(@NotNull OnesideTextDiffViewer viewer, @NotNull Editor editor) {
      if (viewer.getEditor() != editor) return null;
      return viewer.getSide();
    }

    @Override
    public boolean isAnnotationShown(@NotNull OnesideTextDiffViewer viewer, @NotNull Side side) {
      if (side != viewer.getSide()) return false;
      return viewer.getEditor().getGutter().isAnnotationsShown();
    }

    @Override
    public void showAnnotation(@NotNull OnesideTextDiffViewer viewer, @NotNull Side side, @NotNull AnnotationData data) {
      if (side != viewer.getSide()) return;
      AnnotateToggleAction.doAnnotate(viewer.getEditor(), viewer.getProject(), null, data.annotation, data.vcs, null);
    }
  }

  private static class UnifiedAnnotator extends ViewerAnnotator<UnifiedDiffViewer> {
    @Override
    @NotNull
    public Class<UnifiedDiffViewer> getViewerClass() {
      return UnifiedDiffViewer.class;
    }

    @Override
    @Nullable
    public Side getCurrentSide(@NotNull UnifiedDiffViewer viewer, @NotNull Editor editor) {
      if (viewer.getEditor() != editor) return null;
      return viewer.getMasterSide();
    }

    @Override
    public boolean isAnnotationShown(@NotNull UnifiedDiffViewer viewer, @NotNull Side side) {
      if (side != viewer.getMasterSide()) return false;
      return viewer.getEditor().getGutter().isAnnotationsShown();
    }

    @Override
    public void showAnnotation(@NotNull UnifiedDiffViewer viewer, @NotNull Side side, @NotNull AnnotationData data) {
      if (side != viewer.getMasterSide()) return;
      UnifiedUpToDateLineNumberProvider lineNumberProvider = new UnifiedUpToDateLineNumberProvider(viewer, side);
      AnnotateToggleAction.doAnnotate(viewer.getEditor(), viewer.getProject(), null, data.annotation, data.vcs, lineNumberProvider);
    }
  }

  private static class UnifiedUpToDateLineNumberProvider implements UpToDateLineNumberProvider {
    @NotNull private final UnifiedDiffViewer myViewer;
    @NotNull private final Side mySide;
    @NotNull private final UpToDateLineNumberProvider myLocalChangesProvider;

    public UnifiedUpToDateLineNumberProvider(@NotNull UnifiedDiffViewer viewer, @NotNull Side side) {
      myViewer = viewer;
      mySide = side;
      myLocalChangesProvider = new UpToDateLineNumberProviderImpl(myViewer.getDocument(mySide), viewer.getProject());
    }

    @Override
    public int getLineNumber(int currentNumber) {
      int number = myViewer.transferLineFromOnesideStrict(mySide, currentNumber);
      return number != -1 ? myLocalChangesProvider.getLineNumber(number) : -1;
    }

    @Override
    public boolean isLineChanged(int currentNumber) {
      return getLineNumber(currentNumber) == -1;
    }

    @Override
    public boolean isRangeChanged(int start, int end) {
      int line1 = myViewer.transferLineFromOnesideStrict(mySide, start);
      int line2 = myViewer.transferLineFromOnesideStrict(mySide, end);
      if (line2 - line1 != end - start) return true;

      for (int i = start; i <= end; i++) {
        if (isLineChanged(i)) return true; // TODO: a single request to LineNumberConvertor
      }
      return myLocalChangesProvider.isRangeChanged(line1, line2);
    }
  }

  private static abstract class ViewerAnnotator<T extends DiffViewerBase> {
    @NotNull
    public abstract Class<T> getViewerClass();

    @Nullable
    public abstract Side getCurrentSide(@NotNull T viewer, @NotNull Editor editor);

    public abstract boolean isAnnotationShown(@NotNull T viewer, @NotNull Side side);

    public abstract void showAnnotation(@NotNull T viewer, @NotNull Side side, @NotNull AnnotationData data);
  }

  private abstract static class FileAnnotationLoader {
    @NotNull private final AbstractVcs myVcs;

    private VcsException myException;
    private FileAnnotation myResult;

    public FileAnnotationLoader(@NotNull AbstractVcs vcs) {
      myVcs = vcs;
    }

    public VcsException getException() {
      return myException;
    }

    public AnnotationData getResult() {
      return new AnnotationData(myVcs, myResult);
    }

    public void run() {
      try {
        myResult = compute();
      }
      catch (VcsException e) {
        myException = e;
      }
    }

    protected abstract FileAnnotation compute() throws VcsException;
  }

  private static class AnnotationData {
    @NotNull public final AbstractVcs vcs;
    @NotNull public final FileAnnotation annotation;

    public AnnotationData(@NotNull AbstractVcs vcs, @NotNull FileAnnotation annotation) {
      this.vcs = vcs;
      this.annotation = annotation;
    }
  }

  private static boolean checkRunningProgress(@NotNull DiffViewerBase viewer, @NotNull Side side) {
    final ProjectLevelVcsManagerImpl plVcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(viewer.getProject());
    final BackgroundableActionEnabledHandler handler = plVcsManager.getBackgroundableActionHandler(VcsBackgroundableActions.ANNOTATE);
    return handler.isInProgress(key(viewer, side));
  }

  private static void markRunningProgress(@NotNull DiffViewerBase viewer, @NotNull Side side, boolean running) {
    final ProjectLevelVcsManagerImpl plVcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(viewer.getProject());
    final BackgroundableActionEnabledHandler handler = plVcsManager.getBackgroundableActionHandler(VcsBackgroundableActions.ANNOTATE);
    if (running) {
      handler.register(key(viewer, side));
    }
    else {
      handler.completed(key(viewer, side));
    }
  }

  @NotNull
  private static Object key(@NotNull DiffViewer viewer, @NotNull Side side) {
    return Pair.create(viewer, side);
  }
}
