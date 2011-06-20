/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;

/**
 * @author max
 */
public class TitlePanel extends CaptionPanel {

  private final JLabel myIconLabel;
  private final HtmlLabel myLabel;

  private final Icon myRegular;
  private final Icon myInactive;

  @Nullable
  private Component myContentComponent;

  public TitlePanel() {
    this(null, null, null);
  }

  public TitlePanel(Icon regular, Icon inactive, @Nullable Component contentComponent) {
    myRegular = regular;
    myInactive = inactive;
    myContentComponent = contentComponent;


    myLabel = new HtmlLabel();
    add(myLabel, BorderLayout.CENTER);

    myIconLabel = new JLabel();
    myIconLabel.setOpaque(false);
    myIconLabel.setHorizontalAlignment(SwingConstants.CENTER);
    myIconLabel.setVerticalAlignment(SwingConstants.CENTER);
    myIconLabel.setBorder(null);

    add(myIconLabel, BorderLayout.WEST);

    setBorder(new EmptyBorder(1, 2, 2, 2));
    setActive(false);
  }

  public void setFontBold(boolean bold) {
    myLabel.setFont(myLabel.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN));
  }

  public void setActive(final boolean active) {
    super.setActive(active);
    myIconLabel.setIcon(active ? myRegular : myInactive);
    myLabel.setForeground(active ? UIUtil.getLabelForeground() : Color.gray);
  }

  public void setText(String titleText) {
    myLabel.setText(titleText);
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(10, getPreferredSize().height);
  }


  private class HtmlLabel extends JPanel  {

    private JComponent myRenderer = new JEditorPane("text/html", "");
    private JEditorPane myPane = new JEditorPane("text/html", "");
    private String myText;

    private HtmlLabel() {
      setBorder(null);
      setOpaque(false);
      if (UIUtil.isUnderNimbusLookAndFeel()) {
        myRenderer = new JLabel();
        ((JLabel)myRenderer).setHorizontalAlignment(SwingConstants.CENTER);
        ((JLabel)myRenderer).setVerticalAlignment(SwingConstants.CENTER);
      } else {
        myRenderer = new JEditorPane();
        ((JEditorPane)myRenderer).setEditable(false);
        ((JEditorPane)myRenderer).setEditorKit(new HTMLEditorKit());
      }

      myRenderer.setFocusable(false);
      myRenderer.setOpaque(false);
      myRenderer.setForeground(Color.black);
      myRenderer.setBorder(null);

      myPane.setBorder(null);


      add(myRenderer);
    }

    @Override
    public void doLayout() {
      myPane.setSize(Integer.MAX_VALUE, Integer.MAX_VALUE);
      myPane.setText(getText());
      final int prefWidth = myPane.getPreferredSize().width;
      if (prefWidth < getWidth()) {
        myRenderer.setBounds((getWidth() - prefWidth) / 2, 0, prefWidth, getHeight());
      } else {
        myRenderer.setBounds(0, 0, getWidth(), getHeight());
      }
    }

    public void setText(String text) {
      myText = text;
      if (myRenderer instanceof JEditorPane) {
        final String body = UIUtil.getHtmlBody(text);
        myText = UIUtil.toHtml(body);
        ((JEditorPane)myRenderer).setText(myText);
      } else {
        ((JLabel)myRenderer).setText(myText);
      }
    }

    public String getText() {
      return myText;
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension contentSize;
      if (myContentComponent == null) {
        contentSize = new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
      } else {
        contentSize = myContentComponent.getPreferredSize();

        final Dimension preferredSize = super.getPreferredSize();
        if (preferredSize.width <= 300) {
          contentSize = preferredSize;
        }
      }

      myPane.setText(getText());
      myPane.setBounds(new Rectangle(0, 0, contentSize.width, Integer.MAX_VALUE));
      return new Dimension(contentSize.width, myPane.getPreferredSize().height);
    }
  }
}


