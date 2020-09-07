// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.push;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.PushTargetPanel;
import com.intellij.dvcs.push.VcsError;
import com.intellij.dvcs.push.ui.PushTargetEditorListener;
import com.intellij.dvcs.push.ui.PushTargetTextField;
import com.intellij.dvcs.push.ui.VcsEditableTextComponent;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import java.awt.*;
import java.util.List;

public class HgPushTargetPanel extends PushTargetPanel<HgTarget> {

  private final HgRepository myRepository;
  private final @NlsSafe String myBranchName;
  private final TextFieldWithCompletion myDestTargetPanel;
  private final VcsEditableTextComponent myTargetRenderedComponent;

  public HgPushTargetPanel(@NotNull HgRepository repository, @NotNull HgPushSource source, @Nullable HgTarget defaultTarget) {
    setLayout(new BorderLayout());
    setOpaque(false);
    myRepository = repository;
    myBranchName = source.getBranch();
    final List<String> targetVariants = HgUtil.getTargetNames(repository);
    String defaultText = defaultTarget != null ? defaultTarget.getPresentation() : "";
    myTargetRenderedComponent = new VcsEditableTextComponent(HtmlChunk.link("", defaultText).toString(), null);
    myDestTargetPanel = new PushTargetTextField(repository.getProject(), targetVariants, defaultText);
    add(myDestTargetPanel, BorderLayout.CENTER);
  }

  @Override
  public void render(@NotNull ColoredTreeCellRenderer renderer, boolean isSelected, boolean isActive, @Nullable String forceRenderedText) {
    if (forceRenderedText != null) {
      myDestTargetPanel.setText(forceRenderedText);
      renderer.append(forceRenderedText);
      return;
    }
    String targetText = myDestTargetPanel.getText();
    if (StringUtil.isEmptyOrSpaces(targetText)) {
      renderer.append(HgBundle.message("action.hg4idea.push.enter.remote"), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES, myTargetRenderedComponent);
    }
    myTargetRenderedComponent.setSelected(isSelected);
    myTargetRenderedComponent.setTransparent(!isActive);
    myTargetRenderedComponent.render(renderer);
  }

  @Override
  @Nullable
  public HgTarget getValue() {
    return createValidPushTarget();
  }

  @NotNull
  private HgTarget createValidPushTarget() {
    return new HgTarget(myDestTargetPanel.getText(), myBranchName);
  }

  @Override
  public void fireOnCancel() {
    myDestTargetPanel.setText(myTargetRenderedComponent.getText());
  }

  @Override
  public void fireOnChange() {
    myTargetRenderedComponent.updateLinkText(myDestTargetPanel.getText());
  }

  @Override
  @Nullable
  public ValidationInfo verify() {
    if (StringUtil.isEmptyOrSpaces(myDestTargetPanel.getText())) {
      return new ValidationInfo(VcsError.createEmptyTargetError(DvcsUtil.getShortRepositoryName(myRepository)).getText(), this);
    }
    return null;
  }

  @Override
  public void setFireOnChangeAction(@NotNull Runnable action) {
    // no extra changing components => ignore
  }

  @Override
  public void addTargetEditorListener(@NotNull final PushTargetEditorListener listener) {
    myDestTargetPanel.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        listener.onTargetInEditModeChanged(myDestTargetPanel.getText());
      }
    });
  }
}
