// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.ide.DataManager;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.TextAnnotationGutterProvider;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.changes.VcsAnnotationLocalChangesListener;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.UpToDateLineNumberProviderImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 * @author lesya
 */
public final class AnnotateToggleAction extends ToggleAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(AnnotateToggleAction.class);

  public static final ExtensionPointName<Provider> EP_NAME =
    new ExtensionPointName<>("com.intellij.openapi.vcs.actions.AnnotateToggleAction.Provider");

  public AnnotateToggleAction() {
    getTemplatePresentation().setKeepPopupOnPerform(KeepPopupOnPerform.Never);
    setEnabledInModalContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Provider provider = getProvider(e);
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(provider != null);
    if (provider != null) {
      presentation.setText(provider.getActionName(e));
    }
  }

  private static @Nls @NotNull String getVcsActionName(@Nullable Project project) {
    String defaultName = ActionsBundle.message("action.Annotate.text");
    if (project == null) return defaultName;

    Set<String> names = ContainerUtil.map2Set(ProjectLevelVcsManager.getInstance(project).getAllActiveVcss(), vcs -> {
      AnnotationProvider provider = vcs.getAnnotationProvider();
      if (provider != null) {
        String customActionName = provider.getCustomActionName();
        if (customActionName != null) return customActionName;
        String actionName = provider.getActionName();
        if (!actionName.isEmpty()) return actionName;
      }
      return defaultName;
    });

    return ContainerUtil.getOnlyItem(names, defaultName);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    Provider provider = getProvider(e);
    return provider != null && (provider.isAnnotated(e) || provider.isSuspended(e));
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean selected) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor != null) {
      AnnotationWarningUserData warningUserData = editor.getUserData(AnnotateDataKeys.WARNING_DATA);
      if (warningUserData != null && !warningUserData.getWarning().getShowAnnotation()) {
        warningUserData.getForceAnnotate().run();
        return;
      }
    }

    Provider provider = getProvider(e);
    if (provider != null && !provider.isSuspended(e)) {
      provider.perform(e, selected);
    }
  }

  public static void doAnnotate(final @NotNull Editor editor,
                                final @NotNull Project project,
                                final @NotNull FileAnnotation fileAnnotation,
                                final @NotNull AbstractVcs vcs) {
    if (project.isDisposed() || editor.isDisposed()) return;
    UpToDateLineNumberProvider upToDateLineNumberProvider = new UpToDateLineNumberProviderImpl(editor.getDocument(), project);
    doAnnotate(editor, project, fileAnnotation, vcs, upToDateLineNumberProvider);
  }

  public static void doAnnotate(final @NotNull Editor editor,
                                final @NotNull Project project,
                                final @NotNull FileAnnotation fileAnnotation,
                                final @NotNull AbstractVcs vcs,
                                final @NotNull UpToDateLineNumberProvider upToDateLineNumbers) {
    doAnnotate(editor, project, fileAnnotation, vcs, upToDateLineNumbers, project.getService(AnnotateWarningsService.class));
  }

  private static void doAnnotate(final @NotNull Editor editor,
                                 final @NotNull Project project,
                                 final @NotNull FileAnnotation fileAnnotation,
                                 final @NotNull AbstractVcs vcs,
                                 final @NotNull UpToDateLineNumberProvider upToDateLineNumbers,
                                 final @Nullable AnnotateWarningsService warningsService) {
    ThreadingAssertions.assertEventDispatchThread();
    if (project.isDisposed() || editor.isDisposed()) return;

    AnnotationWarning warning = warningsService != null ? warningsService.getWarning(fileAnnotation, upToDateLineNumbers) : null;
    if (warning == null) {
      resetWarningData(editor);
      updateEditorNotifications(editor, project);
    }
    else {
      editor.putUserData(AnnotateDataKeys.WARNING_DATA, new AnnotationWarningUserData(warning, () -> {
        doAnnotate(editor, project, fileAnnotation, vcs, upToDateLineNumbers, null);
      }));
      updateEditorNotifications(editor, project);
      if (!warning.getShowAnnotation()) {
        return;
      }
    }

    fileAnnotation.setCloser(() -> UIUtil.invokeLaterIfNeeded(() -> {
      if (project.isDisposed()) return;
      closeVcsAnnotations(editor);
    }));

    fileAnnotation.setReloader(newFileAnnotation -> {
      if (project.isDisposed()) return;
      if (hasVcsAnnotations(editor)) {
        if (newFileAnnotation != null) {
          assert Comparing.equal(fileAnnotation.getFile(), newFileAnnotation.getFile());
          doAnnotate(editor, project, newFileAnnotation, vcs, upToDateLineNumbers, null);
        }
        else {
          DataContext dataContext = DataManager.getInstance().getDataContext(editor.getComponent());
          AnActionEvent event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext);
          Provider provider = getProvider(event);

          if (provider != null && provider.isEnabled(event) && !provider.isSuspended(event)) {
            provider.perform(event, true);
          }
          else {
            closeVcsAnnotations(editor);
          }
        }
      }
    });

    if (fileAnnotation.isClosed()) return;


    Disposable disposable = new Disposable() {
      @Override
      public void dispose() {
        fileAnnotation.dispose();
      }
    };

    VcsAnnotationLocalChangesListener changesListener = ProjectLevelVcsManager.getInstance(project).getAnnotationLocalChangesListener();
    changesListener.registerAnnotation(fileAnnotation);
    Disposer.register(disposable, () -> changesListener.unregisterAnnotation(fileAnnotation));
    Disposer.register(disposable, () -> resetWarningData(editor));

    closeVcsAnnotations(editor);

    final List<AnnotationFieldGutter> gutters = new ArrayList<>();
    final AnnotationSourceSwitcher switcher = fileAnnotation.getAnnotationSourceSwitcher();

    final AnnotationPresentation presentation = new AnnotationPresentation(fileAnnotation, upToDateLineNumbers, switcher, disposable);
    presentation.addAction(new ShowDiffFromAnnotation(project, fileAnnotation));
    presentation.addAction(new CopyRevisionNumberFromAnnotateAction(fileAnnotation));
    presentation.addAction(Separator.getInstance());

    final Couple<Map<VcsRevisionNumber, Color>> bgColorMap = computeBgColors(fileAnnotation, editor);
    final Map<VcsRevisionNumber, Integer> historyIds = computeLineNumbers(fileAnnotation);

    gutters.add(new FillerColumn(fileAnnotation, presentation, bgColorMap));
    if (switcher != null) {
      switcher.switchTo(switcher.getDefaultSource());
      final LineAnnotationAspect revisionAspect = switcher.getRevisionAspect();
      final CurrentRevisionAnnotationFieldGutter currentRevisionGutter =
        new CurrentRevisionAnnotationFieldGutter(fileAnnotation, revisionAspect, presentation, bgColorMap);
      final MergeSourceAvailableMarkerGutter mergeSourceGutter =
        new MergeSourceAvailableMarkerGutter(fileAnnotation, presentation, bgColorMap);

      SwitchAnnotationSourceAction switchAction = new SwitchAnnotationSourceAction(switcher);
      presentation.addAction(switchAction);
      switchAction.addSourceSwitchListener(currentRevisionGutter);
      switchAction.addSourceSwitchListener(mergeSourceGutter);

      currentRevisionGutter.accept(switcher.getDefaultSource());
      mergeSourceGutter.accept(switcher.getDefaultSource());

      gutters.add(currentRevisionGutter);
      gutters.add(mergeSourceGutter);
    }

    List<LineAnnotationAspect> aspects = Arrays.asList(fileAnnotation.getAspects());
    List<LineAnnotationAspect> fromExt =
      ContainerUtil.mapNotNull(AnnotationGutterColumnProvider.EP_NAME.getExtensions(), extension -> extension.createColumn(fileAnnotation));
    for (LineAnnotationAspect aspect : ContainerUtil.concat(aspects, fromExt)) {
      gutters.add(new AspectAnnotationFieldGutter(fileAnnotation, aspect, presentation, bgColorMap));
    }


    if (historyIds != null) {
      gutters.add(new HistoryIdColumn(fileAnnotation, presentation, bgColorMap, historyIds));
    }
    if (!ExperimentalUI.isNewUI()) {
      gutters.add(new HighlightedAdditionalColumn(fileAnnotation, presentation, bgColorMap));
    }
    final AnnotateActionGroup actionGroup = new AnnotateActionGroup(fileAnnotation, gutters, bgColorMap);
    presentation.addAction(actionGroup, 1);
    gutters.add(new ExtraFieldGutter(fileAnnotation, presentation, bgColorMap, actionGroup));

    presentation.addAction(new AnnotateCurrentRevisionAction(fileAnnotation, vcs));
    presentation.addAction(new AnnotatePreviousRevisionAction(fileAnnotation, vcs));
    addActionsFromExtensions(presentation, fileAnnotation);

    for (AnnotationFieldGutter gutter : gutters) {
      final AnnotationGutterLineConvertorProxy proxy = gutter instanceof TextAnnotationGutterProvider.Filler
                                                       ? new AnnotationGutterLineConvertorProxy.Filler(upToDateLineNumbers, gutter)
                                                       : new AnnotationGutterLineConvertorProxy(upToDateLineNumbers, gutter);
      if (gutter.isGutterAction()) {
        editor.getGutter().registerTextAnnotation(proxy, proxy);
      }
      else {
        editor.getGutter().registerTextAnnotation(proxy);
      }
    }

    InlineDiffFromAnnotation.showDiffOnHover(editor, fileAnnotation, presentation, disposable);
  }

  private static void updateEditorNotifications(@NotNull Editor editor, @NotNull Project project) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    if (file != null) {
      EditorNotifications.getInstance(project).updateNotifications(file);
    }
  }

  static @NotNull List<ActiveAnnotationGutter> getVcsAnnotations(@NotNull Editor editor) {
    List<TextAnnotationGutterProvider> annotations = editor.getGutter().getTextAnnotations();
    return ContainerUtil.filterIsInstance(annotations, ActiveAnnotationGutter.class);
  }

  static boolean hasVcsAnnotations(@NotNull Editor editor) {
    return !getVcsAnnotations(editor).isEmpty();
  }

  static void closeVcsAnnotations(@NotNull Editor editor) {
    List<ActiveAnnotationGutter> vcsAnnotations = getVcsAnnotations(editor);
    editor.getGutter().closeTextAnnotations(vcsAnnotations);
  }

  private static void resetWarningData(@NotNull Editor editor) {
    editor.putUserData(AnnotateDataKeys.WARNING_DATA, null);
  }

  static @Nullable TextAnnotationPresentation getAnnotationPresentation(@NotNull Editor editor) {
    List<ActiveAnnotationGutter> annotations = getVcsAnnotations(editor);
    for (TextAnnotationGutterProvider annotation : annotations) {
      if (annotation instanceof AnnotationGutterLineConvertorProxy) {
        annotation = ((AnnotationGutterLineConvertorProxy)annotation).getDelegate();
      }
      if (annotation instanceof AnnotationFieldGutter) {
        return ((AnnotationFieldGutter)annotation).getPresentation();
      }
    }
    return null;
  }

  static @Nullable FileAnnotation getFileAnnotation(@NotNull Editor editor) {
    TextAnnotationPresentation presentation = getAnnotationPresentation(editor);
    if (presentation instanceof AnnotationPresentation) return ((AnnotationPresentation)presentation).getFileAnnotation();
    return null;
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

  private static @Nullable Map<VcsRevisionNumber, Integer> computeLineNumbers(@NotNull FileAnnotation fileAnnotation) {
    final Map<VcsRevisionNumber, Integer> numbers = new HashMap<>();
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

  private static @NotNull Couple<Map<VcsRevisionNumber, Color>> computeBgColors(@NotNull FileAnnotation fileAnnotation, @NotNull Editor editor) {
    Map<VcsRevisionNumber, Color> commitOrderColors = new HashMap<>();
    Map<VcsRevisionNumber, Color> commitAuthorColors = new HashMap<>();

    EditorColorsScheme colorScheme = editor.getColorsScheme();
    AnnotationsSettings settings = AnnotationsSettings.getInstance();
    List<Color> authorsColorPalette = settings.getAuthorsColors(colorScheme);
    List<Color> orderedColorPalette = settings.getOrderedColors(colorScheme);

    FileAnnotation.AuthorsMappingProvider authorsMappingProvider = fileAnnotation.getAuthorsMappingProvider();
    if (authorsMappingProvider != null) {
      Map<VcsRevisionNumber, String> authorsMap = authorsMappingProvider.getAuthors();

      Map<String, Color> authorColors = new HashMap<>();
      for (String author : ContainerUtil.sorted(new HashSet<>(authorsMap.values()))) {
        int index = authorColors.size();
        Color color = authorsColorPalette.get(index % authorsColorPalette.size());
        authorColors.put(author, color);
      }

      for (Map.Entry<VcsRevisionNumber, String> entry : authorsMap.entrySet()) {
        VcsRevisionNumber revision = entry.getKey();
        String author = entry.getValue();
        Color color = authorColors.get(author);
        commitAuthorColors.put(revision, color);
      }
    }

    FileAnnotation.RevisionsOrderProvider revisionsOrderProvider = fileAnnotation.getRevisionsOrderProvider();
    if (revisionsOrderProvider != null) {
      List<List<VcsRevisionNumber>> orderedRevisions = revisionsOrderProvider.getOrderedRevisions();

      int revisionsCount = orderedRevisions.size();
      for (int index = 0; index < revisionsCount; index++) {
        Color color = orderedColorPalette.get(orderedColorPalette.size() * index / revisionsCount);

        for (VcsRevisionNumber number : orderedRevisions.get(index)) {
          commitOrderColors.put(number, color);
        }
      }
    }

    return Couple.of(commitOrderColors.size() > 1 ? commitOrderColors : null,
                     commitAuthorColors.size() > 1 ? commitAuthorColors : null);
  }

  private static @Nullable Provider getProvider(AnActionEvent e) {
    return EP_NAME.findFirstSafe(provider -> provider.isEnabled(e));
  }

  public interface Provider {
    @CalledInAny
    boolean isEnabled(AnActionEvent e);

    /**
     * @return whether annotations are already being computed in background
     */
    @CalledInAny
    boolean isSuspended(@NotNull AnActionEvent e);

    @CalledInAny
    boolean isAnnotated(AnActionEvent e);

    @RequiresEdt
    void perform(@NotNull AnActionEvent e, boolean selected);

    default @Nls(capitalization = Nls.Capitalization.Title) String getActionName(@NotNull AnActionEvent e) {
      return getVcsActionName(e.getProject());
    }
  }
}
