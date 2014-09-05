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
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;

public class HgPushTargetPanel extends PushTargetPanel<HgTarget> {

  private final static String ENTER_REMOTE = "Enter Remote";
  private final HgRepository myRepository;
  private final TextFieldWithAutoCompletion<String> myDestTargetPanel;
  private String myOldText;

  public HgPushTargetPanel(@NotNull HgRepository repository, @NotNull String defaultTargetName) {
    setLayout(new BorderLayout());
    setOpaque(false);
    myRepository = repository;
    final List<String> targetVariants = HgUtil.getTargetNames(repository);
    TextFieldWithAutoCompletionListProvider<String> provider =
      new TextFieldWithAutoCompletion.StringsCompletionProvider(targetVariants, null) {
        @Override
        public int compare(String item1, String item2) {
          return Integer.valueOf(ContainerUtil.indexOf(targetVariants, item1)).compareTo(ContainerUtil.indexOf(targetVariants, item2));
        }
      };
    myDestTargetPanel = new TextFieldWithAutoCompletion<String>(repository.getProject(), provider, true, defaultTargetName) {

      @Override
      public boolean shouldHaveBorder() {
        return false;
      }

      @Override
      protected void updateBorder(@NotNull final EditorEx editor) {
      }
    };
    myOldText = defaultTargetName;
    myDestTargetPanel.setBorder(UIUtil.getTableFocusCellHighlightBorder());
    myDestTargetPanel.setOneLineMode(true);
    FocusAdapter focusListener = new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myDestTargetPanel.selectAll();
      }
    };
    myDestTargetPanel.addFocusListener(focusListener);
    add(myDestTargetPanel, BorderLayout.CENTER);
  }

  @Override
  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    String targetText = myDestTargetPanel.getText();
    if (StringUtil.isEmptyOrSpaces(targetText)) {
      renderer.append(ENTER_REMOTE, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES, this);
    }
    renderer.append(targetText, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES, this);
  }

  @Override
  @NotNull
  public HgTarget getValue() {
    return createValidPushTarget();
  }

  @NotNull
  private HgTarget createValidPushTarget() {
    return new HgTarget(myDestTargetPanel.getText());
  }

  @Override
  public void fireOnCancel() {
    myDestTargetPanel.setText(myOldText);
  }

  @Override
  public void fireOnChange() {
    myOldText = myDestTargetPanel.getText();
  }

  @Nullable
  public ValidationInfo verify() {
    if (StringUtil.isEmptyOrSpaces(myDestTargetPanel.getText())) {
      return new ValidationInfo(VcsError.createEmptyTargetError(DvcsUtil.getShortRepositoryName(myRepository)).getText(), this);
    }
    return null;
  }
}
