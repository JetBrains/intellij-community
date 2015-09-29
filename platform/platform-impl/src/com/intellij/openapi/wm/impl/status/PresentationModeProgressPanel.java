/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.TransparentPanel;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Konstantin Bulenkov
 */
public class PresentationModeProgressPanel {
  private final InlineProgressIndicator myProgress;
  private JLabel myText;
  private JProgressBar myProgressBar;
  private InplaceButton myCancelButton;
  private JLabel myText2;
  private JPanel myRootPanel;
  private MergingUpdateQueue myUpdateQueue;
  private Update myUpdate;

  public PresentationModeProgressPanel(InlineProgressIndicator progress) {
    myProgress = progress;
    Font font = JBUI.Fonts.label(11);
    myText.setFont(font);
    myText2.setFont(font);
    myText.setIcon(EmptyIcon.create(1, 16));
    myText2.setIcon(EmptyIcon.create(1, 16));
    myUpdateQueue = new MergingUpdateQueue("Presentation Mode Progress", 100, true, null);
    myUpdate = new Update("Update UI") {
      @Override
      public void run() {
        updateImpl();
      }
    };
  }

  public void update() {
    myUpdateQueue.queue(myUpdate);
  }

  @NotNull
  private static Color getTextForeground() {
    return EditorColorsManager.getInstance().getGlobalScheme().getDefaultForeground();
  }

  private void updateImpl() {
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
        return ui.PRESENTATION_MODE || !ui.SHOW_STATUS_BAR && Registry.is("ide.show.progress.without.status.bar");
      }
    };
    IconButton iconButton = new IconButton(myProgress.getInfo().getCancelTooltipText(),
                                                 AllIcons.Process.Stop,
                                                 AllIcons.Process.StopHovered);
    myCancelButton = new InplaceButton(iconButton, new ActionListener() {
      public void actionPerformed(@NotNull ActionEvent e) {
        myProgress.cancelRequest();
      }
    }).setFillBg(false);
  }
}
