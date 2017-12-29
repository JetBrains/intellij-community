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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.annotate.AnnotationGutterActionProvider;
import com.intellij.openapi.vcs.annotate.AnnotationSourceSwitcher;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect;
import com.intellij.openapi.vcs.changes.VcsAnnotationLocalChangesListener;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.UpToDateLineNumberProviderImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.LightColors;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 * @author: lesya
 */
public class AnnotateToggleAction extends ToggleAction implements DumbAware {
  public static final ExtensionPointName<Provider> EP_NAME =
    ExtensionPointName.create("com.intellij.openapi.vcs.actions.AnnotateToggleAction.Provider");

  public AnnotateToggleAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Provider provider = getProvider(e);
    e.getPresentation().setEnabled(provider != null && !provider.isSuspended(e));
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    Provider provider = getProvider(e);
    return provider != null && provider.isAnnotated(e);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean selected) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor != null) {
      MyEditorNotificationPanel notificationPanel = ObjectUtils.tryCast(editor.getHeaderComponent(), MyEditorNotificationPanel.class);
      if (notificationPanel != null) {
        notificationPanel.showAnnotations();
        return;
      }
    }

    Provider provider = getProvider(e);
    if (provider != null) provider.perform(e, selected);
  }

  public static void doAnnotate(@NotNull final Editor editor,
                                @NotNull final Project project,
                                @Nullable final VirtualFile currentFile,
                                @NotNull final FileAnnotation fileAnnotation,
                                @NotNull final AbstractVcs vcs) {
    UpToDateLineNumberProvider upToDateLineNumberProvider = new UpToDateLineNumberProviderImpl(editor.getDocument(), project);
    doAnnotate(editor, project, currentFile, fileAnnotation, vcs, upToDateLineNumberProvider);
  }

  public static void doAnnotate(@NotNull final Editor editor,
                                @NotNull final Project project,
                                @Nullable final VirtualFile currentFile,
                                @NotNull final FileAnnotation fileAnnotation,
                                @NotNull final AbstractVcs vcs,
                                @NotNull final UpToDateLineNumberProvider upToDateLineNumbers) {
    doAnnotate(editor, project, currentFile, fileAnnotation, vcs, upToDateLineNumbers, true);
  }

  private static void doAnnotate(@NotNull final Editor editor,
                                 @NotNull final Project project,
                                 @Nullable final VirtualFile currentFile,
                                 @NotNull final FileAnnotation fileAnnotation,
                                 @NotNull final AbstractVcs vcs,
                                 @NotNull final UpToDateLineNumberProvider upToDateLineNumbers,
                                 final boolean warnAboutSuspiciousAnnotations) {
    if (project.isDisposed() || editor.isDisposed()) return;

    if (warnAboutSuspiciousAnnotations) {
      int expectedLines = Math.max(upToDateLineNumbers.getLineCount(), 1);
      int actualLines = Math.max(fileAnnotation.getLineCount(), 1);
      if (Math.abs(expectedLines - actualLines) > 1) { // 1 - for different conventions about files ending with line separator
        editor.setHeaderComponent(new MyEditorNotificationPanel(editor, vcs, () -> {
          doAnnotate(editor, project, currentFile, fileAnnotation, vcs, upToDateLineNumbers, false);
        }));
        return;
      }
    }


    fileAnnotation.setCloser(() -> {
      UIUtil.invokeLaterIfNeeded(() -> {
        if (project.isDisposed()) return;
        editor.getGutter().closeAllAnnotations();
      });
    });

    fileAnnotation.setReloader(newFileAnnotation -> {
      if (editor.getGutter().isAnnotationsShown()) {
        assert Comparing.equal(fileAnnotation.getFile(), newFileAnnotation.getFile());
        doAnnotate(editor, project, currentFile, newFileAnnotation, vcs, upToDateLineNumbers, false);
      }
    });

    if (fileAnnotation.isClosed()) return;


    Disposable disposable = new Disposable() {
      @Override
      public void dispose() {
        fileAnnotation.dispose();
      }
    };

    if (fileAnnotation.getFile() != null && fileAnnotation.getFile().isInLocalFileSystem()) {
      VcsAnnotationLocalChangesListener changesListener = ProjectLevelVcsManager.getInstance(project).getAnnotationLocalChangesListener();

      changesListener.registerAnnotation(fileAnnotation.getFile(), fileAnnotation);
      Disposer.register(disposable, new Disposable() {
        @Override
        public void dispose() {
          changesListener.unregisterAnnotation(fileAnnotation.getFile(), fileAnnotation);
        }
      });
    }

    editor.getGutter().closeAllAnnotations();

    final List<AnnotationFieldGutter> gutters = new ArrayList<>();
    final AnnotationSourceSwitcher switcher = fileAnnotation.getAnnotationSourceSwitcher();

    final AnnotationPresentation presentation = new AnnotationPresentation(fileAnnotation, upToDateLineNumbers, switcher, disposable);
    if (currentFile != null && vcs.getCommittedChangesProvider() != null) {
      presentation.addAction(new ShowDiffFromAnnotation(fileAnnotation, vcs, currentFile));
    }
    presentation.addAction(new CopyRevisionNumberFromAnnotateAction(fileAnnotation));
    presentation.addAction(Separator.getInstance());

    final Couple<Map<VcsRevisionNumber, Color>> bgColorMap = computeBgColors(fileAnnotation, editor);
    final Map<VcsRevisionNumber, Integer> historyIds = computeLineNumbers(fileAnnotation);

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

      currentRevisionGutter.consume(switcher.getDefaultSource());
      mergeSourceGutter.consume(switcher.getDefaultSource());

      gutters.add(currentRevisionGutter);
      gutters.add(mergeSourceGutter);
    }

    final LineAnnotationAspect[] aspects = fileAnnotation.getAspects();
    for (LineAnnotationAspect aspect : aspects) {
      gutters.add(new AspectAnnotationFieldGutter(fileAnnotation, aspect, presentation, bgColorMap));
    }


    if (historyIds != null) {
      gutters.add(new HistoryIdColumn(fileAnnotation, presentation, bgColorMap, historyIds));
    }
    gutters.add(new HighlightedAdditionalColumn(fileAnnotation, presentation, bgColorMap));
    final AnnotateActionGroup actionGroup = new AnnotateActionGroup(gutters, bgColorMap);
    presentation.addAction(actionGroup, 1);
    gutters.add(new ExtraFieldGutter(fileAnnotation, presentation, bgColorMap, actionGroup));

    presentation.addAction(new AnnotateCurrentRevisionAction(fileAnnotation, vcs));
    presentation.addAction(new AnnotatePreviousRevisionAction(fileAnnotation, vcs));
    addActionsFromExtensions(presentation, fileAnnotation);

    for (AnnotationFieldGutter gutter : gutters) {
      final AnnotationGutterLineConvertorProxy proxy = new AnnotationGutterLineConvertorProxy(upToDateLineNumbers, gutter);
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

  @Nullable
  private static Couple<Map<VcsRevisionNumber, Color>> computeBgColors(@NotNull FileAnnotation fileAnnotation, @NotNull Editor editor) {
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
      for (String author : ContainerUtil.sorted(authorsMap.values(), Comparing::compare)) {
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

  @Nullable
  private static Provider getProvider(AnActionEvent e) {
    for (Provider provider : EP_NAME.getExtensions()) {
      if (provider.isEnabled(e)) return provider;
    }
    return null;
  }

  public interface Provider {
    boolean isEnabled(AnActionEvent e);

    boolean isSuspended(AnActionEvent e);

    boolean isAnnotated(AnActionEvent e);

    void perform(AnActionEvent e, boolean selected);
  }

  private static class MyEditorNotificationPanel extends EditorNotificationPanel {
    private final Editor myEditor;
    private final Runnable myShowAnnotations;

    public MyEditorNotificationPanel(@NotNull Editor editor, @NotNull AbstractVcs vcs, @NotNull Runnable doShowAnnotations) {
      super(LightColors.RED);
      myEditor = editor;
      myShowAnnotations = doShowAnnotations;

      setText(VcsBundle.message("annotation.wrong.line.number.notification.text", vcs.getDisplayName()));

      createActionLabel("Display anyway", () -> {
        showAnnotations();
      });

      createActionLabel("Hide", () -> {
        hideNotification();
      }).setToolTipText("Hide this notification");
    }

    public void showAnnotations() {
      hideNotification();
      myShowAnnotations.run();
    }

    private void hideNotification() {
      setVisible(false);
      if (myEditor.getHeaderComponent() == this) myEditor.setHeaderComponent(null);
    }
  }
}
