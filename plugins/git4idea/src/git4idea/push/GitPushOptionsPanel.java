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
package git4idea.push;

import com.intellij.dvcs.push.VcsPushOptionValue;
import com.intellij.dvcs.push.VcsPushOptionsPanel;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public class GitPushOptionsPanel extends VcsPushOptionsPanel {

  @NotNull private final JBCheckBox myPushTags;
  @NotNull private final ComboBox<GitPushTagMode> myPushTagsMode;
  @NotNull private final JBCheckBox myRunHooks;

  public GitPushOptionsPanel(@Nullable GitPushTagMode defaultMode, boolean followTagsSupported, boolean showSkipHookOption) {
    String checkboxText = "Push Tags";
    if (followTagsSupported) {
      checkboxText += ": ";
    }
    myPushTags = new JBCheckBox(checkboxText);
    myPushTags.setMnemonic('T');
    myPushTags.setSelected(defaultMode != null);

    myPushTagsMode = new ComboBox<>(GitPushTagMode.getValues());
    myPushTagsMode.setRenderer(new ListCellRendererWrapper<GitPushTagMode>() {
      @Override
      public void customize(JList list, GitPushTagMode value, int index, boolean selected, boolean hasFocus) {
        setText(value.getTitle());
      }
    });
    myPushTagsMode.setEnabled(myPushTags.isSelected());
    if (defaultMode != null) {
      myPushTagsMode.setSelectedItem(defaultMode);
    }

    myPushTags.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        myPushTagsMode.setEnabled(myPushTags.isSelected());
      }
    });
    myPushTagsMode.setVisible(followTagsSupported);

    myRunHooks = new JBCheckBox("Run Git hooks");
    myRunHooks.setMnemonic(KeyEvent.VK_H);
    myRunHooks.setSelected(true);
    myRunHooks.setVisible(showSkipHookOption);

    setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
    add(myPushTags);
    if (myPushTagsMode.isVisible()) {
      add(Box.createHorizontalStrut(calcStrutWidth(8, myPushTags, myPushTagsMode)));
      add(myPushTagsMode);
    }
    if (myRunHooks.isVisible()) {
      add(Box.createHorizontalStrut(calcStrutWidth(40, myPushTagsMode, myRunHooks)));
      add(myRunHooks);
    }
  }

  private static int calcStrutWidth(int plannedWidth, @NotNull JComponent leftComponent, @NotNull JComponent rightComponent) {
    return JBUI.scale(plannedWidth) - JBUI.insets(rightComponent.getInsets()).left - JBUI.insets(leftComponent.getInsets()).right;
  }

  @Nullable
  @Override
  public VcsPushOptionValue getValue() {
    GitPushTagMode selectedTagMode = !myPushTagsMode.isVisible() ? GitPushTagMode.ALL : (GitPushTagMode)myPushTagsMode.getSelectedItem();
    GitPushTagMode tagMode = myPushTags.isSelected() ? selectedTagMode : null;
    return new GitVcsPushOptionValue(tagMode, myRunHooks.isVisible() && !myRunHooks.isSelected());
  }

}
