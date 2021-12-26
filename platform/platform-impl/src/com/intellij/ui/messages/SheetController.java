// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.messages;

import com.intellij.BundleBase;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.mac.touchbar.Touchbar;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jdesktop.swingx.graphics.GraphicsUtilities;
import org.jdesktop.swingx.graphics.ShadowRenderer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

public final class SheetController implements Disposable {
  private static final KeyStroke VK_ESC_KEYSTROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);

  private static final Logger LOG = Logger.getInstance(SheetController.class);
  private static final int SHEET_MINIMUM_HEIGHT = 143;
  private final DoNotAskOption myDoNotAskOption;
  private final @Nullable JButton myHelpButton;
  private boolean myDoNotAskResult;

  private BufferedImage myShadowImage;

  private final JButton[] buttons;
  private final JButton myDefaultButton;
  private JComponent myFocusedComponent;

  private final JCheckBox doNotAskCheckBox = new JCheckBox();

  public static final int SHADOW_BORDER = 5;

  private static final int RIGHT_OFFSET = 10 - SHADOW_BORDER;

  private final static int TOP_SHEET_PADDING = 20;
  private final static int GAP_BETWEEN_LINES = 10;

  private final static int LEFT_SHEET_PADDING = 20;
  private final static int LEFT_SHEET_OFFSET = 120;

  private static final int GAP_BETWEEN_BUTTONS = 5;

  @NonNls private static final String SPACE_OR_LINE_SEPARATOR_PATTERN = "([\\s" + System.lineSeparator() + "]|(<br\\s*/?>))+";

  // SHEET
  public int SHEET_WIDTH = 400;

  public int SHEET_HEIGHT = 143;

  // SHEET + shadow
  int SHEET_NC_WIDTH = SHEET_WIDTH + SHADOW_BORDER * 2;
  int SHEET_NC_HEIGHT = SHEET_HEIGHT + SHADOW_BORDER ;


  private Icon myIcon = UIUtil.getInformationIcon();

  private String myResult;
  private final JPanel mySheetPanel;
  private SheetMessage mySheetMessage;

  private final JEditorPane messageTextPane = new JEditorPane();
  private final Dimension messageArea = new Dimension(250, Short.MAX_VALUE);

  private final JEditorPane headerLabel = new JEditorPane();

  SheetController(final SheetMessage sheetMessage,
                  @Nls final String title,
                  @Nls final String message,
                  final Icon icon,
                  final String[] buttonTitles,
                  final String defaultButtonTitle,
                  final DoNotAskOption doNotAskOption,
                  final String focusedButtonTitle,
                  @Nullable String helpId) {
    if (icon != null) {
      myIcon = icon;
    }
    myHelpButton = helpId != null
                   ? DialogWrapper.createHelpButton(new DialogWrapper.HelpAction(() -> HelpManager.getInstance().invokeHelp(helpId)))
                   : null;

    myDoNotAskOption = doNotAskOption;
    myDoNotAskResult = (doNotAskOption != null) && !doNotAskOption.isToBeShown();
    mySheetMessage = sheetMessage;
    buttons = new JButton[buttonTitles.length];

    myResult = null;

    int defaultButtonIndex = -1;
    int focusedButtonIndex = -1;

    for (int i = 0; i < buttons.length; i++) {
      String buttonTitle = buttonTitles[i];

      buttons[i] = new JButton();
      buttons[i].setOpaque(false);
      handleMnemonics(i, buttonTitle);

      if (buttonTitle.equals(defaultButtonTitle)) {
        defaultButtonIndex = i;
      }

      if (buttonTitle.equals(focusedButtonTitle) && !focusedButtonTitle.equals("Cancel")) {
        focusedButtonIndex = i;
      }
    }

    defaultButtonIndex = (focusedButtonIndex == defaultButtonIndex) || defaultButtonTitle == null ? 0 : defaultButtonIndex;

    if (focusedButtonIndex != -1 && defaultButtonIndex != focusedButtonIndex) {
      myFocusedComponent = buttons[focusedButtonIndex];
    } else if (doNotAskOption != null) {
      myFocusedComponent = doNotAskCheckBox;
    } else if (buttons.length > 1) {
      myFocusedComponent = buttons[buttons.length - 1];
    }

    myDefaultButton = (defaultButtonIndex == -1) ? buttons[0] : buttons[defaultButtonIndex];

    if (myResult == null) {
      myResult = Messages.getCancelButton();
    }

    mySheetPanel = createSheetPanel(title, message, buttons);
    if (SystemInfo.isMac) {
      List<JButton> touchbarButtons = Arrays.asList(buttons);
      Collections.reverse(touchbarButtons);
      Touchbar.setButtonActions(mySheetPanel, null, touchbarButtons, myDefaultButton);
    }
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

  private void handleMnemonics(int i, @NlsContexts.Button String title) {
    buttons[i].setName(title);

    if (!setButtonTextAndMnemonic(i, title, '_') &&
        !setButtonTextAndMnemonic(i, title, '&') &&
        !setButtonTextAndMnemonic(i, title, BundleBase.MNEMONIC)) {
      buttons[i].setText(title);
    }
  }

  private boolean setButtonTextAndMnemonic(int i, @NlsContexts.Button String title, char mnemonics) {
    int mIdx;
    if ((mIdx = title.indexOf(mnemonics)) >= 0) {
      String text = title.substring(0, mIdx) + title.substring(mIdx + 1);

      buttons[i].setText(text);
      buttons[i].setMnemonic(text.charAt(mIdx));
      return true;
    }
    else {
      return false;
    }
  }

  void requestFocus() {
    getGlobalInstance().doWhenFocusSettlesDown(() -> {
      if (myFocusedComponent != null) {
        getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(myFocusedComponent, true));
      } else {
        LOG.debug("My focused component is null for the next message: " + messageTextPane.getText());
      }
    });
  }

  void setDefaultResult () {
     myResult = myDefaultButton.getName();
  }

  void setFocusedResult () {
    if (myFocusedComponent instanceof JButton) {
      JButton focusedButton = (JButton)myFocusedComponent;
      myResult = focusedButton.getName();
    }
  }

  private void setResultAndStartClose(String result) {
    if (result != null)
      myResult = result;
    mySheetMessage.startAnimation(false);
  }

  JPanel getPanel(@NotNull RootPaneContainer container) {
    container.getRootPane().setDefaultButton(myDefaultButton);

    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        final String res = e.getSource() instanceof JButton ? ((JButton)e.getSource()).getName() : null;
        setResultAndStartClose(res);
      }
    };

    mySheetPanel.registerKeyboardAction(actionListener,
                                        VK_ESC_KEYSTROKE,
                                        JComponent.WHEN_IN_FOCUSED_WINDOW);

    for (JButton button: buttons) {
      button.addActionListener(actionListener);
    }
    return mySheetPanel;
  }

  private static float getSheetAlpha() {
    return .95f;
  }

  private JPanel createSheetPanel(@Nls String title, @Nls String message, JButton[] buttons) {
    JPanel sheetPanel;

    if (SheetMessage.useTransparent()) {
      sheetPanel = new JPanel() {
        @Override
        protected void paintComponent(@NotNull Graphics g2d) {
          super.paintComponent(g2d);
          Graphics2D g = (Graphics2D)g2d.create();
          g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, getSheetAlpha()));

          g.setColor(new JBColor(Gray._230, UIUtil.getPanelBackground()));
          Rectangle dialog = new Rectangle(SHADOW_BORDER, 0, SHEET_WIDTH, SHEET_HEIGHT);

          paintShadow(g);
          // draw the sheet background
          if (StartupUiUtil.isUnderDarcula()) {
            g.fillRoundRect((int)dialog.getX(), (int)dialog.getY() - 5, (int)dialog.getWidth(), (int)(5 + dialog.getHeight()), 5, 5);
          }
          else {
            //todo make bottom corners
            g.fill(dialog);
          }

          Border border = UIManager.getBorder("Window.border");
          if (border != null) {
            border.paintBorder(this, g, dialog.x, dialog.y, dialog.width, dialog.height);
          }

          paintShadowFromParent(g);
          g.dispose();
        }
      };
      sheetPanel.setOpaque(false);
      initShadowImage();
    }
    else {
      sheetPanel = new JPanel();
    }

    sheetPanel.setLayout(null);

    JPanel ico = new JPanel() {
      @Override
      protected void paintComponent(@NotNull Graphics g) {
        super.paintComponent(g);
        myIcon.paintIcon(this, g, 0, 0);
      }
    };

    headerLabel.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    headerLabel.setFont(StartupUiUtil.getLabelFont().deriveFont(Font.BOLD));
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
    Font font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL);
    messageTextPane.setFont(font);
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
            catch (IOException | URISyntaxException e) {
              LOG.error(e);
            }
          }
        }
      }
    });

    FontMetrics fontMetrics = sheetPanel.getFontMetrics(font);

    int widestWordWidth = 250;

    String [] words = (message == null) ? ArrayUtilRt.EMPTY_STRING_ARRAY : message.split(SPACE_OR_LINE_SEPARATOR_PATTERN);

    for (String word : words) {
      widestWordWidth = Math.max(fontMetrics.stringWidth(word), widestWordWidth);
    }

    messageTextPane.setSize(widestWordWidth, Short.MAX_VALUE);
    messageTextPane.setText(handleBreaks(message));
    widestWordWidth = Math.max(widestWordWidth, messageTextPane.getMinimumSize().width);
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
    return message == null ? "" : message.replaceAll("(\r\n|\n)", "<br/>");
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
    return ((StartupUiUtil.isUnderDarcula())) ? .85f : .35f;
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
    headerLabel.setSize(Math.max(headerLabel.getWidth(), buttonWidth), headerLabel.getHeight());
    messageTextPane.setSize(Math.max(messageTextPane.getWidth(), buttonWidth), messageTextPane.getHeight());

    SHEET_WIDTH = Math.max(buttonsRowWidth, SHEET_WIDTH);

    int buttonShift = RIGHT_OFFSET;

    if (myHelpButton != null) {
      myHelpButton.repaint();
      Dimension size = myHelpButton.getPreferredSize();
      myHelpButton.setSize(size);

      myHelpButton.setBounds(LEFT_SHEET_PADDING, SHEET_HEIGHT, size.width, size.height);
      panel.add(myHelpButton);
    }

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
    doNotAskCheckBox.setVisible(myDoNotAskOption.canBeHidden());
    doNotAskCheckBox.setSelected(!myDoNotAskOption.isToBeShown());
    doNotAskCheckBox.setOpaque(false);
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

    BufferedImage image = ImageUtil.createImage(SHEET_NC_WIDTH, SHEET_NC_HEIGHT,
                                                myShadowImage == null ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);

    Graphics g = image.createGraphics();
    mySheetPanel.paint(g);

    g.dispose();

    myOffScreenFrame.remove(mySheetPanel);

    myOffScreenFrame.dispose();
    return image;
  }

  JPanel getSheetPanel() { return mySheetPanel; }

  public boolean getDoNotAskResult () {
    return myDoNotAskResult;
  }

  public String getResult() {
    return myResult;
  }

  @Override
  public void dispose() {
    mySheetPanel.unregisterKeyboardAction(VK_ESC_KEYSTROKE);
    mySheetMessage = null;
  }
}
