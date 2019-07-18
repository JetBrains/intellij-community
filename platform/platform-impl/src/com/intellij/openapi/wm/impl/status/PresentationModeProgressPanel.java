// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TransparentPanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class PresentationModeProgressPanel {
  private final InlineProgressIndicator myProgress;
  private final JBIterable<ProgressButton> myEastButtons;
  private JLabel myText;
  private JProgressBar myProgressBar;
  private JLabel myText2;
  private JPanel myRootPanel;
  private JPanel myButtonPanel;

  public PresentationModeProgressPanel(InlineProgressIndicator progress) {
    myProgress = progress;
    Font font = JBUI.Fonts.label(11);
    myText.setFont(font);
    myText2.setFont(font);
    myText.setIcon(JBUI.scale(EmptyIcon.create(1, 16)));
    myText2.setIcon(JBUI.scale(EmptyIcon.create(1, 16)));
    myEastButtons = myProgress.createEastButtons();
    myButtonPanel.add(InlineProgressIndicator.createButtonPanel(myEastButtons.map(b -> b.button)));
    myRootPanel.setPreferredSize(new JBDimension(250, 60));
    myProgressBar.setPreferredSize(new Dimension(JBUIScale.scale(250), myProgressBar.getPreferredSize().height));
  }

  @NotNull
  private static Color getTextForeground() {
    return EditorColorsManager.getInstance().getGlobalScheme().getDefaultForeground();
  }

  void update() {
    Color color = getTextForeground();
    myText.setForeground(color);
    myText2.setForeground(color);
    myProgressBar.setForeground(color);

    if (!StringUtil.equals(myText.getText(), myProgress.getText())) {
      myText.setText(myProgress.getText());
    }
    if (!StringUtil.equals(myText2.getText(), myProgress.getText2())) {
      myText2.setText(myProgress.getText2());
    }
    if ((myProgress.isIndeterminate() || myProgress.getFraction() == 0.0) != myProgressBar.isIndeterminate()) {
      myProgressBar.setIndeterminate(myProgress.isIndeterminate() || myProgress.getFraction() == 0.0);
      myProgressBar.revalidate();
    }

    if (!myProgressBar.isIndeterminate()) {
      myProgressBar.setValue((int)(myProgress.getFraction() * 99) + 1);
    }

    myEastButtons.forEach(b -> b.updateAction.run());
  }

  @NotNull
  public JComponent getProgressPanel() {
    return myRootPanel;
  }

  private void createUIComponents() {
    myRootPanel = new TransparentPanel(0.5f) {
      @Override
      public boolean isVisible() {
        UISettings ui = UISettings.getInstance();
        return ui.getPresentationMode() || !ui.getShowStatusBar() && Registry.is("ide.show.progress.without.status.bar");
      }
    };
  }
}
