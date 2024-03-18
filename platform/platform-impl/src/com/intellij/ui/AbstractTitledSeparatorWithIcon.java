// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.changes.RefreshablePanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @deprecated Use Kotlin UI DSL and {@link com.intellij.ui.dsl.builder.Panel#collapsibleGroup} instead
 */
@Deprecated(forRemoval = true)
@ApiStatus.Internal
public abstract class AbstractTitledSeparatorWithIcon extends JPanel {
  protected RefreshablePanel myDetailsComponent;
  protected final JLabel myLabel;
  protected final JPanel myWrapper;
  protected boolean myOn;
  protected final Icon myIcon;
  protected final Icon myIconOpen;
  protected final JSeparator mySeparator;

  public AbstractTitledSeparatorWithIcon(final @NotNull Icon icon,
                                         final @NotNull Icon iconOpen,
                                         final @NlsContexts.Separator @NotNull String text) {
    UIUtil.applyDeprecatedBackground(this);
    myIcon = icon;
    myIconOpen = iconOpen;
    setLayout(new GridBagLayout());
    myLabel = new JLabel(icon);
    add(myLabel, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 8), 0, 0));
    Color oldColor = UIManager.getColor("Separator.separatorColor");
    UIManager.put("Separator.separatorColor", JBColor.border());
    mySeparator = new JSeparator(SwingConstants.HORIZONTAL);
    UIManager.put("Separator.separatorColor", oldColor);
    GridBagConstraints gb =
            new GridBagConstraints(1, 0, GridBagConstraints.REMAINDER, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                                   new Insets(3, 0, 0, 0), 0, 0);
    add(mySeparator, gb);
    setBorder(JBUI.Borders.empty(3, 0, 5, 5));
    myLabel.setFont(UIUtil.getTitledBorderFont());
    myLabel.setForeground(UIUtil.getLabelForeground());
    myLabel.setText(UIUtil.replaceMnemonicAmpersand(text));

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

  public void setText(@NlsContexts.Separator String text) {
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
