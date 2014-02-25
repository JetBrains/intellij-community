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
package com.intellij.ui.messages;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Created by Denis Fokin
 */
public class SheetController {

  private static String fontName = "Lucida Grande";
  private static Font regularFont = new Font(fontName, Font.PLAIN, 12);
  private static Font boldFont = new Font(fontName, Font.BOLD, 14).deriveFont(Font.BOLD);
  private final DialogWrapper.DoNotAskOption myDoNotAskOption;
  private JCheckBox doNotAskCheckBox;
  private boolean myDoNotAskResult;


  private JButton[] buttons;
  private JButton myDefaultButton;
  private JButton myFocusedButton;
  public int SHEET_WIDTH = 400;
  public int SHEET_HEIGHT = 150;

  private Icon myIcon = AllIcons.Logo_welcomeScreen;

  private String myResult;
  private JPanel mySheetPanel;
  private SheetMessage mySheetMessage;

  private Dimension messageArea = new Dimension(250, Short.MAX_VALUE);

  SheetController(final SheetMessage sheetMessage,
                  final String title,
                  final String message,
                  final Icon icon,
                  final String[] buttonTitles,
                  final String defaultButtonTitle,
                  final DialogWrapper.DoNotAskOption doNotAskOption,
                  final String focusedButton) {
    if (icon != null) {
      myIcon = icon;
    }
    myDoNotAskOption = doNotAskOption;
    mySheetMessage = sheetMessage;
    buttons = new JButton[buttonTitles.length];

    myResult = null;

    for (int i = 0; i < buttons.length; i++) {
      int titleIndex = buttonTitles.length - 1 - i;
      String buttonTitle = buttonTitles[titleIndex];

      buttons[i] = new JButton();

      handleMnemonics(i, buttonTitle);

      if (buttonTitle.equals(defaultButtonTitle)) {
        myDefaultButton = buttons[i];
      }
      if (buttonTitle.equals(focusedButton)) {
        myFocusedButton = buttons[i];
      }
      if (buttonTitle.equals("Cancel")) {
        myResult = "Cancel";
      }
    }

    if (myResult == null) {
      myResult = buttonTitles[0];
    }

    mySheetPanel = createSheetPanel(title, message, buttons);
  }

  private void handleMnemonics(int i, String buttonTitle) {
    buttons[i].setName(buttonTitle);

    if (buttonTitle.indexOf('&') != -1) {
      buttons[i].setMnemonic(buttonTitle.charAt(buttonTitle.indexOf('&') + 1));
      buttonTitle = buttonTitle.replace("&","");
    }

    buttons[i].setText(buttonTitle);
  }

  void requestFocus() {
    final JComponent focusedComponent = (myDoNotAskOption == null) ? myFocusedButton : doNotAskCheckBox;
    if (focusedComponent == null) return; // it might be we have only one button. it is a default one in that case
    focusedComponent.requestFocusInWindow();
  }

  JPanel getPanel(final JDialog w) {
    w.getRootPane().setDefaultButton(myDefaultButton);


    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof JButton) {
          myResult = ((JButton)e.getSource()).getName();
        }
        mySheetMessage.startAnimation(false);
      }
    };

    mySheetPanel.registerKeyboardAction(actionListener,
                                        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                        JComponent.WHEN_IN_FOCUSED_WINDOW);

    for (JButton button: buttons) {
      button.addActionListener(actionListener);
    }
    return mySheetPanel;
  }


  private JPanel createSheetPanel(String title, String message, JButton[] buttons) {
    JPanel sheetPanel = new JPanel() {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        final Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f));

        g2d.setColor(Gray._225);
        Rectangle2D dialog  = new Rectangle2D.Double(0,0,mySheetPanel.getBounds().width - 5, mySheetPanel.getBounds().height - 10);
        g2d.fill(dialog);
        paintShadow(g2d, dialog);
      }

    };

    sheetPanel.setOpaque(false);
    sheetPanel.setLayout(null);

    JPanel ico = new JPanel() {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        myIcon.paintIcon(this, g, 0, 0);
      }
    };


    JEditorPane headerLabel = new JEditorPane();



    headerLabel.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    headerLabel.setFont(boldFont);
    headerLabel.setEditable(false);

    headerLabel.setContentType("text/html");
    headerLabel.setSize(250, Short.MAX_VALUE);
    headerLabel.setText(title);
    headerLabel.setSize(250, headerLabel.getPreferredSize().height);

    headerLabel.setOpaque(false);
    headerLabel.setFocusable(false);

    sheetPanel.add(headerLabel);

    headerLabel.repaint();

    JEditorPane messageTextPane = new JEditorPane();

    messageTextPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    messageTextPane.setFont(regularFont);
    messageTextPane.setEditable(false);

    messageTextPane.setContentType("text/html");
    messageTextPane.setSize(250, Short.MAX_VALUE);
    messageTextPane.setText(message);
    messageArea.setSize(250, messageTextPane.getPreferredSize().height);
    messageTextPane.setSize(messageArea);

    messageTextPane.setOpaque(false);
    messageTextPane.setFocusable(false);

    sheetPanel.add(messageTextPane);

    messageTextPane.repaint();


    SHEET_HEIGHT = 20 + headerLabel.getPreferredSize().height + 10 + messageArea.height + 10 + 70;
    sheetPanel.setSize(SHEET_WIDTH, SHEET_HEIGHT);

    ico.setOpaque(false);
    ico.setSize(new Dimension(AllIcons.Logo_welcomeScreen.getIconWidth(), AllIcons.Logo_welcomeScreen.getIconHeight()));
    ico.setLocation(20, 20);
    sheetPanel.add(ico);
    headerLabel.setLocation(120, 20);
    messageTextPane.setLocation(120, 20 + headerLabel.getPreferredSize().height + 10);
    layoutWithAbsoluteLayout(buttons, sheetPanel);

    sheetPanel.setFocusCycleRoot(true);

    return sheetPanel;
  }

  private void layoutWithAbsoluteLayout(JButton[] buttons, JPanel sheetPanel) {
    layoutButtons(buttons, sheetPanel);

    if (myDoNotAskOption != null) {
      layoutDoNotAskCheckbox();
      sheetPanel.add(doNotAskCheckBox);
    }
  }

  private void paintShadow(Graphics2D g2d, Rectangle2D dialog) {
    Area shadow = new Area(new RoundRectangle2D.Double(0, 0, mySheetPanel.getBounds().width, mySheetPanel.getBounds().height, 10, 10));

    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.80f));

    Color color1 = Gray._130;
    Color color2 = new JBColor(new Color(130, 130, 130, 0), new Color(130, 130, 130, 0));

    GradientPaint gp = new GradientPaint(
      0, mySheetPanel.getBounds().height - 10, color1,
      0, mySheetPanel.getBounds().height, color2 );

    g2d.setPaint(gp);
    shadow.subtract(new Area(dialog));
    g2d.fill(shadow);
  }

  private void layoutButtons(final JButton[] buttons, JPanel panel) {

    int buttonsWidth = 120;

    for (JButton button : buttons) {
      panel.add(button);
      button.repaint();
      buttonsWidth = button.getWidth() + 10;
    }

    SHEET_WIDTH = Math.max(buttonsWidth, SHEET_WIDTH);

    int buttonShift = 15;

    for (JButton button : buttons) {
      Dimension size = button.getPreferredSize();
      buttonShift += size.width;
      button.setBounds(SHEET_WIDTH - buttonShift,
                       SHEET_HEIGHT - 45,
                       size.width, size.height);
      buttonShift += 10;
    }
  }

  private void layoutDoNotAskCheckbox() {
    doNotAskCheckBox = new JCheckBox(myDoNotAskOption.getDoNotShowMessage());
    doNotAskCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myDoNotAskResult = e.getStateChange() == ItemEvent.SELECTED;
      }
    });
    doNotAskCheckBox.repaint();
    doNotAskCheckBox.setSize(doNotAskCheckBox.getPreferredSize());
    doNotAskCheckBox.setLocation(120, SHEET_HEIGHT - 70);
  }

  /**
   * This method is used to show an image during message showing
   * @return image to show
   */
  Image getStaticImage() {
    final JFrame myOffScreenFrame = new JFrame() ;
    myOffScreenFrame.add(mySheetPanel);
    myOffScreenFrame.getRootPane().setDefaultButton(myDefaultButton);

    final BufferedImage image = UIUtil.createImage(SHEET_WIDTH, SHEET_HEIGHT, BufferedImage.TYPE_INT_ARGB);

    mySheetPanel.paint(image.createGraphics());
    myOffScreenFrame.dispose();
    return image;
  }

  public boolean getDoNotAskResult () {
    return myDoNotAskResult;
  }

  public String getResult() {
    return myResult;
  }
}
