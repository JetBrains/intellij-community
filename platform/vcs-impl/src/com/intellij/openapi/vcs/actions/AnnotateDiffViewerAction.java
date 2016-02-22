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

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffContextEx;
import com.intellij.diff.DiffExtension;
import com.intellij.diff.FrameDiffTool.DiffViewer;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.base.DiffViewerBase;
import com.intellij.diff.tools.util.base.DiffViewerListener;
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer;
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer;
import com.intellij.diff.util.BackgroundTaskUtil;
import com.intellij.diff.util.Side;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.BackgroundableActionLock;
import com.intellij.openapi.vcs.impl.UpToDateLineNumberProviderImpl;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.AnnotationProviderEx;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class AnnotateDiffViewerAction extends ToggleAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(AnnotateDiffViewerAction.class);

  private static final Key<AnnotationData[]> CACHE_KEY = Key.create("Diff.AnnotateAction.Cache");
  private static final Key<boolean[]> ANNOTATIONS_SHOWN_KEY = Key.create("Diff.AnnotateAction.AnnotationShown");

  private static final ViewerAnnotator[] ANNOTATORS = new ViewerAnnotator[]{
    new TwosideAnnotator(), new OnesideAnnotator(), new UnifiedAnnotator()
  };

  public AnnotateDiffViewerAction() {
    EmptyAction.setupAction(this, "Annotate", null);
    setEnabledInModalContext(true);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    boolean enabled = isEnabled(e);
    e.getPresentation().setVisible(enabled);
    e.getPresentation().setEnabled(enabled && !isSuspended(e));
  }

  @Nullable
  private static ViewerAnnotator getAnnotator(@NotNull DiffViewerBase viewer) {
    for (ViewerAnnotator annotator : ANNOTATORS) {
      if (annotator.getViewerClass().isInstance(viewer)) return annotator;
    }
    return null;
  }

  @Nullable
  private static EventData collectEventData(AnActionEvent e) {
    DiffViewerBase viewer = ObjectUtils.tryCast(e.getData(DiffDataKeys.DIFF_VIEWER), DiffViewerBase.class);
    if (viewer == null) return null;
    if (viewer.getProject() == null) return null;
    if (viewer.isDisposed()) return null;

    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) return null;

    ViewerAnnotator annotator = getAnnotator(viewer);
    if (annotator == null) return null;

    //noinspection unchecked
    Side side = annotator.getCurrentSide(viewer, editor);
    if (side == null) return null;

    return new EventData(viewer, editor, annotator, side);
  }

  private static boolean isEnabled(AnActionEvent e) {
    EventData data = collectEventData(e);
    if (data == null) return false;

    //noinspection unchecked
    if (data.annotator.isAnnotationShown(data.viewer, data.side)) return true;
    return createAnnotationsLoader(data.viewer.getProject(), data.viewer.getRequest(), data.side) != null;
  }

  private static boolean isSuspended(AnActionEvent e) {
    EventData data = collectEventData(e);
    return data != null && getBackgroundableLock(data.viewer, data.side).isLocked();
  }

  private static boolean isAnnotated(AnActionEvent e) {
    EventData data = collectEventData(e);
    assert data != null;
    //noinspection unchecked
    return data.annotator.isAnnotationShown(data.viewer, data.side);
  }

  private static void perform(AnActionEvent e, boolean selected) {
    EventData data = collectEventData(e);
    assert data != null;

    //noinspection unchecked
    boolean annotationShown = data.annotator.isAnnotationShown(data.viewer, data.side);
    if (annotationShown) {
      //noinspection unchecked
      data.annotator.hideAnnotation(data.viewer, data.side);
    }
    else {
      doAnnotate(data.annotator, data.viewer, data.side);
    }
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    EventData data = collectEventData(e);
    //noinspection unchecked
    return data != null && data.annotator.isAnnotationShown(data.viewer, data.side);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    perform(e, state);
  }

  private static <T extends DiffViewerBase> void doAnnotate(@NotNull final ViewerAnnotator<T> annotator,
                                                           @NotNull final T viewer,
                                                           @NotNull final Side side) {
    final Project project = viewer.getProject();
    if (project == null) return;
    final ContentDiffRequest request = viewer.getRequest();

    AnnotationData data = getDataFromCache(request, side);
    if (data != null) {
      annotator.showAnnotation(viewer, side, data);
      return;
    }

    final FileAnnotationLoader loader = createAnnotationsLoader(project, request, side);
    if (loader == null) return;

    final DiffContextEx diffContext = ObjectUtils.tryCast(viewer.getContext(), DiffContextEx.class);

    getBackgroundableLock(viewer, side).lock();
    if (diffContext != null) diffContext.showProgressBar(true);

    BackgroundTaskUtil.executeOnPooledThread(new Consumer<ProgressIndicator>() {
      @Override
      public void consume(ProgressIndicator indicator) {
        try {
          loader.run();
        }
        finally {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              if (diffContext != null) diffContext.showProgressBar(false);
              getBackgroundableLock(viewer, side).unlock();

              VcsException exception = loader.getException();
              if (exception != null) {
                Notification notification = VcsNotifier.IMPORTANT_ERROR_NOTIFICATION
                  .createNotification("Can't Load Annotations", exception.getMessage(), NotificationType.ERROR, null);
                showNotification(viewer, notification);
                LOG.warn(exception);
                return;
              }

              if (loader.getResult() == null) return;
              if (loader.shouldCache()) {
                // data race is possible here, but we expect AnnotationData to be immutable, so this is not an issue
                putDataToCache(request, side, loader.getResult());
              }

              if (viewer.isDisposed()) return;
              annotator.showAnnotation(viewer, side, loader.getResult());
            }
          }, indicator.getModalityState());
        }
      }
    }, viewer);
  }

  @Nullable
  private static FileAnnotationLoader createAnnotationsLoader(@NotNull Project project, @NotNull DiffRequest request, @NotNull Side side) {
    Change change = request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY);
    if (change != null) {
      ContentRevision revision = side.select(change.getBeforeRevision(), change.getAfterRevision());
      if (revision != null) {
        AbstractVcs vcs = ChangesUtil.getVcsForChange(change, project);

        if (revision instanceof CurrentContentRevision) {
          VirtualFile file = ((CurrentContentRevision)revision).getVirtualFile();
          FileAnnotationLoader loader = doCreateAnnotationsLoader(project, vcs, file);
          if (loader != null) return loader;
        }
        else {
          FileAnnotationLoader loader = doCreateAnnotationsLoader(vcs, revision.getFile(), revision.getRevisionNumber());
          if (loader != null) return loader;
        }
      }
    }

    if (request instanceof ContentDiffRequest) {
      ContentDiffRequest requestEx = (ContentDiffRequest)request;
      if (requestEx.getContents().size() == 2) {

        DiffContent content = side.select(requestEx.getContents());
        if (content instanceof FileContent) {
          VirtualFile file = ((FileContent)content).getFile();
          AbstractVcs vcs = VcsUtil.getVcsFor(project, file);
          FileAnnotationLoader loader = doCreateAnnotationsLoader(project, vcs, file);
          if (loader != null) return loader;
        }

        Pair<FilePath, VcsRevisionNumber> info = content.getUserData(VcsHistoryUtil.REVISION_INFO_KEY);
        if (info != null) {
          FilePath filePath = info.first;
          AbstractVcs vcs = VcsUtil.getVcsFor(project, filePath);
          FileAnnotationLoader loader = doCreateAnnotationsLoader(vcs, filePath, info.second);
          if (loader != null) return loader;
        }
      }
    }

    return null;
  }

  @Nullable
  private static FileAnnotationLoader doCreateAnnotationsLoader(@NotNull Project project,
                                                                @Nullable AbstractVcs vcs,
                                                                @Nullable final VirtualFile file) {
    if (vcs == null || file == null) return null;
    final AnnotationProvider annotationProvider = vcs.getCachingAnnotationProvider();
    if (annotationProvider == null) return null;

    FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(file);
    if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED || fileStatus == FileStatus.IGNORED) {
      return null;
    }

    // TODO: cache them too, listening for ProjectLevelVcsManager.getInstance(project).getAnnotationLocalChangesListener() ?
    return new FileAnnotationLoader(vcs, false) {
      @Override
      public FileAnnotation compute() throws VcsException {
        return annotationProvider.annotate(file);
      }
    };
  }

  @Nullable
  private static FileAnnotationLoader doCreateAnnotationsLoader(@Nullable AbstractVcs vcs,
                                                                @Nullable final FilePath path,
                                                                @Nullable final VcsRevisionNumber revisionNumber) {
    if (vcs == null || path == null || revisionNumber == null) return null;
    final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
    if (!(annotationProvider instanceof AnnotationProviderEx)) return null;

    return new FileAnnotationLoader(vcs, true) {
      @Override
      public FileAnnotation compute() throws VcsException {
        return ((AnnotationProviderEx)annotationProvider).annotate(path, revisionNumber);
      }
    };
  }

  private static void putDataToCache(@NotNull DiffRequest request, @NotNull Side side, @NotNull AnnotationData data) {
    AnnotationData[] cache = request.getUserData(CACHE_KEY);
    if (cache == null || cache.length != 2) {
      cache = new AnnotationData[2];
      request.putUserData(CACHE_KEY, cache);
    }
    cache[side.getIndex()] = data;
  }

  @Nullable
  private static AnnotationData getDataFromCache(@NotNull DiffRequest request, @NotNull Side side) {
    AnnotationData[] cache = request.getUserData(CACHE_KEY);
    if (cache != null && cache.length == 2) {
      return side.select(cache);
    }
    return null;
  }

  public static class MyDiffExtension extends DiffExtension {
    @Override
    public void onViewerCreated(@NotNull DiffViewer diffViewer, @NotNull DiffContext context, @NotNull DiffRequest request) {
      if (diffViewer instanceof DiffViewerBase) {
        DiffViewerBase viewer = (DiffViewerBase)diffViewer;
        viewer.addListener(new MyDiffViewerListener(viewer));
      }
    }
  }

  private static class MyDiffViewerListener extends DiffViewerListener {
    @NotNull private final DiffViewerBase myViewer;

    public MyDiffViewerListener(@NotNull DiffViewerBase viewer) {
      myViewer = viewer;
    }

    @Override
    public void onInit() {
      if (myViewer.getProject() == null) return;

      boolean[] annotationsShown = myViewer.getRequest().getUserData(ANNOTATIONS_SHOWN_KEY);
      if (annotationsShown == null || annotationsShown.length != 2) return;

      ViewerAnnotator annotator = getAnnotator(myViewer);
      if (annotator == null) return;

      if (annotationsShown[0]) doAnnotate(annotator, myViewer, Side.LEFT);
      if (annotationsShown[1]) doAnnotate(annotator, myViewer, Side.RIGHT);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onDispose() {
      ViewerAnnotator annotator = getAnnotator(myViewer);
      if (annotator == null) return;

      boolean[] annotationsShown = new boolean[2];
      annotationsShown[0] = annotator.isAnnotationShown(myViewer, Side.LEFT);
      annotationsShown[1] = annotator.isAnnotationShown(myViewer, Side.RIGHT);

      myViewer.getRequest().putUserData(ANNOTATIONS_SHOWN_KEY, annotationsShown);
    }
  }

  private static void showNotification(@NotNull DiffViewerBase viewer, @NotNull Notification notification) {
    JComponent component = viewer.getComponent();

    Window window = UIUtil.getWindow(component);
    if (window instanceof IdeFrame && NotificationsManagerImpl.findWindowForBalloon(viewer.getProject()) == window) {
      notification.notify(viewer.getProject());
      return;
    }

    Balloon balloon = NotificationsManagerImpl.createBalloon(component, notification, false, true, null);
    Disposer.register(viewer, balloon);

    Dimension componentSize = component.getSize();
    Dimension balloonSize = balloon.getPreferredSize();

    int width = Math.min(balloonSize.width, componentSize.width);
    int height = Math.min(balloonSize.height, componentSize.height);

    // top-right corner, 20px to the edges
    RelativePoint point = new RelativePoint(component, new Point(componentSize.width - 20 - width / 2, 20 + height / 2));
    balloon.show(point, Balloon.Position.above);
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
      Project project = ObjectUtils.assertNotNull(viewer.getProject());
      AnnotateToggleAction.doAnnotate(viewer.getEditor(side), project, null, data.annotation, data.vcs);
    }

    @Override
    public void hideAnnotation(@NotNull TwosideTextDiffViewer viewer, @NotNull Side side) {
      viewer.getEditor(side).getGutter().closeAllAnnotations();
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
      Project project = ObjectUtils.assertNotNull(viewer.getProject());
      AnnotateToggleAction.doAnnotate(viewer.getEditor(), project, null, data.annotation, data.vcs);
    }

    @Override
    public void hideAnnotation(@NotNull OnesideTextDiffViewer viewer, @NotNull Side side) {
      viewer.getEditor().getGutter().closeAllAnnotations();
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
      Project project = ObjectUtils.assertNotNull(viewer.getProject());
      UnifiedUpToDateLineNumberProvider lineNumberProvider = new UnifiedUpToDateLineNumberProvider(viewer, side);
      AnnotateToggleAction.doAnnotate(viewer.getEditor(), project, null, data.annotation, data.vcs, lineNumberProvider);
    }

    @Override
    public void hideAnnotation(@NotNull UnifiedDiffViewer viewer, @NotNull Side side) {
      viewer.getEditor().getGutter().closeAllAnnotations();
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
      return number != -1 ? myLocalChangesProvider.getLineNumber(number) : FAKE_LINE_NUMBER;
    }

    @Override
    public boolean isLineChanged(int currentNumber) {
      return getLineNumber(currentNumber) == ABSENT_LINE_NUMBER;
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

    public abstract void hideAnnotation(@NotNull T viewer, @NotNull Side side);
  }

  private abstract static class FileAnnotationLoader {
    @NotNull private final AbstractVcs myVcs;
    private final boolean myShouldCache;

    @Nullable private VcsException myException;
    @Nullable private FileAnnotation myResult;

    public FileAnnotationLoader(@NotNull AbstractVcs vcs, boolean cache) {
      myVcs = vcs;
      myShouldCache = cache;
    }

    @Nullable
    public VcsException getException() {
      return myException;
    }

    @Nullable
    public AnnotationData getResult() {
      return myResult != null ? new AnnotationData(myVcs, myResult) : null;
    }

    public boolean shouldCache() {
      return myShouldCache;
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

  @NotNull
  private static BackgroundableActionLock getBackgroundableLock(@NotNull DiffViewerBase viewer, @NotNull Side side) {
    return BackgroundableActionLock.getLock(viewer.getProject(), VcsBackgroundableActions.ANNOTATE, viewer, side);
  }

  private static class EventData {
    @NotNull public final DiffViewerBase viewer;
    @NotNull public final Editor editor;
    @NotNull public final ViewerAnnotator annotator;
    @NotNull public final Side side;

    public EventData(@NotNull DiffViewerBase viewer, @NotNull Editor editor, @NotNull ViewerAnnotator annotator, @NotNull Side side) {
      this.viewer = viewer;
      this.editor = editor;
      this.annotator = annotator;
      this.side = side;
    }
  }

  public static class Provider implements AnnotateToggleAction.Provider {
    @Override
    public boolean isEnabled(AnActionEvent e) {
      return AnnotateDiffViewerAction.isEnabled(e);
    }

    @Override
    public boolean isSuspended(AnActionEvent e) {
      return AnnotateDiffViewerAction.isSuspended(e);
    }

    @Override
    public boolean isAnnotated(AnActionEvent e) {
      return AnnotateDiffViewerAction.isAnnotated(e);
    }

    @Override
    public void perform(AnActionEvent e, boolean selected) {
      AnnotateDiffViewerAction.perform(e, selected);
    }
  }
}
