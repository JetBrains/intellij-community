// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.uiDesigner.core.GridConstraints.*;

public class ExtensibleUIProgressWindow extends ProgressWindow {

  public ExtensibleUIProgressWindow(boolean shouldShowCancel,
                                    boolean shouldShowBackground,
                                    @Nullable Project project,
                                    @Nullable JComponent parentComponent,
                                    @Nullable @NlsContexts.Button String cancelText,
                                    @Nullable Icon processIcon,
                                    @Nullable String helpText,
                                    @Nullable String helpToolTipText) {
    super(shouldShowCancel, shouldShowBackground, project, parentComponent, cancelText);
    UIUtil.invokeLaterIfNeeded(() -> {
      var anySize = new Dimension(-1, -1);
      var defaultSizePolicy = SIZEPOLICY_CAN_GROW | SIZEPOLICY_CAN_SHRINK;

      JPanel panel = getDialog().myPanel;
      GridLayoutManager oldLayoutManager = (GridLayoutManager)panel.getLayout();
      GridLayoutManager newLayoutManager = new GridLayoutManager(oldLayoutManager.getRowCount() + 1, oldLayoutManager.getColumnCount() + 2,
                                                      oldLayoutManager.getMargin(), oldLayoutManager.getHGap(), oldLayoutManager.getVGap(), oldLayoutManager.isSameSizeHorizontally(),
                                                      oldLayoutManager.isSameSizeVertically());
      var oldComponents = panel.getComponents();
      panel.removeAll();
      panel.setLayout(newLayoutManager);

      //adding process icon
      if(processIcon != null) {
        JLabel jLabel = new JLabel(processIcon);
        jLabel.setBorder(BorderFactory.createEmptyBorder(0, JBUI.scale(20), 0, 0));
        panel.add(jLabel,
                                new GridConstraints(1, 0, 1, 1,
                                                    ANCHOR_WEST, defaultSizePolicy, 0, 0, anySize, anySize, anySize));
      }

      //adding old components back
      panel.add(oldComponents[0], new GridConstraints(1, 1, 1, 2,
                                                                    0, defaultSizePolicy, 3, 3, anySize, anySize, anySize));
      panel.add(oldComponents[1], new GridConstraints(0, 0, 1, 3,
                                                                    0, defaultSizePolicy, 7, 0, anySize, anySize, anySize));

      //help text and tooltip in the bottom of the window
      if (helpText != null && helpToolTipText != null) {
        JLabel jTextLabel = new JLabel(helpText);
        jTextLabel.setBorder(BorderFactory.createEmptyBorder(0, JBUI.scale(10), JBUI.scale(30), 0));
        panel.add(jTextLabel, new GridConstraints(2, 1, 1, 1,
                                                                ANCHOR_WEST, 0, 0, 0, anySize, anySize, anySize));

        JLabel helpIcon = new JLabel(AllIcons.General.ContextHelp);
        helpIcon.setBorder(BorderFactory.createEmptyBorder(0, 0, JBUI.scale(30), JBUI.scale(10)));
        helpIcon.setToolTipText(helpToolTipText);
        panel.add(helpIcon, new GridConstraints(2, 2, 1, 1,
                                                              ANCHOR_WEST, 0, SIZEPOLICY_CAN_SHRINK, 0, anySize, anySize, anySize));
      }

      panel.doLayout();
      panel.updateUI();
    });
  }
}
