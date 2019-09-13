// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.push;

import com.intellij.dvcs.push.VcsPushOptionsPanel;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class HgPushOptionsPanel extends VcsPushOptionsPanel {

  private final JBCheckBox myPushBookmarkCheckBox;

  public HgPushOptionsPanel() {
    setLayout(new BorderLayout());
    myPushBookmarkCheckBox = new JBCheckBox("Export active bookmarks");
    add(myPushBookmarkCheckBox, BorderLayout.WEST);
  }

  @Override
  @Nullable
  public HgVcsPushOptionValue getValue() {
    return myPushBookmarkCheckBox.isSelected() ? HgVcsPushOptionValue.Current : null;
  }

  @NotNull
  @Override
  public OptionsPanelPosition getPosition() {
    return OptionsPanelPosition.SOUTH;
  }
}
