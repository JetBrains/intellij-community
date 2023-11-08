// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.push;

import com.intellij.dvcs.push.VcsPushOptionsPanel;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;

import java.awt.*;

public class HgPushOptionsPanel extends VcsPushOptionsPanel {

  private final JBCheckBox myPushBookmarkCheckBox;

  public HgPushOptionsPanel() {
    setLayout(new BorderLayout());
    myPushBookmarkCheckBox = new JBCheckBox(HgBundle.message("checkbox.export.active.bookmarks"));
    add(myPushBookmarkCheckBox, BorderLayout.WEST);
  }

  @Override
  public @Nullable HgVcsPushOptionValue getValue() {
    return myPushBookmarkCheckBox.isSelected() ? HgVcsPushOptionValue.Current : null;
  }

  @Override
  public @NotNull OptionsPanelPosition getPosition() {
    return OptionsPanelPosition.SOUTH;
  }
}
