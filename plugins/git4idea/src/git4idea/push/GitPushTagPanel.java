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
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @deprecated Use {@link GitPushOptionsPanel}.
 */
@Deprecated
public class GitPushTagPanel extends VcsPushOptionsPanel {

  private final ComboBox<GitPushTagMode> myCombobox;
  private final JBCheckBox myCheckBox;

  public GitPushTagPanel(@Nullable GitPushTagMode defaultMode, boolean followTagsSupported) {
    String checkboxText = GitBundle.getString("push.dialog.push.tags");
    if (followTagsSupported) {
      checkboxText += ": ";
    }
    myCheckBox = new JBCheckBox(checkboxText);
    myCheckBox.setMnemonic('T');
    myCheckBox.setSelected(defaultMode != null);

    setLayout(new BorderLayout());
    add(myCheckBox, BorderLayout.WEST);

    if (followTagsSupported) {
      myCombobox = new ComboBox<>(GitPushTagMode.getValues());
      myCombobox.setRenderer(SimpleListCellRenderer.create("", GitPushTagModeKt::localizedTitle));
      myCombobox.setEnabled(myCheckBox.isSelected());
      if (defaultMode != null) {
        myCombobox.setSelectedItem(defaultMode);
      }

      myCheckBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(@NotNull ActionEvent e) {
          myCombobox.setEnabled(myCheckBox.isSelected());
        }
      });
      add(myCombobox, BorderLayout.CENTER);
    }
    else {
      myCombobox = null;
    }
  }

  @Nullable
  @Override
  public VcsPushOptionValue getValue() {
    GitPushTagMode mode =
      myCheckBox.isSelected() ? myCombobox == null ? GitPushTagMode.ALL : (GitPushTagMode)myCombobox.getSelectedItem() : null;
    return new GitVcsPushOptionValue(mode, false);
  }
}
