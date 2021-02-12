// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push;

import com.intellij.dvcs.push.VcsPushOptionValue;
import com.intellij.dvcs.push.VcsPushOptionsPanel;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @deprecated Use {@link GitPushOptionsPanel}.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
public class GitPushTagPanel extends VcsPushOptionsPanel {

  private final ComboBox<GitPushTagMode> myCombobox;
  private final JBCheckBox myCheckBox;

  public GitPushTagPanel(@Nullable GitPushTagMode defaultMode, boolean followTagsSupported) {
    String checkboxText = GitBundle.message("push.dialog.push.tags");
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
