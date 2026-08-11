// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffContextEx;
import com.intellij.diff.DiffExtension;
import com.intellij.diff.DiffVcsDataKeys;
import com.intellij.diff.FrameDiffTool.DiffViewer;
import com.intellij.diff.PatchBaseAnnotationInfo;
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.merge.MergeThreesideViewer;
import com.intellij.diff.merge.TextMergeViewer;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer;
import com.intellij.diff.tools.simple.SimpleThreesideDiffViewer;
import com.intellij.diff.tools.simple.ThreesideTextDiffViewerEx;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.base.DiffViewerBase;
import com.intellij.diff.tools.util.base.DiffViewerListener;
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer;
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer;
import com.intellij.diff.tools.util.text.LineOffsetsUtil;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.ThreeSide;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotificationIdsHolder;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.TextRevisionNumber;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.patch.DefaultPatchBaseVersionProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.BackgroundableActionLock;
import com.intellij.openapi.vcs.impl.UpToDateLineNumberProviderImpl;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.BalloonLayoutData;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ObjectUtils;
import com.intellij.vcs.AnnotationProviderEx;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@ApiStatus.Internal
public final class AnnotateDiffViewerAction {
  private static final Logger LOG = Logger.getInstance(AnnotateDiffViewerAction.class);
  private static class Holder {
    private static final Key<boolean[]> ANNOTATIONS_SHOWN_KEY = Key.create("Diff.AnnotateAction.AnnotationShown");

    private static final ViewerAnnotatorFactory<?>[] ANNOTATORS = new ViewerAnnotatorFactory[]{
      new TwosideAnnotatorFactory(), new OnesideAnnotatorFactory(), new UnifiedAnnotatorFactory(),
      new ThreesideAnnotatorFactory(), new TextMergeAnnotatorFactory()
    };
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static @Nullable ViewerAnnotator getAnnotator(@NotNull DiffViewerBase viewer, @Nullable Editor editor) {
    for (ViewerAnnotatorFactory annotator : Holder.ANNOTATORS) {
      if (annotator.getViewerClass().isInstance(viewer)) return annotator.createAnnotator(viewer, editor);
    }
    return null;
  }

  private static @Nullable EventData collectEventData(@NotNull AnActionEvent e) {
    DiffViewerBase viewer = getViewer(e);
    if (viewer == null) return null;
    if (viewer.getProject() == null) return null;
    if (viewer.isDisposed()) return null;

    Editor editor = e.getData(CommonDataKeys.EDITOR);

    ViewerAnnotator annotator = getAnnotator(viewer, editor);
    if (annotator == null) return null;

    return new EventData(viewer, annotator);
  }

  private static @Nullable DiffViewerBase getViewer(@NotNull AnActionEvent e) {
    DiffViewerBase diffViewer = ObjectUtils.tryCast(e.getData(DiffDataKeys.DIFF_VIEWER), DiffViewerBase.class);
    if (diffViewer != null) return diffViewer;

    TextMergeViewer mergeViewer = ObjectUtils.tryCast(e.getData(DiffDataKeys.MERGE_VIEWER), TextMergeViewer.class);
    if (mergeViewer != null) return mergeViewer.getViewer();

    return null;
  }

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    EventData data = collectEventData(e);
    if (data == null) return false;

    if (data.annotator.isAnnotationShown()) return true;
    return data.annotator.createAnnotationsLoader() != null;
  }

  private static boolean isSuspended(@NotNull AnActionEvent e) {
    EventData data = collectEventData(e);
    return data != null && data.annotator.getBackgroundableLock().isLocked();
  }

  private static boolean isAnnotated(@NotNull AnActionEvent e) {
    EventData data = collectEventData(e);
    assert data != null;
    return data.annotator.isAnnotationShown();
  }

  private static void perform(AnActionEvent e, boolean selected) {
    EventData data = collectEventData(e);
    assert data != null;

    if (!selected) {
      data.annotator.hideAnnotation();
    }
    else {
      doAnnotate(data.annotator);
    }
  }

  private static void doAnnotate(final @NotNull ViewerAnnotator annotator) {
    final DiffViewerBase viewer = annotator.getViewer();
    final Project project = viewer.getProject();
    if (project == null) return;

    final FileAnnotationLoader loader = annotator.createAnnotationsLoader();
    if (loader == null) return;

    final DiffContextEx diffContext = ObjectUtils.tryCast(viewer.getContext(), DiffContextEx.class);

    annotator.getBackgroundableLock().lock();
    if (diffContext != null) diffContext.showProgressBar(true);

    BackgroundTaskUtil.executeOnPooledThread(viewer, () -> {
      // loader.run() does all the heavy lifting (VCS calls, and for a patch-base side the line-diff that builds the
      // line-number provider) on this pooled thread, so showAnnotation() below never freezes the UI.
      try {
        loader.run();
      }
      finally {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (diffContext != null) diffContext.showProgressBar(false);
          annotator.getBackgroundableLock().unlock();

          VcsException exception = loader.getException();
          if (exception != null) {
            Notification notification = VcsNotifier.importantNotification()
              .createNotification(VcsBundle.message("notification.title.cant.load.annotations"), exception.getMessage(), NotificationType.ERROR)
              .setDisplayId(VcsNotificationIdsHolder.CANNOT_LOAD_ANNOTATIONS);
            showNotification(viewer, notification);
            LOG.warn(exception);
            return;
          }

          AnnotationData result = loader.getResult();
          if (result == null) return;
          if (viewer.isDisposed()) return;

          annotator.showAnnotation(result);
        });
      }
    });
  }

  private static @Nullable FileAnnotationLoader createThreesideAnnotationsLoader(@NotNull Project project,
                                                                                 @NotNull DiffRequest request,
                                                                                 @NotNull ThreeSide side) {
    if (request instanceof ContentDiffRequest requestEx) {
      if (requestEx.getContents().size() == 3) {
        DiffContent content = side.select(requestEx.getContents());
        FileAnnotationLoader loader = createAnnotationsLoader(project, content);
        if (loader != null) return loader;
      }
    }

    return null;
  }

  private static @Nullable FileAnnotationLoader createTwosideAnnotationsLoader(@NotNull Project project,
                                                                               @NotNull DiffRequest request,
                                                                               @NotNull Side side) {
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

    if (request instanceof ContentDiffRequest requestEx) {
      if (requestEx.getContents().size() == 2) {
        DiffContent content = side.select(requestEx.getContents());
        return createAnnotationsLoader(project, content);
      }
    }

    return null;
  }

  private static @Nullable FileAnnotationLoader createAnnotationsLoader(@NotNull Project project, @NotNull DiffContent content) {
    if (content instanceof FileContent) {
      VirtualFile file = ((FileContent)content).getFile();
      AbstractVcs vcs = VcsUtil.getVcsFor(project, file);
      FileAnnotationLoader loader = doCreateAnnotationsLoader(project, vcs, file);
      if (loader != null) return loader;
    }

    VirtualFile localFile = content.getUserData(DiffVcsDataKeys.LOCAL_FILE);
    if (localFile != null) {
      AbstractVcs vcs = VcsUtil.getVcsFor(project, localFile);
      FileAnnotationLoader loader = doCreateAnnotationsLoader(project, vcs, localFile);
      if (loader != null) return loader;
    }

    Pair<FilePath, VcsRevisionNumber> info = content.getUserData(DiffVcsDataKeys.REVISION_INFO);
    if (info != null) {
      FilePath filePath = info.first;
      AbstractVcs vcs = VcsUtil.getVcsFor(project, filePath);
      FileAnnotationLoader loader = doCreateAnnotationsLoader(vcs, filePath, info.second);
      if (loader != null) return loader;
    }

    PatchBaseAnnotationInfo patchBaseInfo = content.getUserData(DiffVcsDataKeys.PATCH_BASE_INFO);
    if (patchBaseInfo != null) {
      AbstractVcs vcs = VcsUtil.getVcsFor(project, patchBaseInfo.getPath());
      if (vcs != null) {
        Document patchedDocument = content instanceof DocumentContent documentContent ? documentContent.getDocument() : null;
        FileAnnotationLoader loader = createPatchBaseAnnotationsLoader(vcs, patchBaseInfo, patchedDocument);
        if (loader != null) return loader;
      }
    }
    return null;
  }

  /**
   * Annotates the committed base revision referenced by a patch base version id (see {@link DiffVcsDataKeys#PATCH_BASE_INFO}),
   * so the "Your uncommitted changes" side of a patch conflict can be blamed against it.
   * <p>
   * Both the revision resolution (some VCS, e.g. Git, run commands to resolve a revision number and validate an annotation)
   * and the line-number provider's line diff run on a background thread inside {@link FileAnnotationLoader#run()}, so nothing
   * heavy happens on the EDT where this loader is built as part of the annotate action.
   */
  private static @Nullable FileAnnotationLoader createPatchBaseAnnotationsLoader(@NotNull AbstractVcs vcs,
                                                                                 @NotNull PatchBaseAnnotationInfo patchBaseInfo,
                                                                                 @Nullable Document patchedDocument) {
    FilePath path = patchBaseInfo.getPath();
    String baseVersionId = patchBaseInfo.getBaseVersionId();
    AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
    if (!(annotationProvider instanceof AnnotationProviderEx annotationProviderEx)) return null;
    if (DefaultPatchBaseVersionProvider.parseVersionAsRevision(baseVersionId, vcs) == null) return null; // cheap check, no VCS calls

    return new FileAnnotationLoader(vcs) {
      @Override
      protected FileAnnotation compute() throws VcsException {
        String revisionString = DefaultPatchBaseVersionProvider.parseVersionAsRevision(baseVersionId, vcs);
        if (revisionString == null) return null;
        VcsRevisionNumber revision = vcs.parseRevisionNumber(revisionString, path);
        if (revision == null || revision == VcsRevisionNumber.NULL) return null;
        if (!annotationProviderEx.isAnnotationValid(path, revision)) return null;
        return annotationProviderEx.annotate(path, revision);
      }

      @Override
      protected @Nullable UpToDateLineNumberProvider computeLineNumberProvider() {
        // The annotation is computed for the base revision; remap the displayed (patched) lines back to it so that lines
        // added on top of the base render as "not committed yet".
        if (patchedDocument == null) return null;
        return createPatchBaseLineNumberProvider(patchBaseInfo.getBaseContent(), patchedDocument);
      }
    };
  }

  /**
   * Maps lines of a patched content (a base revision with extra changes applied) back to the base revision lines:
   * context lines map to their base line, lines added/changed on top of the base map to {@code FAKE_LINE_NUMBER}
   * (rendered as "not committed yet"). Returns {@code null} if the texts are too big to diff.
   */
  @VisibleForTesting
  public static @Nullable UpToDateLineNumberProvider createPatchBaseLineNumberProvider(@NotNull CharSequence baseContent,
                                                                                       @NotNull Document patchedDocument) {
    int patchedLineCount = patchedDocument.getLineCount();
    // mapping[patchedLine] = corresponding base-revision line, or FAKE_LINE_NUMBER for lines added on top of the base.
    // Default everything to FAKE; the walk below fills in the lines that map back to a base line.
    int[] mapping = new int[patchedLineCount];
    Arrays.fill(mapping, UpToDateLineNumberProvider.FAKE_LINE_NUMBER);
    //noinspection IncorrectCancellationExceptionHandling
    try {
      // Each fragment is a *changed* region (base range [startLine1, endLine1) vs patched range [startLine2, endLine2));
      // the gaps between fragments are equal regions that line up 1:1 between base and patched.
      List<LineFragment> fragments = ComparisonManager.getInstance()
        .compareLines(baseContent, patchedDocument.getImmutableCharSequence(),
                      ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE);

      int baseLine = 0;
      int patchedLine = 0;
      for (LineFragment fragment : fragments) {
        // Equal region before this fragment: context lines map 1:1 to their base line.
        while (patchedLine < fragment.getStartLine2() && patchedLine < patchedLineCount) {
          mapping[patchedLine++] = baseLine++;
        }
        // The fragment's patched lines were added/changed on top of the base: no base line, leave them FAKE.
        while (patchedLine < fragment.getEndLine2() && patchedLine < patchedLineCount) {
          mapping[patchedLine++] = UpToDateLineNumberProvider.FAKE_LINE_NUMBER;
        }
        // Skip the base lines the fragment deleted/replaced (they don't appear in patched) and resync the patched cursor.
        baseLine = fragment.getEndLine1();
        patchedLine = Math.max(patchedLine, fragment.getEndLine2());
      }
      // Trailing equal region after the last fragment: map the rest 1:1.
      while (patchedLine < patchedLineCount) {
        mapping[patchedLine++] = baseLine++;
      }
    }
    catch (DiffTooBigException e) {
      return null;
    }
    // getLineCount() must report the annotated (base) content size, not the displayed editor size, so that
    // AnnotateWarningsService does not flag a line-count mismatch (the base revision and the patched content may differ).
    int baseLineCount = LineOffsetsUtil.create(baseContent).getLineCount();
    return new PatchBaseUpToDateLineNumberProvider(mapping, baseLineCount);
  }

  private static @Nullable FileAnnotationLoader doCreateAnnotationsLoader(@NotNull Project project,
                                                                          @Nullable AbstractVcs vcs,
                                                                          final @Nullable VirtualFile file) {
    if (vcs == null || file == null) return null;
    final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
    if (annotationProvider == null) return null;

    FileStatus fileStatus = ChangeListManager.getInstance(project).getStatus(file);
    if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED || fileStatus == FileStatus.IGNORED) {
      return null;
    }

    return new FileAnnotationLoader(vcs) {
      @Override
      public FileAnnotation compute() throws VcsException {
        return annotationProvider.annotate(file);
      }
    };
  }

  private static @Nullable FileAnnotationLoader doCreateAnnotationsLoader(@Nullable AbstractVcs vcs,
                                                                          final @Nullable FilePath path,
                                                                          final @Nullable VcsRevisionNumber revisionNumber) {
    if (vcs == null || path == null || revisionNumber == null) return null;
    if (revisionNumber instanceof TextRevisionNumber ||
        revisionNumber == VcsRevisionNumber.NULL) {
      return null;
    }
    AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
    if (!(annotationProvider instanceof AnnotationProviderEx)) return null;
    if (!((AnnotationProviderEx)annotationProvider).isAnnotationValid(path, revisionNumber)) return null;

    return new FileAnnotationLoader(vcs) {
      @Override
      public FileAnnotation compute() throws VcsException {
        return ((AnnotationProviderEx)annotationProvider).annotate(path, revisionNumber);
      }
    };
  }

  @ApiStatus.Internal
  public static final class MyDiffExtension extends DiffExtension {
    @Override
    public void onViewerCreated(@NotNull DiffViewer diffViewer, @NotNull DiffContext context, @NotNull DiffRequest request) {
      if (diffViewer instanceof DiffViewerBase viewer) {
        viewer.addListener(new MyDiffViewerListener(viewer));
      }
    }
  }

  @SuppressWarnings("rawtypes")
  private static class MyDiffViewerListener extends DiffViewerListener {
    private final @NotNull DiffViewerBase myViewer;

    MyDiffViewerListener(@NotNull DiffViewerBase viewer) {
      myViewer = viewer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onInit() {
      if (myViewer.getProject() == null) return;

      for (ViewerAnnotatorFactory annotator : Holder.ANNOTATORS) {
        if (annotator.getViewerClass().isInstance(myViewer)) annotator.showRememberedAnnotations(myViewer);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onDispose() {
      if (myViewer.getProject() == null) return;

      for (ViewerAnnotatorFactory annotator : Holder.ANNOTATORS) {
        if (annotator.getViewerClass().isInstance(myViewer)) annotator.rememberShownAnnotations(myViewer);
      }
    }
  }

  private static void showNotification(@NotNull DiffViewerBase viewer, @NotNull Notification notification) {
    JComponent component = viewer.getComponent();

    Window window = ComponentUtil.getWindow(component);
    if (window instanceof IdeFrame && NotificationsManagerImpl.findWindowForBalloon(viewer.getProject()) == window) {
      notification.notify(viewer.getProject());
      return;
    }

    Balloon balloon = NotificationsManagerImpl.createBalloon(component, notification, false, true, BalloonLayoutData.fullContent(), viewer);

    Dimension componentSize = component.getSize();
    Dimension balloonSize = balloon.getPreferredSize();

    int width = Math.min(balloonSize.width, componentSize.width);
    int height = Math.min(balloonSize.height, componentSize.height);

    // top-right corner, 20px to the edges
    RelativePoint point = new RelativePoint(component, new Point(componentSize.width - 20 - width / 2, 20 + height / 2));
    balloon.show(point, Balloon.Position.above);
  }

  private static class TwosideAnnotatorFactory extends TwosideViewerAnnotatorFactory<TwosideTextDiffViewer> {
    @Override
    public @NotNull Class<TwosideTextDiffViewer> getViewerClass() {
      return TwosideTextDiffViewer.class;
    }

    @Override
    public @Nullable Side getCurrentSide(@NotNull TwosideTextDiffViewer viewer, @Nullable Editor editor) {
      if (viewer.getEditor(Side.LEFT) == editor) return Side.LEFT;
      if (viewer.getEditor(Side.RIGHT) == editor) return Side.RIGHT;
      if (editor != null) return null; // disable with some unknown editor in context
      return viewer.getCurrentSide(); // enable in SetEditorSettingsActionGroup on the diff toolbar
    }

    @Override
    public boolean isAnnotationShown(@NotNull TwosideTextDiffViewer viewer, @NotNull Side side) {
      return AnnotateToggleAction.hasVcsAnnotations(viewer.getEditor(side));
    }

    @Override
    public void showAnnotation(@NotNull TwosideTextDiffViewer viewer, @NotNull Side side, @NotNull AnnotationData data) {
      Project project = Objects.requireNonNull(viewer.getProject());
      AnnotateToggleAction.doAnnotate(viewer.getEditor(side), project, data.annotation, data.vcs);
    }

    @Override
    public void hideAnnotation(@NotNull TwosideTextDiffViewer viewer, @NotNull Side side) {
      AnnotateToggleAction.closeVcsAnnotations(viewer.getEditor(side));
    }
  }

  private static class OnesideAnnotatorFactory extends TwosideViewerAnnotatorFactory<OnesideTextDiffViewer> {
    @Override
    public @NotNull Class<OnesideTextDiffViewer> getViewerClass() {
      return OnesideTextDiffViewer.class;
    }

    @Override
    public @Nullable Side getCurrentSide(@NotNull OnesideTextDiffViewer viewer, @Nullable Editor editor) {
      if (editor != null && viewer.getEditor() != editor) return null;
      return viewer.getSide();
    }

    @Override
    public boolean isAnnotationShown(@NotNull OnesideTextDiffViewer viewer, @NotNull Side side) {
      if (side != viewer.getSide()) return false;
      return AnnotateToggleAction.hasVcsAnnotations(viewer.getEditor());
    }

    @Override
    public void showAnnotation(@NotNull OnesideTextDiffViewer viewer, @NotNull Side side, @NotNull AnnotationData data) {
      if (side != viewer.getSide()) return;
      Project project = Objects.requireNonNull(viewer.getProject());
      AnnotateToggleAction.doAnnotate(viewer.getEditor(), project, data.annotation, data.vcs);
    }

    @Override
    public void hideAnnotation(@NotNull OnesideTextDiffViewer viewer, @NotNull Side side) {
      AnnotateToggleAction.closeVcsAnnotations(viewer.getEditor());
    }
  }

  private static class UnifiedAnnotatorFactory extends TwosideViewerAnnotatorFactory<UnifiedDiffViewer> {
    @Override
    public @NotNull Class<UnifiedDiffViewer> getViewerClass() {
      return UnifiedDiffViewer.class;
    }

    @Override
    public @Nullable Side getCurrentSide(@NotNull UnifiedDiffViewer viewer, @Nullable Editor editor) {
      if (editor != null && viewer.getEditor() != editor) return null;
      return viewer.getMasterSide();
    }

    @Override
    public boolean isAnnotationShown(@NotNull UnifiedDiffViewer viewer, @NotNull Side side) {
      if (side != viewer.getMasterSide()) return false;
      return AnnotateToggleAction.hasVcsAnnotations(viewer.getEditor());
    }

    @Override
    public void showAnnotation(@NotNull UnifiedDiffViewer viewer, @NotNull Side side, @NotNull AnnotationData data) {
      if (side != viewer.getMasterSide()) return;
      Project project = Objects.requireNonNull(viewer.getProject());
      UnifiedUpToDateLineNumberProvider lineNumberProvider = new UnifiedUpToDateLineNumberProvider(viewer, side);
      AnnotateToggleAction.doAnnotate(viewer.getEditor(), project, data.annotation, data.vcs, lineNumberProvider);
    }

    @Override
    public void hideAnnotation(@NotNull UnifiedDiffViewer viewer, @NotNull Side side) {
      AnnotateToggleAction.closeVcsAnnotations(viewer.getEditor());
    }
  }

  private static class UnifiedUpToDateLineNumberProvider implements UpToDateLineNumberProvider {
    private final @NotNull UnifiedDiffViewer myViewer;
    private final @NotNull Side mySide;
    private final @NotNull UpToDateLineNumberProvider myLocalChangesProvider;

    UnifiedUpToDateLineNumberProvider(@NotNull UnifiedDiffViewer viewer, @NotNull Side side) {
      myViewer = viewer;
      mySide = side;
      myLocalChangesProvider = new UpToDateLineNumberProviderImpl(myViewer.getDocument(mySide), viewer.getProject());
    }

    @Override
    public int getLineNumber(int currentNumber) {
      return getLineNumber(currentNumber, false);
    }

    @Override
    public int getLineNumber(int currentNumber, boolean approximate) {
      int number = myViewer.transferLineFromOnesideStrict(mySide, currentNumber);
      if (number == -1) return FAKE_LINE_NUMBER;
      return myLocalChangesProvider.getLineNumber(number, approximate);
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

    @Override
    public int getLineCount() {
      return myLocalChangesProvider.getLineCount();
    }
  }

  private static final class PatchBaseUpToDateLineNumberProvider
    implements UpToDateLineNumberProvider, AnnotationGutterLineConvertorProxy.NonAnnotatedLineTextProvider {
    private final int[] myMapping; // patched line -> base revision line, or FAKE_LINE_NUMBER for added/changed lines
    private final int myBaseLineCount; // line count of the annotated (base) revision

    private PatchBaseUpToDateLineNumberProvider(int[] mapping, int baseLineCount) {
      myMapping = mapping;
      myBaseLineCount = baseLineCount;
    }

    @Override
    public @Nullable String getNonAnnotatedLineText(int line) {
      // Lines added on top of the base (mapped to FAKE_LINE_NUMBER) have no committed source - label them as local edits.
      return getLineNumber(line) == FAKE_LINE_NUMBER ? VcsBundle.message("annotation.line.not.committed.yet") : null;
    }

    @Override
    public int getLineCount() {
      return myBaseLineCount;
    }

    @Override
    public int getLineNumber(int currentNumber) {
      if (currentNumber < 0 || currentNumber >= myMapping.length) return ABSENT_LINE_NUMBER;
      return myMapping[currentNumber];
    }

    @Override
    public int getLineNumber(int currentNumber, boolean approximate) {
      return getLineNumber(currentNumber);
    }

    @Override
    public boolean isLineChanged(int currentNumber) {
      int number = getLineNumber(currentNumber);
      return number == FAKE_LINE_NUMBER || number == ABSENT_LINE_NUMBER;
    }

    @Override
    public boolean isRangeChanged(int start, int end) {
      for (int i = start; i <= end; i++) {
        if (isLineChanged(i)) return true;
      }
      return getLineNumber(end) - getLineNumber(start) != end - start;
    }
  }

  private static class ThreesideAnnotatorFactory extends ThreesideViewerAnnotatorFactory<ThreesideTextDiffViewerEx> {
    @Override
    public @NotNull Class<? extends ThreesideTextDiffViewerEx> getViewerClass() {
      return SimpleThreesideDiffViewer.class;
    }

    @Override
    public @Nullable ThreeSide getCurrentSide(@NotNull ThreesideTextDiffViewerEx viewer, @Nullable Editor editor) {
      if (viewer.getEditor(ThreeSide.LEFT) == editor) return ThreeSide.LEFT;
      if (viewer.getEditor(ThreeSide.BASE) == editor) return ThreeSide.BASE;
      if (viewer.getEditor(ThreeSide.RIGHT) == editor) return ThreeSide.RIGHT;
      if (editor != null) return null; // disable with some unknown editor in context
      return viewer.getCurrentSide(); // enable in SetEditorSettingsActionGroup on the diff toolbar
    }

    @Override
    public boolean isAnnotationShown(@NotNull ThreesideTextDiffViewerEx viewer, @NotNull ThreeSide side) {
      return AnnotateToggleAction.hasVcsAnnotations(viewer.getEditor(side));
    }

    @Override
    public void showAnnotation(@NotNull ThreesideTextDiffViewerEx viewer, @NotNull ThreeSide side, @NotNull AnnotationData data) {
      Project project = Objects.requireNonNull(viewer.getProject());
      Editor editor = viewer.getEditor(side);

      if (data.lineNumberProvider != null) {
        AnnotateToggleAction.doAnnotate(editor, project, data.annotation, data.vcs, data.lineNumberProvider);
        return;
      }
      AnnotateToggleAction.doAnnotate(editor, project, data.annotation, data.vcs);
    }

    @Override
    public void hideAnnotation(@NotNull ThreesideTextDiffViewerEx viewer, @NotNull ThreeSide side) {
      AnnotateToggleAction.closeVcsAnnotations(viewer.getEditor(side));
    }
  }

  private static class TextMergeAnnotatorFactory extends ThreesideAnnotatorFactory {
    @Override
    public @NotNull Class<? extends ThreesideTextDiffViewerEx> getViewerClass() {
      return MergeThreesideViewer.class;
    }

    @Override
    public @Nullable ViewerAnnotator createAnnotator(@NotNull ThreesideTextDiffViewerEx viewer, @NotNull ThreeSide side) {
      if (side == ThreeSide.BASE) return null; // middle content is local Document, not the BASE one
      return super.createAnnotator(viewer, side);
    }
  }

  private abstract static class TwosideViewerAnnotatorFactory<T extends DiffViewerBase> extends ViewerAnnotatorFactory<T> {
    public abstract @Nullable Side getCurrentSide(@NotNull T viewer, @Nullable Editor editor);

    public abstract boolean isAnnotationShown(@NotNull T viewer, @NotNull Side side);

    public abstract void showAnnotation(@NotNull T viewer, @NotNull Side side, @NotNull AnnotationData data);

    public abstract void hideAnnotation(@NotNull T viewer, @NotNull Side side);

    @Override
    public @Nullable ViewerAnnotator createAnnotator(@NotNull T viewer, @Nullable Editor editor) {
      Side side = getCurrentSide(viewer, editor);
      if (side == null) return null;
      return createAnnotator(viewer, side);
    }

    @Override
    public void showRememberedAnnotations(@NotNull T viewer) {
      boolean[] annotationsShown = viewer.getRequest().getUserData(Holder.ANNOTATIONS_SHOWN_KEY);
      if (annotationsShown == null || annotationsShown.length != 2) return;
      if (annotationsShown[0]) {
        ViewerAnnotator annotator = createAnnotator(viewer, Side.LEFT);
        if (annotator != null) doAnnotate(annotator);
      }
      if (annotationsShown[1]) {
        ViewerAnnotator annotator = createAnnotator(viewer, Side.RIGHT);
        if (annotator != null) doAnnotate(annotator);
      }
    }

    @Override
    public void rememberShownAnnotations(@NotNull T viewer) {
      boolean[] annotationsShown = new boolean[2];
      annotationsShown[0] = isAnnotationShown(viewer, Side.LEFT);
      annotationsShown[1] = isAnnotationShown(viewer, Side.RIGHT);

      viewer.getRequest().putUserData(Holder.ANNOTATIONS_SHOWN_KEY, annotationsShown);
    }

    public @Nullable ViewerAnnotator createAnnotator(@NotNull T viewer, @NotNull Side side) {
      TwosideViewerAnnotatorFactory<T> factory = this;
      Project project = viewer.getProject();
      assert project != null;

      return new ViewerAnnotator() {
        @Override
        public @NotNull T getViewer() {
          return viewer;
        }

        @Override
        public boolean isAnnotationShown() {
          return factory.isAnnotationShown(viewer, side);
        }

        @Override
        public void showAnnotation(@NotNull AnnotationData data) {
          factory.showAnnotation(viewer, side, data);
        }

        @Override
        public void hideAnnotation() {
          factory.hideAnnotation(viewer, side);
        }

        @Override
        public @Nullable FileAnnotationLoader createAnnotationsLoader() {
          return createTwosideAnnotationsLoader(project, viewer.getRequest(), side);
        }

        @Override
        public @NotNull BackgroundableActionLock getBackgroundableLock() {
          return BackgroundableActionLock.getLock(viewer.getProject(), VcsBackgroundableActions.ANNOTATE, viewer, side);
        }
      };
    }
  }

  private abstract static class ThreesideViewerAnnotatorFactory<T extends DiffViewerBase> extends ViewerAnnotatorFactory<T> {
    public abstract @Nullable ThreeSide getCurrentSide(@NotNull T viewer, @Nullable Editor editor);

    public abstract boolean isAnnotationShown(@NotNull T viewer, @NotNull ThreeSide side);

    public abstract void showAnnotation(@NotNull T viewer, @NotNull ThreeSide side, @NotNull AnnotationData data);

    public abstract void hideAnnotation(@NotNull T viewer, @NotNull ThreeSide side);

    @Override
    public @Nullable ViewerAnnotator createAnnotator(@NotNull T viewer, @Nullable Editor editor) {
      ThreeSide side = getCurrentSide(viewer, editor);
      if (side == null) return null;
      return createAnnotator(viewer, side);
    }

    @Override
    public void showRememberedAnnotations(@NotNull T viewer) {
      boolean[] annotationsShown = viewer.getRequest().getUserData(Holder.ANNOTATIONS_SHOWN_KEY);
      if (annotationsShown == null || annotationsShown.length != 3) return;
      if (annotationsShown[0]) {
        ViewerAnnotator annotator = createAnnotator(viewer, ThreeSide.LEFT);
        if (annotator != null) doAnnotate(annotator);
      }
      if (annotationsShown[1]) {
        ViewerAnnotator annotator = createAnnotator(viewer, ThreeSide.BASE);
        if (annotator != null) doAnnotate(annotator);
      }
      if (annotationsShown[2]) {
        ViewerAnnotator annotator = createAnnotator(viewer, ThreeSide.RIGHT);
        if (annotator != null) doAnnotate(annotator);
      }
    }

    @Override
    public void rememberShownAnnotations(@NotNull T viewer) {
      boolean[] annotationsShown = new boolean[3];
      annotationsShown[0] = isAnnotationShown(viewer, ThreeSide.LEFT);
      annotationsShown[1] = isAnnotationShown(viewer, ThreeSide.BASE);
      annotationsShown[2] = isAnnotationShown(viewer, ThreeSide.RIGHT);

      viewer.getRequest().putUserData(Holder.ANNOTATIONS_SHOWN_KEY, annotationsShown);
    }

    public @Nullable ViewerAnnotator createAnnotator(@NotNull T viewer, @NotNull ThreeSide side) {
      ThreesideViewerAnnotatorFactory<T> factory = this;
      Project project = viewer.getProject();
      assert project != null;

      BackgroundableActionLock actionLock = BackgroundableActionLock.getLock(project, VcsBackgroundableActions.ANNOTATE, viewer, side);

      return new ViewerAnnotator() {
        @Override
        public @NotNull T getViewer() {
          return viewer;
        }

        @Override
        public boolean isAnnotationShown() {
          return factory.isAnnotationShown(viewer, side);
        }

        @Override
        public void showAnnotation(@NotNull AnnotationData data) {
          factory.showAnnotation(viewer, side, data);
        }

        @Override
        public void hideAnnotation() {
          factory.hideAnnotation(viewer, side);
        }

        @Override
        public @Nullable FileAnnotationLoader createAnnotationsLoader() {
          return createThreesideAnnotationsLoader(project, viewer.getRequest(), side);
        }

        @Override
        public @NotNull BackgroundableActionLock getBackgroundableLock() {
          return actionLock;
        }
      };
    }
  }

  private abstract static class ViewerAnnotatorFactory<T extends DiffViewerBase> {
    public abstract @NotNull Class<? extends T> getViewerClass();

    public abstract @Nullable ViewerAnnotator createAnnotator(@NotNull T viewer, @Nullable Editor editor);

    public abstract void showRememberedAnnotations(@NotNull T viewer);

    public abstract void rememberShownAnnotations(@NotNull T viewer);
  }

  private abstract static class ViewerAnnotator {
    public abstract @NotNull DiffViewerBase getViewer();

    public abstract boolean isAnnotationShown();

    public abstract void showAnnotation(@NotNull AnnotationData data);

    public abstract void hideAnnotation();

    public abstract @Nullable FileAnnotationLoader createAnnotationsLoader();

    public abstract @NotNull BackgroundableActionLock getBackgroundableLock();
  }

  private abstract static class FileAnnotationLoader {
    private final @NotNull AbstractVcs myVcs;

    private @Nullable VcsException myException;
    private @Nullable AnnotationData myResult;

    FileAnnotationLoader(@NotNull AbstractVcs vcs) {
      myVcs = vcs;
    }

    public @Nullable VcsException getException() {
      return myException;
    }

    public @Nullable AnnotationData getResult() {
      return myResult;
    }

    public void run() {
      try {
        FileAnnotation annotation = compute();
        if (annotation != null) {
          myResult = new AnnotationData(myVcs, annotation, computeLineNumberProvider());
        }
      }
      catch (VcsException e) {
        myException = e;
      }
    }

    protected abstract @Nullable FileAnnotation compute() throws VcsException;

    /**
     * Computes a custom line-number provider for {@link #getResult}, on the same background thread as {@link #compute}
     * (so its potentially heavy line diff does not freeze the EDT). Returns {@code null} when no custom provider is needed.
     */
    protected @Nullable UpToDateLineNumberProvider computeLineNumberProvider() {
      return null;
    }
  }

  private static class AnnotationData {
    public final @NotNull AbstractVcs vcs;
    public final @NotNull FileAnnotation annotation;
    public final @Nullable UpToDateLineNumberProvider lineNumberProvider;

    AnnotationData(@NotNull AbstractVcs vcs, @NotNull FileAnnotation annotation, @Nullable UpToDateLineNumberProvider lineNumberProvider) {
      this.vcs = vcs;
      this.annotation = annotation;
      this.lineNumberProvider = lineNumberProvider;
    }
  }

  private static class EventData {
    public final @NotNull DiffViewerBase viewer;
    public final @NotNull ViewerAnnotator annotator;

    EventData(@NotNull DiffViewerBase viewer, @NotNull ViewerAnnotator annotator) {
      this.viewer = viewer;
      this.annotator = annotator;
    }
  }

  @ApiStatus.Internal
  public static final class Provider implements AnnotateToggleAction.Provider {
    @Override
    public boolean isEnabled(AnActionEvent e) {
      return AnnotateDiffViewerAction.isEnabled(e);
    }

    @Override
    public boolean isSuspended(@NotNull AnActionEvent e) {
      return AnnotateDiffViewerAction.isSuspended(e);
    }

    @Override
    public boolean isAnnotated(AnActionEvent e) {
      return AnnotateDiffViewerAction.isAnnotated(e);
    }

    @Override
    public void perform(@NotNull AnActionEvent e, boolean selected) {
      AnnotateDiffViewerAction.perform(e, selected);
    }
  }
}
