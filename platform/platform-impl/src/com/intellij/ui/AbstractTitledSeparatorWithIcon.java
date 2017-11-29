/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.vcs.changes.RefreshablePanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public abstract class AbstractTitledSeparatorWithIcon extends JPanel {
  protected RefreshablePanel myDetailsComponent;
  protected final JLabel myLabel;
  private String originalText;
  protected final JPanel myWrapper;
  protected boolean myOn;
  protected final Icon myIcon;
  protected final Icon myIconOpen;
  protected final JSeparator mySeparator;

  public AbstractTitledSeparatorWithIcon(@NotNull final Icon icon,
                                         @NotNull final Icon iconOpen,
                                         @NotNull final String text) {
    myIcon = icon;
    myIconOpen = iconOpen;
    setLayout(new GridBagLayout());
    myLabel = new JLabel(icon);
    add(myLabel, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 8), 0, 0));
    Color oldColor = UIManager.getColor("Separator.foreground");
    UIManager.put("Separator.foreground", UIUtil.getBorderColor());
    mySeparator = new JSeparator(SwingConstants.HORIZONTAL);
    UIManager.put("Separator.foreground", oldColor);
    GridBagConstraints gb =
            new GridBagConstraints(1, 0, GridBagConstraints.REMAINDER, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                                   new Insets(3, 0, 0, 0), 0, 0);
    add(mySeparator, gb);
    setBorder(JBUI.Borders.empty(3, 0, 5, 5));
    myLabel.setFont(UIUtil.getTitledBorderFont());
    myLabel.setForeground(UIUtil.getLabelForeground());
    originalText = text;
    myLabel.setText(UIUtil.replaceMnemonicAmpersand(originalText));

    ++ gb.gridy;
    gb.gridx = 0;
    gb.fill = GridBagConstraints.BOTH;
    gb.anchor = GridBagConstraints.NORTHWEST;
    gb.weighty = 1;
    myWrapper = new JPanel(new BorderLayout());
    add(myWrapper, gb);

    myLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        if (myOn) {
          off();
        }
        else {
          on();
        }
      }
      @Override
      public void mouseEntered(MouseEvent e) {
        setCursor(new Cursor(Cursor.HAND_CURSOR));
      }

      @Override
      public void mouseExited(MouseEvent e) {
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
      }
    });
  }
  
  public void setText(final String text) {
    myLabel.setText(UIUtil.replaceMnemonicAmpersand(text));
  }

  protected abstract RefreshablePanel createPanel();
  
  protected void initDetails() {
    if (myDetailsComponent != null) {
      myDetailsComponent.refresh();
      return;
    }
    myDetailsComponent = createPanel();
  }
  
  public void on() {
    initDetails();
    myOn = true;
    myLabel.setIcon(myIconOpen);
    onImpl();
  }

  public void initOn() {
    initDetails();
    myOn = true;
    myLabel.setIcon(myIconOpen);
    initOnImpl();
  }

  protected abstract void initOnImpl();

  public void off() {
    myOn = false;
    myLabel.setIcon(myIcon);
    offImpl();
  }

  protected abstract void onImpl();
  protected abstract void offImpl();
}
