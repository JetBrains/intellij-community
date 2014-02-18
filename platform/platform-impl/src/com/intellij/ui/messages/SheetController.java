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

import javax.swing.*;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
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


  private JLabel myHeaderLabel = new JLabel();
  private MultilineLabel myMessageLabel;
  private JButton[] buttons;
  private JButton myDefaultButton;
  private JButton myFocusedButton;
  public static int SHEET_WIDTH = 400;
  public int SHEET_HEIGHT = 150;

  private String myResult;
  private JPanel mySheetPanel;
  private SheetMessage mySheetMessage;
  private JTextArea myTextArea;

  SheetController(final SheetMessage sheetMessage,
                  final String title,
                  final String message,
                  final Icon icon,
                  final String[] buttonTitles,
                  final String defaultButtonTitle,
                  final DialogWrapper.DoNotAskOption doNotAskOption,
                  final String focusedButton) {
    myDoNotAskOption = doNotAskOption;
    mySheetMessage = sheetMessage;
    buttons = new JButton[buttonTitles.length];
    for (int i = 0; i < buttonTitles.length; i++) {
      buttons[i] = new JButton(buttonTitles[i]);
      if (buttonTitles[i].equals(defaultButtonTitle)) {
        myDefaultButton = buttons[i];
      }
      if (buttonTitles[i].equals(focusedButton)) {
        myFocusedButton = buttons[i];
      }
    }
    mySheetPanel = createSheetPanel(title, message, buttons);


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
          myResult = ((JButton)e.getSource()).getText();
        }
        mySheetMessage.startAnimation();

      }
    };

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
        AllIcons.Logo_welcomeScreen.paintIcon(this, g, 0, 0);
      }
    };


    myHeaderLabel = new JLabel(title);

    myHeaderLabel.setFont(boldFont);

    myHeaderLabel.repaint();
    myHeaderLabel.setSize(myHeaderLabel.getPreferredSize());

    sheetPanel.add(myHeaderLabel);

    myTextArea = new JTextArea(message);

    myTextArea.setFont(regularFont);
    myTextArea.setLineWrap(true);
    myTextArea.setWrapStyleWord(true);

    myTextArea.setSize(250, 10);
    myTextArea.setOpaque(false);



    sheetPanel.add(myTextArea);

    myTextArea.repaint();
    myTextArea.setSize(myTextArea.getPreferredSize());

    SHEET_HEIGHT = myTextArea.getPreferredSize().height +  myHeaderLabel.getPreferredSize().height + 20 + 10 + 100;

    sheetPanel.setSize(SHEET_WIDTH, SHEET_HEIGHT);

    ico.setOpaque(false);
    ico.setSize(new Dimension(AllIcons.Logo_welcomeScreen.getIconWidth(), AllIcons.Logo_welcomeScreen.getIconHeight()));
    ico.setLocation(20, 20);
    sheetPanel.add(ico);
    myHeaderLabel.setLocation(120, 20);
    myTextArea.setLocation(120, 20 + myHeaderLabel.getPreferredSize().height + 10);
    layoutWithAbsoluteLayout(title, message, buttons, sheetPanel);

    return sheetPanel;
  }

  private void layoutWithAbsoluteLayout(String title, String message, JButton[] buttons, JPanel sheetPanel) {
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
    Color color2 = new Color(130, 130, 130, 0);

    GradientPaint gp = new GradientPaint(
      0, mySheetPanel.getBounds().height - 10, color1,
      0, mySheetPanel.getBounds().height, color2 );

    g2d.setPaint(gp);
    shadow.subtract(new Area(dialog));
    g2d.fill(shadow);
  }

  private void layoutButtons(final JButton[] buttons, JPanel panel) {
    for (int i = 0; i < buttons.length ; i ++) {
      panel.add(buttons[i]);
      buttons[i].repaint();

      Dimension size = buttons[i].getPreferredSize();
      buttons[i].setBounds(SHEET_WIDTH - (size.width + 10) * (buttons.length - i) -  15, SHEET_HEIGHT - 45,
                           size.width, size.height);
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
  private Image getStaticImage() {
    final JFrame myOffScreenFrame = new JFrame() ;
    myOffScreenFrame.add(mySheetPanel);
    myOffScreenFrame.getRootPane().setDefaultButton(myDefaultButton);

    final BufferedImage image = new BufferedImage(SHEET_WIDTH, SHEET_HEIGHT, BufferedImage.TYPE_INT_ARGB);

    mySheetPanel.paint(image.createGraphics());
    myOffScreenFrame.dispose();
    return image;
  }

  public JPanel getStaticPanel() {
    final Image staticImage = getStaticImage();
    JPanel jPanel = new JPanel() {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        int zeroOffset = getHeight() - SHEET_HEIGHT;
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f));
        g2d.drawImage(staticImage, 0 , zeroOffset, null);
      }
    };
    jPanel.setOpaque(false);
    return jPanel;
  }

  public boolean getDoNotAskResult () {
    return myDoNotAskResult;
  }

  public String getResult() {
    return myResult;
  }
}

class MultilineLabel extends JTextPane {
  private static final long serialVersionUID = 1L;
  public MultilineLabel(){
    super();
    setEditable(false);
    setCursor(null);
    setOpaque(false);
    setFocusable(false);

    setPreferredSize(new Dimension(200,50));

    StyledDocument doc = getStyledDocument();
    SimpleAttributeSet center = new SimpleAttributeSet();
    StyleConstants.setAlignment(center, StyleConstants.ALIGN_LEFT);
    doc.setParagraphAttributes(0, doc.getLength(), center, false);
  }
}
