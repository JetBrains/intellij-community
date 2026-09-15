// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.core;

import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.PresentationRenderer;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NonEmptyInputValidator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@ApiStatus.Internal
abstract class AbstractAddInlayInternalAction extends AnAction implements DumbAware {
  protected record InlayData(@NotNull String text, boolean relatesToPrecedingText) { }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    EditorImpl editor = ObjectUtils.tryCast(e.getData(CommonDataKeys.EDITOR), EditorImpl.class);
    if (editor == null) return;
    List<Caret> carets = editor.getCaretModel().getAllCarets();
    if (carets.isEmpty()) return;

    InlayData inlayData = showDialog(project, carets.size());
    if (inlayData == null) return;

    int[] offsets = carets.stream().mapToInt(Caret::getOffset).toArray();
    InlayModel model = editor.getInlayModel();
    for (int offset : offsets) {
      addInlay(project, editor, model, offset, inlayData);
    }
  }

  protected abstract @Nullable InlayData showDialog(@NotNull Project project, int caretCount);

  protected abstract @Nullable Inlay<?> createInlay(@NotNull InlayModel model,
                                                    int offset,
                                                    @NotNull InlayData inlayData,
                                                    @NotNull PresentationRenderer renderer);

  protected static @Nullable InlayData showTextInlayDialog(@NlsContexts.DialogTitle @NotNull String title) {
    Pair<String, Boolean> result = Messages.showInputDialogWithCheckBox(DevCoreBundle.message("dialog.message.inlay.text"), title,
                                                                        DevCoreBundle.message(
                                                                          "dialog.checkbox.relates.to.preceding.text"), false, true,
                                                                        Messages.getInformationIcon(), null, new NonEmptyInputValidator());
    String inlayText = result.getFirst();
    return inlayText == null ? null : new InlayData(inlayText, result.getSecond());
  }

  private void addInlay(@NotNull Project project,
                        @NotNull Editor editor,
                        @NotNull InlayModel model,
                        int offset,
                        @NotNull InlayData inlayData) {
    AtomicReference<Inlay<?>> ref = new AtomicReference<>();
    MenuOnClickPresentation presentation = new MenuOnClickPresentation(
      new PresentationFactory(editor).text(inlayData.text()),
      project,
      () -> Collections.singletonList(new AnAction(DevCoreBundle.messagePointer("action.Anonymous.text.remove.inlay"),
                                                   DevCoreBundle.messagePointer("action.Anonymous.description.remove.this.inlay"),
                                                   AllIcons.Actions.Cancel) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          Inlay<?> inlay = ref.get();
          if (inlay != null) {
            Disposer.dispose(inlay);
          }
        }
      })
    );
    Inlay<?> inlay = createInlay(model, offset, inlayData, new PresentationRenderer(presentation));
    ref.set(inlay);
  }

  @Override
  public final @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    EditorImpl editor = ObjectUtils.tryCast(e.getData(CommonDataKeys.EDITOR), EditorImpl.class);
    presentation.setEnabled(project != null && editor != null);
  }
}
