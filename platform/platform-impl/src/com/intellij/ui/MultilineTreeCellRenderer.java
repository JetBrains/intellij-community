// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.font.TextAttribute;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;

public abstract class MultilineTreeCellRenderer extends JComponent implements Accessible, TreeCellRenderer {

  private boolean myWrapsCalculated = false;
  private boolean myTooSmall = false;
  private int myHeightCalculated = -1;
  private int myWrapsCalculatedForWidth = -1;

  private ArrayList myWraps = new ArrayList();

  private int myMinHeight = 1;
  private Insets myTextInsets;
  private final Insets myLabelInsets = new Insets(1, 2, 1, 2);

  private boolean mySelected;
  private boolean myHasFocus;

  private Icon myIcon;
  private String[] myLines = ArrayUtilRt.EMPTY_STRING_ARRAY;
  private String myPrefix;
  private int myTextLength;
  private int myPrefixWidth;
  @NonNls protected static final String FONT_PROPERTY_NAME = "font";
  private JTree myTree;


  public MultilineTreeCellRenderer() {
    myTextInsets = new Insets(0,0,0,0);

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        onSizeChanged();
      }
    });

    addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (FONT_PROPERTY_NAME.equalsIgnoreCase(evt.getPropertyName())) {
          onFontChanged();
        }
      }
    });
    updateUI();
  }

  @Override
  public void updateUI() {
    GraphicsUtil.setAntialiasingType(this, AntialiasingType.getAAHintForSwingComponent());
  }

  protected void setMinHeight(int height) {
    myMinHeight = height;
    myHeightCalculated = Math.max(myMinHeight, myHeightCalculated);
  }

  protected void setTextInsets(Insets textInsets) {
    myTextInsets = textInsets;
    onSizeChanged();
  }

  private void onFontChanged() {
    myWrapsCalculated = false;
  }

  private void onSizeChanged() {
    int currWidth = getWidth();
    if (currWidth != myWrapsCalculatedForWidth) {
      myWrapsCalculated = false;
      myHeightCalculated = -1;
      myWrapsCalculatedForWidth = -1;
    }
  }

  private FontMetrics getCurrFontMetrics() {
    // Disable kerning for font because of huge performance penalty
    // String width will increase a bit but it's OK here
    Font font = getFont().deriveFont(
      Collections.singletonMap(TextAttribute.KERNING, Integer.valueOf(0)));
    return getFontMetrics(font);
  }

  @Override
  public void paint(Graphics g) {
    int height = getHeight();
    int width = getWidth();
    int borderX = myLabelInsets.left - 1;
    int borderY = myLabelInsets.top - 1;
    int borderW = width - borderX - myLabelInsets.right + 2;
    int borderH = height - borderY - myLabelInsets.bottom + 1;

    if (myIcon != null) {
      int verticalIconPosition = (height - myIcon.getIconHeight())/2;
      myIcon.paintIcon(this, g, 0, isIconVerticallyCentered() ? verticalIconPosition : myTextInsets.top);
      borderX += myIcon.getIconWidth();
      borderW -= myIcon.getIconWidth();
    }

    Color bgColor = UIUtil.getTreeBackground(mySelected, myHasFocus);
    Color fgColor = UIUtil.getTreeForeground(mySelected, myHasFocus);

    // fill background
    if (!WideSelectionTreeUI.isWideSelection(myTree)) {
      g.setColor(bgColor);
      g.fillRect(borderX, borderY, borderW, borderH);

      // draw border
      if (mySelected) {
        g.setColor(UIUtil.getTreeSelectionBorderColor());
        UIUtil.drawDottedRectangle(g, borderX, borderY, borderX + borderW - 1, borderY + borderH - 1);
      }
    }

    // paint text
    recalculateWraps();

    if (myTooSmall) { // TODO ???
      return;
    }

    int fontHeight = getCurrFontMetrics().getHeight();
    int currBaseLine = getCurrFontMetrics().getAscent();
    currBaseLine += myTextInsets.top;
    g.setFont(getFont());
    g.setColor(fgColor);
    UISettings.setupAntialiasing(g);

    if (!StringUtil.isEmpty(myPrefix)) {
      g.drawString(myPrefix, myTextInsets.left - myPrefixWidth + 1, currBaseLine);
    }

    for (int i = 0; i < myWraps.size(); i++) {
      String currLine = (String)myWraps.get(i);
      g.drawString(currLine, myTextInsets.left, currBaseLine);
      currBaseLine += fontHeight;  // first is getCurrFontMetrics().getAscent()
    }
  }

  public void setText(String[] lines, String prefix) {
    myLines = lines;
    myTextLength = 0;
    for (String line : lines) {
      myTextLength += line.length();
    }
    myPrefix = prefix;

    myWrapsCalculated = false;
    myHeightCalculated = -1;
    myWrapsCalculatedForWidth = -1;
  }

  public void setIcon(Icon icon) {
    myIcon = icon;

    myWrapsCalculated = false;
    myHeightCalculated = -1;
    myWrapsCalculatedForWidth = -1;
  }

  @Override
  public Dimension getMinimumSize() {
    if (getFont() != null) {
      int minHeight = getCurrFontMetrics().getHeight();
      return new Dimension(minHeight, minHeight);
    }
    return new Dimension(
      MIN_WIDTH + myTextInsets.left + myTextInsets.right,
      MIN_WIDTH + myTextInsets.top + myTextInsets.bottom
    );
  }

  private static final int MIN_WIDTH = 10;

  // Calculates height for current width.
  @Override
  public Dimension getPreferredSize() {
    recalculateWraps();
    return new Dimension(myWrapsCalculatedForWidth, myHeightCalculated);
  }

  // Calculate wraps for the current width
  private void recalculateWraps() {
    int currwidth = getWidth();
    if (myWrapsCalculated) {
      if (currwidth == myWrapsCalculatedForWidth) {
        return;
      }
      else {
        myWrapsCalculated = false;
      }
    }
    int wrapsCount = calculateWraps(currwidth);
    myTooSmall = (wrapsCount == -1);
    if (myTooSmall) {
      wrapsCount = myTextLength;
    }
    int fontHeight = getCurrFontMetrics().getHeight();
    myHeightCalculated = wrapsCount * fontHeight + myTextInsets.top + myTextInsets.bottom;
    myHeightCalculated = Math.max(myMinHeight, myHeightCalculated);

    int maxWidth = 0;
    for (int i=0; i < myWraps.size(); i++) {
      String s = (String)myWraps.get(i);
      int width = getCurrFontMetrics().stringWidth(s);
      maxWidth = Math.max(maxWidth, width);
    }

    myWrapsCalculatedForWidth = myTextInsets.left + maxWidth + myTextInsets.right;
    myWrapsCalculated = true;
  }

  private int calculateWraps(int width) {
    myTooSmall = width < MIN_WIDTH;
    if (myTooSmall) {
      return -1;
    }

    int result = 0;
    myWraps = new ArrayList();

    for (String aLine : myLines) {
      int lineFirstChar = 0;
      int lineLastChar = aLine.length() - 1;
      int currFirst = lineFirstChar;
      int printableWidth = width - myTextInsets.left - myTextInsets.right;
      if (aLine.isEmpty()) {
        myWraps.add(aLine);
        result++;
      }
      else {
        while (currFirst <= lineLastChar) {
          int currLast = calculateLastVisibleChar(aLine, printableWidth, currFirst, lineLastChar);
          if (currLast < lineLastChar) {
            int currChar = currLast + 1;
            if (!Character.isWhitespace(aLine.charAt(currChar))) {
              while (currChar >= currFirst) {
                if (Character.isWhitespace(aLine.charAt(currChar))) {
                  break;
                }
                currChar--;
              }
              if (currChar > currFirst) {
                currLast = currChar;
              }
            }
          }
          myWraps.add(aLine.substring(currFirst, currLast + 1));
          currFirst = currLast + 1;
          while ((currFirst <= lineLastChar) && (Character.isWhitespace(aLine.charAt(currFirst)))) {
            currFirst++;
          }
          result++;
        }
      }
    }
    return result;
  }

  private int calculateLastVisibleChar(String line, int viewWidth, int firstChar, int lastChar) {
    if (firstChar == lastChar) return lastChar;
    if (firstChar > lastChar) throw new IllegalArgumentException("firstChar=" + firstChar + ", lastChar=" + lastChar);
    int totalWidth = getCurrFontMetrics().stringWidth(line.substring(firstChar, lastChar + 1));
    if (totalWidth == 0 || viewWidth > totalWidth) {
      return lastChar;
    }
    else {
      int newApprox = (lastChar - firstChar + 1) * viewWidth / totalWidth;
      int currChar = firstChar + Math.max(newApprox - 1, 0);
      int currWidth = getCurrFontMetrics().stringWidth(line.substring(firstChar, currChar + 1));
      while (true) {
        if (currWidth > viewWidth) {
          currChar--;
          if (currChar <= firstChar) {
            return firstChar;
          }
          currWidth -= getCurrFontMetrics().charWidth(line.charAt(currChar + 1));
          if (currWidth <= viewWidth) {
            return currChar;
          }
        }
        else {
          currChar++;
          if (currChar > lastChar) {
            return lastChar;
          }
          currWidth += getCurrFontMetrics().charWidth(line.charAt(currChar));
          if (currWidth >= viewWidth) {
            return currChar - 1;
          }
        }
      }
    }
  }

  private int getChildIndent(JTree tree) {
    TreeUI newUI = tree.getUI();
    if (newUI instanceof BasicTreeUI btreeui) {
      return btreeui.getLeftChildIndent() + btreeui.getRightChildIndent();
    }
    else {
      return ((Integer)UIUtil.getTreeLeftChildIndent()).intValue() + ((Integer)UIUtil.getTreeRightChildIndent()).intValue();
    }
  }

  private int getAvailableWidth(Object forValue, JTree tree) {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)forValue;
    int busyRoom = tree.getInsets().left + tree.getInsets().right + getChildIndent(tree) * node.getLevel();
    return tree.getVisibleRect().width - busyRoom - 2;
  }

  protected abstract void initComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus);

  @Override
  public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    setFont(UIUtil.getTreeFont());

    initComponent(tree, value, selected, expanded, leaf, row, hasFocus);

    mySelected = selected;
    myHasFocus = hasFocus;

    myTree = tree;

    int availWidth = getAvailableWidth(value, tree);
    if (availWidth > 0) {
      setSize(availWidth, 100);     // height will be calculated automatically
    }

    int leftInset = myLabelInsets.left;

    if (myIcon != null) {
      leftInset += myIcon.getIconWidth() + 2;
    }

    if (!StringUtil.isEmpty(myPrefix)) {
      myPrefixWidth = getCurrFontMetrics().stringWidth(myPrefix) + 5;
      leftInset += myPrefixWidth;
    }

    setTextInsets(new Insets(myLabelInsets.top, leftInset, myLabelInsets.bottom, myLabelInsets.right));
    if (myIcon != null) {
      setMinHeight(myIcon.getIconHeight());
    }
    else {
      setMinHeight(1);
    }

    setSize(getPreferredSize());
    recalculateWraps();

    return this;
  }

  /**
   * Returns {@code true} if icon should be vertically centered. Otherwise, icon will be placed on top
   */
  protected boolean isIconVerticallyCentered() {
    return false;
  }

  public static JScrollPane installRenderer(final JTree tree, final MultilineTreeCellRenderer renderer) {
    final TreeCellRenderer defaultRenderer = tree.getCellRenderer();

    JScrollPane scrollpane = new JBScrollPane(tree){
      private int myAddRemoveCounter = 0;
      private boolean myShouldResetCaches = false;
      @Override
      public void setSize(Dimension d) {
        boolean isChanged = getWidth() != d.width || myShouldResetCaches;
        super.setSize(d);
        if (isChanged) resetCaches();
      }

      @Override
      public void reshape(int x, int y, int w, int h) {
        boolean isChanged = w != getWidth() || myShouldResetCaches;
        super.reshape(x, y, w, h);
        if (isChanged) resetCaches();
      }

      private void resetCaches() {
        resetHeightCache(tree, defaultRenderer, renderer);
        myShouldResetCaches = false;
      }

      @Override
      public void addNotify() {
        super.addNotify();
        if (myAddRemoveCounter == 0) myShouldResetCaches = true;
        myAddRemoveCounter++;
      }

      @Override
      public void removeNotify() {
        super.removeNotify();
        myAddRemoveCounter--;
      }
    };
    scrollpane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
    scrollpane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

    tree.setCellRenderer(renderer);

    scrollpane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        resetHeightCache(tree, defaultRenderer, renderer);
      }

      @Override
      public void componentShown(ComponentEvent e) {
        // componentResized not called when adding to opened tool window.
        // Seems to be BUG#4765299, however I failed to create same code to reproduce it.
        // To reproduce it with IDEA: 1. remove this method, 2. Start any Ant task, 3. Keep message window open 4. start Ant task again.
        resetHeightCache(tree, defaultRenderer, renderer);
      }
    });

    return scrollpane;
  }

  @NotNull
  public String getText() {
    StringBuilder sb = new StringBuilder();
    myWraps.forEach(o -> sb.append(o.toString() + "\n"));
    return sb.toString();
  }

  private static void resetHeightCache(final JTree tree,
                                       final TreeCellRenderer defaultRenderer,
                                       final MultilineTreeCellRenderer renderer) {
    tree.setCellRenderer(defaultRenderer);
    tree.setCellRenderer(renderer);
  }

//  private static class DelegatingScrollablePanel extends JPanel implements Scrollable {
//    private final Scrollable myDelegatee;
//
//    public DelegatingScrollablePanel(Scrollable delegatee) {
//      super(new BorderLayout(0, 0));
//      myDelegatee = delegatee;
//      add((JComponent)delegatee, BorderLayout.CENTER);
//    }
//
//    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
//      return myDelegatee.getScrollableUnitIncrement(visibleRect, orientation, direction);
//    }
//
//    public boolean getScrollableTracksViewportWidth() {
//      return myDelegatee.getScrollableTracksViewportWidth();
//    }
//
//    public Dimension getPreferredScrollableViewportSize() {
//      return myDelegatee.getPreferredScrollableViewportSize();
//    }
//
//    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
//      return myDelegatee.getScrollableBlockIncrement(visibleRect, orientation, direction);
//    }
//
//    public boolean getScrollableTracksViewportHeight() {
//      return myDelegatee.getScrollableTracksViewportHeight();
//    }
//  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleMultilineTreeCellRenderer();
    }
    return accessibleContext;
  }

  protected class AccessibleMultilineTreeCellRenderer extends AccessibleJComponent {
    @Override
    public String getAccessibleName() {
      @NlsSafe String name = accessibleName;
      if (name == null) {
        name = (String)getClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY);
      }

      if (name == null) {
        StringBuilder sb = new StringBuilder();
        for (String aLine : myLines) {
          sb.append(aLine);
          sb.append(System.lineSeparator());
        }
        if (sb.length() > 0) name = sb.toString();
      }

      if (name == null) {
        name = super.getAccessibleName();
      }
      return name;
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.LABEL;
    }
  }

  public Icon getIcon() {
    return myIcon;
  }

  public String getPrefix() {
    return myPrefix;
  }

  public String[] getLines() {
    return myLines;
  }
}

