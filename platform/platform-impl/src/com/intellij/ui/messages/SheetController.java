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
import com.intellij.util.ui.UIUtil;

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
  public static int SHEET_HEIGHT = 150;

  private String myResult;
  private JPanel mySheetPanel;
  private SheetMessage mySheetMessage;

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
    JPanel sheetPanel = new JPanel(new GridBagLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        final Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f));

        g2d.setColor(Gray._225);
        //g2d.setColor(Color.black);
        Rectangle2D dialog  = new Rectangle2D.Double(0,0,mySheetPanel.getBounds().width - 5, mySheetPanel.getBounds().height - 10);
        g2d.fill(dialog);
        paintShadow(g2d, dialog);
      }


      //  }
    };
    sheetPanel.setOpaque(false);

    myHeaderLabel.setFont(boldFont);
    myHeaderLabel.setText(title);

    GridBagConstraints c1 = new GridBagConstraints();

    c1.gridheight = 3;

    //c1.fill =  GridBagConstraints.BOTH;
    c1.anchor = GridBagConstraints.FIRST_LINE_START;
    c1.gridx = 0;
    c1.gridy = 0;
    c1.insets = new Insets(0,0,10,10);



    addIcon(sheetPanel, c1);

    GridBagConstraints c2 = new GridBagConstraints();

    c2.gridx = 1;
    c2.gridy = 0;
    c2.insets = new Insets(0,10,10,10);
    c2.anchor = GridBagConstraints.FIRST_LINE_START;


    myMessageLabel  = new MultilineLabel();
    // myMessageLabel.setMaximumSize(new Dimension(200,90));
    myMessageLabel.setFont(regularFont);
    myMessageLabel.setText(message);
    sheetPanel.add(myMessageLabel, c2);
    GridBagConstraints c3 = new GridBagConstraints();

    c3.gridx = 1;
    c3.gridy = 1;
    c3.insets = new Insets(0,0,10,10);
    c3.anchor = GridBagConstraints.FIRST_LINE_START;

    sheetPanel.add(myMessageLabel, c3);

    GridBagConstraints c3_1 = new GridBagConstraints();

    c3_1.gridx = 1;
    c3_1.gridy = 2;
    c3_1.insets = new Insets(0,0,10,10);
    c3_1.anchor = GridBagConstraints.FIRST_LINE_START;

    if (myDoNotAskOption != null) {
      doNotAskCheckBox = new JCheckBox(myDoNotAskOption.getDoNotShowMessage());
      doNotAskCheckBox.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          myDoNotAskResult = e.getStateChange() == ItemEvent.SELECTED;
        }
      });
      sheetPanel.add(doNotAskCheckBox, c3_1);
    }

    GridBagConstraints c4 = new GridBagConstraints();

    c4.gridx = 1;
    c4.gridy = 4;
    c4.insets = new Insets(10,10,10,10);
    c4.anchor = GridBagConstraints.LAST_LINE_END;

    final JPanel buttonPanel = new JPanel(new GridBagLayout());
    buttonPanel.setOpaque(false);
    layoutButtons(buttons, buttonPanel);

    sheetPanel.add(buttonPanel, c4);

    sheetPanel.setPreferredSize(new Dimension(SHEET_WIDTH, SHEET_HEIGHT));



    return sheetPanel;
  }

  private void paintShadow(Graphics2D g2d, Rectangle2D dialog) {


    //   if (!shrinkDialog.get()) {
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

  private static void layoutButtons(final JButton[] buttons, final JPanel buttonPanel) {
    GridBagConstraints c5 = new GridBagConstraints();
    c5.anchor = GridBagConstraints.LAST_LINE_END;
    for (JButton button : buttons) {
      buttonPanel.add(button, c5);
    }
  }


  private void addIcon(JPanel jPanel, GridBagConstraints c1) {
    JPanel ico = new JPanel() {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        AllIcons.Logo_welcomeScreen.paintIcon(this, g, 0, 0);
      }
    };
    ico.setPreferredSize(new Dimension(AllIcons.Logo_welcomeScreen.getIconWidth(), AllIcons.Logo_welcomeScreen.getIconWidth()));
    jPanel.add(ico, c1);
  }

  /**
   * This method is used to show an image during message showing
   * @return image to show
   */
  private Image getStaticImage() {
    final JFrame myOffScreenFrame = new JFrame() ;
    myOffScreenFrame.add(mySheetPanel);
    myOffScreenFrame.getRootPane().setDefaultButton(myDefaultButton);

    myOffScreenFrame.pack();
    final BufferedImage image = UIUtil.createImage(SHEET_WIDTH, SHEET_HEIGHT, BufferedImage.TYPE_INT_ARGB);

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
        int zeroOffset = getHeight() - 150;
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f));
        g2d.drawImage(staticImage, 0 , zeroOffset, null);
        // g2d.setColor(new Color (225,225,225, 0));

        // Rectangle2D dialog  = new Rectangle2D.Double(0,0,mySheetPanel.getBounds().width - 5, mySheetPanel.getBounds().height - 10);


        //paintShadow(g2d, dialog);

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
