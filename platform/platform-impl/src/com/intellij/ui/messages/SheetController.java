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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jdesktop.swingx.graphics.GraphicsUtilities;
import org.jdesktop.swingx.graphics.ShadowRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Created by Denis Fokin
 */
public class SheetController {
  private static final Logger LOG = Logger.getInstance(SheetController.class);
  private static final int SHEET_MINIMUM_HEIGHT = 143;
  private static final String fontName = "Lucida Grande";
  private static final Font regularFont = new Font(fontName, Font.PLAIN, 10);
  private static final Font boldFont = new Font(fontName, Font.BOLD, 12).deriveFont(Font.BOLD);
  private final DialogWrapper.DoNotAskOption myDoNotAskOption;
  private boolean myDoNotAskResult;

  private BufferedImage myShadowImage;
  
  private final JButton[] buttons;
  private JButton myDefaultButton;
  private JComponent myFocusedComponent;

  private JCheckBox doNotAskCheckBox = new JCheckBox();

  public static int SHADOW_BORDER = 5;

  private static final int RIGHT_OFFSET = 10 - SHADOW_BORDER;

  private final static int TOP_SHEET_PADDING = 20;
  private final static int GAP_BETWEEN_LINES = 10;

  private final static int LEFT_SHEET_PADDING = 35;
  private final static int LEFT_SHEET_OFFSET = 120;

  private static final int GAP_BETWEEN_BUTTONS = 5;

  private static final String SPACE_OR_LINE_SEPARATOR_PATTERN = "[\\s" + System.getProperty("line.separator") + "]+";

  // SHEET
  public int SHEET_WIDTH = 400;

  public int SHEET_HEIGHT = 143;

  // SHEET + shadow
  int SHEET_NC_WIDTH = SHEET_WIDTH + SHADOW_BORDER * 2;
  int SHEET_NC_HEIGHT = SHEET_HEIGHT + SHADOW_BORDER ;


  private Icon myIcon = UIUtil.getInformationIcon();

  private String myResult;
  private final JPanel mySheetPanel;
  private final SheetMessage mySheetMessage;

  private final JEditorPane messageTextPane = new JEditorPane();
  private final Dimension messageArea = new Dimension(250, Short.MAX_VALUE);

  SheetController(final SheetMessage sheetMessage,
                  final String title,
                  final String message,
                  final Icon icon,
                  final String[] buttonTitles,
                  final String defaultButtonTitle,
                  final DialogWrapper.DoNotAskOption doNotAskOption,
                  final String focusedButtonTitle) {
    if (icon != null) {
      myIcon = icon;
    }

    myDoNotAskOption = doNotAskOption;
    myDoNotAskResult = (doNotAskOption != null) && !doNotAskOption.isToBeShown();
    mySheetMessage = sheetMessage;
    buttons = new JButton[buttonTitles.length];

    myResult = null;

    int defaultButtonIndex = -1;
    int focusedButtonIndex = -1;

    boolean moveFocusedToTheLeft = defaultButtonTitle != null && defaultButtonTitle.equals(focusedButtonTitle);

    for (int i = 0; i < buttons.length; i++) {
      String buttonTitle = buttonTitles[i];

      buttons[i] = new JButton();
      buttons[i].setOpaque(false);
      handleMnemonics(i, buttonTitle);

      if (buttonTitle.equals(defaultButtonTitle)) {
        defaultButtonIndex = i;
      }

      if (buttonTitle.equals("Cancel")) {
        moveFocusedToTheLeft = true;
      }

      if (buttonTitle.equals(focusedButtonTitle)) {
        focusedButtonIndex = moveFocusedToTheLeft ?  buttons.length - 1 : i;
      }
    }

    defaultButtonIndex = (focusedButtonIndex == defaultButtonIndex) || defaultButtonTitle == null ? 0 : defaultButtonIndex;

    if (focusedButtonIndex != -1 && !moveFocusedToTheLeft) {
      myFocusedComponent = buttons[focusedButtonIndex];
    } else if (doNotAskOption != null) {
      myFocusedComponent = doNotAskCheckBox;
    } else {
      myFocusedComponent = buttons[buttons.length - 1];
    }

    myDefaultButton = (defaultButtonIndex == -1) ? buttons[0] : buttons[defaultButtonIndex];

    if (myResult == null) {
      myResult = Messages.CANCEL_BUTTON;
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
    renderer.setOpacity(.80f);
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
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(new Runnable() {
      @Override
      public void run() {
        myFocusedComponent.requestFocus();
      }
    });
  }

  JPanel getPanel(final JDialog w) {
    w.getRootPane().setDefaultButton(myDefaultButton);


    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
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

  private static float getSheetAlpha() {
    return .95f;
  }

  private JPanel createSheetPanel(String title, String message, JButton[] buttons) {
    JPanel sheetPanel = new JPanel() {
      @Override
      protected void paintComponent(@NotNull Graphics g2d) {
        final Graphics2D g = (Graphics2D) g2d.create();
        super.paintComponent(g);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, getSheetAlpha()));

        g.setColor(new JBColor(Gray._230, UIUtil.getPanelBackground()));
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
      protected void paintComponent(@NotNull Graphics g) {
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

    messageTextPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    messageTextPane.setFont(regularFont);
    messageTextPane.setEditable(false);

    messageTextPane.setContentType("text/html");

    messageTextPane.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent he) {
        if(he.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          if(Desktop.isDesktopSupported()) {
            try {
              URL url = he.getURL();
              if (url != null) {
                Desktop.getDesktop().browse(url.toURI());
              } else {
                LOG.warn("URL is null; HyperlinkEvent: " + he.toString());
              }
            }
            catch (IOException e) {
              LOG.error(e);
            }
            catch (URISyntaxException e) {
              LOG.error(e);
            }
          }
        }
      }
    });

    FontMetrics fontMetrics = sheetPanel.getFontMetrics(regularFont);

    int widestWordWidth = 250;

    String [] words = (message == null) ? ArrayUtil.EMPTY_STRING_ARRAY : message.split(SPACE_OR_LINE_SEPARATOR_PATTERN);

    for (String word : words) {
      widestWordWidth = Math.max(fontMetrics.stringWidth(word), widestWordWidth);
    }

    messageTextPane.setSize(widestWordWidth, Short.MAX_VALUE);
    messageTextPane.setText(handleBreaks(message));
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

    int BOTTOM_SHEET_PADDING = 10;
    SHEET_HEIGHT += BOTTOM_SHEET_PADDING;

    if (SHEET_HEIGHT < SHEET_MINIMUM_HEIGHT) {
      SHEET_HEIGHT = SHEET_MINIMUM_HEIGHT;
      shiftButtonsToTheBottom(SHEET_MINIMUM_HEIGHT - SHEET_HEIGHT);
    }

    sheetPanel.setFocusCycleRoot(true);

    recalculateShadow();

    sheetPanel.setSize(SHEET_NC_WIDTH, SHEET_NC_HEIGHT);

    return sheetPanel;
  }

  private static String handleBreaks(final String message) {
    return message.replaceAll("(\r\n|\n)", "<br/>");
  }

  private void shiftButtonsToTheBottom(int shiftDistance) {
    for (JButton b : buttons) {
      b.setLocation(b.getX(), b.getY() + shiftDistance);
    }
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


  private static float getShadowAlpha() {
    return ((UIUtil.isUnderDarcula())) ? .85f : .35f;
  }

  private void paintShadowFromParent(Graphics2D g2d) {
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, getShadowAlpha()));
    g2d.drawImage(myShadowImage, 0, - SHEET_HEIGHT - SHADOW_BORDER, null);
  }

  private void layoutButtons(final JButton[] buttons, JPanel panel) {

    //int widestButtonWidth = 0;
    int buttonWidth = 0;
    SHEET_HEIGHT += GAP_BETWEEN_LINES;

    for (int i = 0; i < buttons.length; i ++) {
      buttons[i].repaint();
      buttons[i].setSize(buttons[i].getPreferredSize());
      buttonWidth += buttons[i].getWidth();
      if (i == buttons.length - 1) break;
      buttonWidth += GAP_BETWEEN_BUTTONS;
    }

    int buttonsRowWidth = LEFT_SHEET_OFFSET + buttonWidth + RIGHT_OFFSET;

    // update the pane if the sheet is going to be wider
    messageTextPane.setSize(Math.max(messageTextPane.getWidth(), buttonWidth), messageTextPane.getHeight());

    SHEET_WIDTH = Math.max(buttonsRowWidth, SHEET_WIDTH);

    int buttonShift = RIGHT_OFFSET;

    for (JButton button : buttons) {
      Dimension size = button.getSize();
      buttonShift += size.width;
      button.setBounds(SHEET_WIDTH - buttonShift,
                       SHEET_HEIGHT,
                       size.width, size.height);
      panel.add(button);
      buttonShift += GAP_BETWEEN_BUTTONS;
    }

    SHEET_HEIGHT += buttons[0].getHeight();
  }

  private void layoutDoNotAskCheckbox(JPanel sheetPanel) {
    doNotAskCheckBox.setText(myDoNotAskOption.getDoNotShowMessage());
    doNotAskCheckBox.setSelected(!myDoNotAskOption.isToBeShown());

    doNotAskCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(@NotNull ItemEvent e) {
        myDoNotAskResult = (e.getStateChange() == ItemEvent.SELECTED);
      }
    });
    doNotAskCheckBox.repaint();
    doNotAskCheckBox.setSize(doNotAskCheckBox.getPreferredSize());

    doNotAskCheckBox.setLocation(LEFT_SHEET_OFFSET, SHEET_HEIGHT);
    sheetPanel.add(doNotAskCheckBox);

    if (myFocusedComponent == null) {
      myFocusedComponent = doNotAskCheckBox;
    }

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
