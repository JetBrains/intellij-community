// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push;

import com.intellij.dvcs.push.VcsPushOptionValue;
import com.intellij.dvcs.push.VcsPushOptionsPanel;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBInsets;
import git4idea.i18n.GitBundle;
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
    String checkboxText = GitBundle.message("push.dialog.push.tags");
    if (followTagsSupported) {
      checkboxText += ": ";
    }
    myPushTags = new JBCheckBox(checkboxText);
    myPushTags.setMnemonic('T');
    myPushTags.setSelected(defaultMode != null);

    myPushTagsMode = new ComboBox<>(GitPushTagMode.getValues());
    myPushTagsMode.setRenderer(SimpleListCellRenderer.create("", GitPushTagModeKt::localizedTitle));
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

    myRunHooks = new JBCheckBox(GitBundle.message("checkbox.run.git.hooks"));
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
      add(Box.createHorizontalStrut(calcStrutWidth(16, myPushTagsMode, myRunHooks)));
      add(myRunHooks);
    }
  }

  private static int calcStrutWidth(int plannedWidth, @NotNull JComponent leftComponent, @NotNull JComponent rightComponent) {
    return JBUIScale.scale(plannedWidth) - JBInsets.create(rightComponent.getInsets()).left - JBInsets.create(leftComponent.getInsets()).right;
  }

  @Nullable
  @Override
  public VcsPushOptionValue getValue() {
    GitPushTagMode selectedTagMode = !myPushTagsMode.isVisible() ? GitPushTagMode.ALL : (GitPushTagMode)myPushTagsMode.getSelectedItem();
    GitPushTagMode tagMode = myPushTags.isSelected() ? selectedTagMode : null;
    return new GitVcsPushOptionValue(tagMode, myRunHooks.isVisible() && !myRunHooks.isSelected());
  }

  @NotNull
  @Override
  public OptionsPanelPosition getPosition() {
    return OptionsPanelPosition.SOUTH;
  }
}
