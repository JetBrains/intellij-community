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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jdesktop.swingx.graphics.GraphicsUtilities;
import org.jdesktop.swingx.graphics.ShadowRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Created by Denis Fokin
 */
public class SheetController {

  private static String fontName = "Lucida Grande";
  private static Font regularFont = new Font(fontName, Font.PLAIN, 10);
  private static Font boldFont = new Font(fontName, Font.BOLD, 12).deriveFont(Font.BOLD);
  private final DialogWrapper.DoNotAskOption myDoNotAskOption;
  private JCheckBox doNotAskCheckBox;
  private boolean myDoNotAskResult;

  private BufferedImage myShadowImage;
  
  private JButton[] buttons;
  private JButton myDefaultButton;
  private JButton myFocusedButton;

  public static int SHADOW_BORDER = 5;

  private static int RIGHT_OFFSET = 20 + SHADOW_BORDER;
  private static int BOTTOM_SHEET_PADDING = 10;

  private final static int TOP_SHEET_PADDING = 20;
  private final static int GAP_BETWEEN_LINES = 10;

  private final static int LEFT_SHEET_PADDING = 35;
  private final static int LEFT_SHEET_OFFSET = 120;

  private static int GAP_BETWEEN_BUTTONS = 5;

  // SHEET
  public int SHEET_WIDTH = 400;

  public int SHEET_HEIGHT = 150;

  // SHEET + shadow
  int SHEET_NC_WIDTH = SHEET_WIDTH + SHADOW_BORDER * 2;
  int SHEET_NC_HEIGHT = SHEET_HEIGHT + SHADOW_BORDER ;


  private Icon myIcon = UIUtil.getInformationIcon();

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
    myDoNotAskResult = (doNotAskOption == null) ? false : !doNotAskOption.isToBeShown();
    mySheetMessage = sheetMessage;
    buttons = new JButton[buttonTitles.length];

    myResult = null;

    for (int i = 0; i < buttons.length; i++) {
      int titleIndex = buttonTitles.length - 1 - i;
      String buttonTitle = buttonTitles[titleIndex];

      buttons[i] = new JButton();
      buttons[i].setOpaque(false);
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

    initShadowImage();
  }

  private void initShadowImage() {

    final BufferedImage mySheetStencil = GraphicsUtilities.createCompatibleTranslucentImage(SHEET_WIDTH, SHEET_HEIGHT);

    Graphics2D g2 = mySheetStencil.createGraphics();
    g2.setColor(new JBColor(Gray._255, Gray._0));
    g2.fillRect(0, 0, SHEET_WIDTH, SHEET_HEIGHT);
    g2.dispose();

    ShadowRenderer renderer = new ShadowRenderer();
    renderer.setSize(SHADOW_BORDER);
    renderer.setOpacity(.75f);
    renderer.setColor(new JBColor(JBColor.BLACK, Gray._10));
    myShadowImage = renderer.createShadow(mySheetStencil);
  }

  private void handleMnemonics(int i, String buttonTitle) {
    buttons[i].setName(buttonTitle);
    buttons[i].setText(buttonTitle);
    setMnemonicsFromChar('&', buttons[i]);
    setMnemonicsFromChar('_', buttons[i]);
  }

  private static void setMnemonicsFromChar(char mnemonicChar, JButton button) {
    String buttonTitle = button.getText();
    if (buttonTitle.indexOf(mnemonicChar) != -1) {
      button.setMnemonic(buttonTitle.charAt(buttonTitle.indexOf(mnemonicChar) + 1));
      button.setText(buttonTitle.replace(Character.toString(mnemonicChar), ""));
    }
  }

  void requestFocus() {
    final JComponent focusedComponent = (myDoNotAskOption == null) ? myFocusedButton : doNotAskCheckBox;
    if (focusedComponent == null) return; // it might be we have only one button. it is a default one in that case
    if (SystemInfo.isAppleJvm) {
      focusedComponent.requestFocus();
    } else {
      focusedComponent.requestFocusInWindow();
    }
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
      protected void paintComponent(Graphics g2d) {
        final Graphics2D g = (Graphics2D) g2d.create();
        super.paintComponent(g);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .85f));

        g.setColor(new JBColor(Gray._225, UIUtil.getPanelBackground()));
        Rectangle2D dialog  = new Rectangle2D.Double(SHADOW_BORDER, 0, SHEET_WIDTH, SHEET_HEIGHT);

        paintShadow(g);
        // draw the sheet background
        if (UIUtil.isUnderDarcula()) {
          g.fillRoundRect((int)dialog.getX(), (int)dialog.getY() - 5, (int)dialog.getWidth(), (int)(5 + dialog.getHeight()), 5, 5);
        } else {
          //todo make bottom corners
          g.fill(dialog);
        }
        paintShadowFromParent(g);
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

    FontMetrics fontMetrics = mySheetMessage.getFontMetrics(regularFont);

    int widestWordWidth = 250;

    String [] words = message.split(" ");

    for (String word : words) {
      widestWordWidth = Math.max(fontMetrics.stringWidth(word), widestWordWidth);
    }

    messageTextPane.setSize(widestWordWidth, Short.MAX_VALUE);
    messageTextPane.setText(message);
    messageArea.setSize(widestWordWidth, messageTextPane.getPreferredSize().height);

    SHEET_WIDTH = Math.max(LEFT_SHEET_OFFSET + widestWordWidth + RIGHT_OFFSET, SHEET_WIDTH);
    messageTextPane.setSize(messageArea);

    messageTextPane.setOpaque(false);
    messageTextPane.setFocusable(false);

    sheetPanel.add(messageTextPane);

    messageTextPane.repaint();



    ico.setOpaque(false);
    ico.setSize(new Dimension(AllIcons.Logo_welcomeScreen.getIconWidth(), AllIcons.Logo_welcomeScreen.getIconHeight()));
    ico.setLocation(LEFT_SHEET_PADDING, TOP_SHEET_PADDING);
    sheetPanel.add(ico);
    headerLabel.setLocation(LEFT_SHEET_OFFSET, TOP_SHEET_PADDING);
    messageTextPane.setLocation(LEFT_SHEET_OFFSET, TOP_SHEET_PADDING + headerLabel.getPreferredSize().height + GAP_BETWEEN_LINES);

    SHEET_HEIGHT = TOP_SHEET_PADDING + headerLabel.getPreferredSize().height + GAP_BETWEEN_LINES + messageArea.height + GAP_BETWEEN_LINES;

    if (myDoNotAskOption != null) {
      layoutDoNotAskCheckbox(sheetPanel);
    }

    layoutWithAbsoluteLayout(buttons, sheetPanel);

    SHEET_HEIGHT += BOTTOM_SHEET_PADDING;

    sheetPanel.setFocusCycleRoot(true);

    recalculateShadow();

    sheetPanel.setSize(SHEET_NC_WIDTH, SHEET_NC_HEIGHT);

    return sheetPanel;
  }

  private void recalculateShadow() {
    SHEET_NC_WIDTH = SHEET_WIDTH + SHADOW_BORDER * 2;
    SHEET_NC_HEIGHT = SHEET_HEIGHT + SHADOW_BORDER;
  }

  private void layoutWithAbsoluteLayout(JButton[] buttons, JPanel sheetPanel) {
    layoutButtons(buttons, sheetPanel);
  }

  private void paintShadow(Graphics2D g2d) {
    g2d.setBackground(new JBColor(new Color(255, 255, 255, 0), new Color(110, 110, 110, 0)));
    g2d.clearRect(0, 0, SHEET_NC_WIDTH, SHEET_HEIGHT);
    g2d.drawImage(myShadowImage, 0, -SHADOW_BORDER, null);
    g2d.clearRect(SHADOW_BORDER, 0, SHEET_WIDTH, SHEET_HEIGHT);
  }

  private void paintShadowFromParent(Graphics2D g2d) {
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .35f));
    g2d.drawImage(myShadowImage, 0, - SHEET_HEIGHT, null);
  }

  private void layoutButtons(final JButton[] buttons, JPanel panel) {

    int widestButtonWidth = 0;

    for (JButton button : buttons) {
      button.repaint();
      widestButtonWidth = Math.max(button.getPreferredSize().width, widestButtonWidth);
    }

    for (JButton button : buttons) {
      button.setSize(widestButtonWidth, button.getPreferredSize().height);
      panel.add(button);
    }

    int buttonsWidth = (widestButtonWidth + GAP_BETWEEN_BUTTONS) * buttons.length + RIGHT_OFFSET;

    SHEET_WIDTH = Math.max(buttonsWidth, SHEET_WIDTH);

    int buttonShift = 0;

    for (JButton button : buttons) {
      Dimension size = button.getSize();
      buttonShift += size.width;
      button.setBounds(SHEET_WIDTH - buttonShift,
                       SHEET_HEIGHT,
                       size.width, size.height);
      buttonShift += GAP_BETWEEN_BUTTONS;
    }

    SHEET_HEIGHT += buttons[0].getHeight();
  }

  private void layoutDoNotAskCheckbox(JPanel sheetPanel) {
    doNotAskCheckBox = new JCheckBox(myDoNotAskOption.getDoNotShowMessage(), !myDoNotAskOption.isToBeShown());
    doNotAskCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myDoNotAskResult = e.getStateChange() == ItemEvent.SELECTED;
      }
    });
    doNotAskCheckBox.repaint();
    doNotAskCheckBox.setSize(doNotAskCheckBox.getPreferredSize());

    doNotAskCheckBox.setLocation(LEFT_SHEET_OFFSET, SHEET_HEIGHT);
    sheetPanel.add(doNotAskCheckBox);

    SHEET_HEIGHT += doNotAskCheckBox.getHeight();
  }

  /**
   * This method is used to show an image during message showing
   * @return image to show
   */
  Image getStaticImage() {
    final JFrame myOffScreenFrame = new JFrame() ;
    myOffScreenFrame.add(mySheetPanel);
    myOffScreenFrame.getRootPane().setDefaultButton(myDefaultButton);

    final BufferedImage image = (SystemInfo.isJavaVersionAtLeast("1.7")) ?
                                UIUtil.createImage(SHEET_NC_WIDTH, SHEET_NC_HEIGHT, BufferedImage.TYPE_INT_ARGB) :
                                GraphicsUtilities.createCompatibleTranslucentImage(SHEET_NC_WIDTH, SHEET_NC_HEIGHT);

    Graphics g = image.createGraphics();
    mySheetPanel.paint(g);

    g.dispose();

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
