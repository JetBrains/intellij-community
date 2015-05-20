/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.zmlx.hg4idea.push;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.PushTargetPanel;
import com.intellij.dvcs.push.VcsError;
import com.intellij.dvcs.push.ui.PushTargetEditorListener;
import com.intellij.dvcs.push.ui.PushTargetTextField;
import com.intellij.dvcs.push.ui.VcsEditableTextComponent;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TextFieldWithAutoCompletion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import java.awt.*;
import java.util.List;

public class HgPushTargetPanel extends PushTargetPanel<HgTarget> {

  private final static String ENTER_REMOTE = "Enter Remote";
  private final HgRepository myRepository;
  private final String myBranchName;
  private final TextFieldWithAutoCompletion<String> myDestTargetPanel;
  private final VcsEditableTextComponent myTargetRenderedComponent;

  public HgPushTargetPanel(@NotNull HgRepository repository, @Nullable HgTarget defaultTarget) {
    setLayout(new BorderLayout());
    setOpaque(false);
    myRepository = repository;
    myBranchName = myRepository.getCurrentBranchName();
    final List<String> targetVariants = HgUtil.getTargetNames(repository);
    String defaultText = defaultTarget != null ? defaultTarget.getPresentation() : "";
    myTargetRenderedComponent = new VcsEditableTextComponent("<a href=''>" + defaultText + "</a>", null);
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
      renderer.append(ENTER_REMOTE, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES, this);
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
    myDestTargetPanel.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        super.documentChanged(e);
        listener.onTargetInEditModeChanged(myDestTargetPanel.getText());
      }
    });
  }
}
