// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.emojipicker.ui;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.paint.RectanglePainter2D;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ImageLoader;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.emojipicker.Emoji;
import org.jetbrains.plugins.emojipicker.EmojiCategory;
import org.jetbrains.plugins.emojipicker.EmojiSkinTone;
import org.jetbrains.plugins.emojipicker.messages.EmojiCategoriesBundle;
import org.jetbrains.plugins.emojipicker.messages.EmojipickerBundle;
import org.jetbrains.plugins.emojipicker.service.EmojiService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextLayout;
import java.util.List;

import static java.util.Objects.requireNonNull;

final class EmojiListPanel extends JBScrollPane {
  private static final Image NO_EMOJI_FOUND_IMAGE = ImageLoader.loadFromStream(
    requireNonNull(EmojiListPanel.class.getResourceAsStream("/org/jetbrains/plugins/emojipicker/icons/NoEmojiFound.png"))
  );

  private static final int HORIZONTAL_PADDING = 8;
  private final Dimension myCellSize = new Dimension(JBUIScale.scale(40), JBUIScale.scale(40));
  private final Dimension myCellGaps = new Dimension(JBUIScale.scale(3), JBUIScale.scale(3));
  private final Image myNoEmojiFoundImage = ImageLoader.scaleImage(requireNonNull(NO_EMOJI_FOUND_IMAGE), JBUIScale.scale(40));

  private final EmojiPicker myEmojiPicker;
  private final EmojiPickerStyle myStyle;
  private final CategoriesListPanel myCategoriesPanel;
  private final Category mySearchCategoryPanel;
  private UpdatableLayoutPanel myCurrentPanel;
  private int myItemsPerRow = 0;
  private Category myCurrentItemCategory;
  private int myCurrentItemIndex = -1;
  private EmojiSkinTone myCurrentSkinTone = EmojiSkinTone.NO_TONE;

  EmojiListPanel(EmojiPicker emojiPicker, EmojiPickerStyle style, List<EmojiCategory> categories) {
    super(null, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myEmojiPicker = emojiPicker;
    myStyle = style;
    myCategoriesPanel = new CategoriesListPanel(ContainerUtil.map(categories, Category::new));
    mySearchCategoryPanel = new Category(null);
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        myItemsPerRow = (getWidth() - JBUIScale.scale(HORIZONTAL_PADDING) * 2 + myCellGaps.width) / (myCellSize.width + myCellGaps.width);
        myCurrentPanel.updateLayout(getWidth());
        setViewportView(myCurrentPanel);
      }
    });
    getViewport().addChangeListener(e -> {
      if (myCurrentPanel == myCategoriesPanel) {
        Component topComponent = myCategoriesPanel.getComponentAt(getViewport().getViewPosition());
        if (topComponent instanceof Category) {
          emojiPicker.selectCategory(((Category)topComponent).myCategory, false);
        }
        else {
          emojiPicker.selectCategory(myCategoriesPanel.myCategories.get(0).myCategory, false);
        }
      }
    });
    setBorder(JBUI.Borders.customLine(myStyle.myBorderColor, 1, 0, 0, 0));
  }

  void selectCategory(EmojiCategory category) {
    if (myCurrentPanel != myCategoriesPanel) {
      setViewportView(myCurrentPanel = myCategoriesPanel);
    }
    if (category == null || category == myCategoriesPanel.myCategories.get(0).myCategory) {
      setCurrentItem(myCategoriesPanel.myCategories.get(0).myCategory.getEmoji().isEmpty() ?
                     myCategoriesPanel.myCategories.get(1) : myCategoriesPanel.myCategories.get(0), 0);
      myCategoriesPanel.scrollRectToVisible(new Rectangle(0, 0, 1, getHeight()));
    }
    else {
      Category cat = null;
      for (Category c : myCategoriesPanel.myCategories) {
        if (c.myCategory == category) cat = c;
      }
      if (cat == null) return;
      setCurrentItem(cat, 0);
      cat.scrollRectToVisible(new Rectangle(0, 0, 1, getHeight()));
    }
  }

  void updateSkinTone(EmojiSkinTone skinTone) {
    myCurrentSkinTone = skinTone;
    myCurrentPanel.repaint();
    if (myCurrentItemCategory != null && myCurrentItemIndex >= 0) {
      myEmojiPicker.updateEmojiInfo(myCurrentItemCategory.myCategory.getEmoji().get(myCurrentItemIndex), myCurrentSkinTone);
    }
  }

  void updateSearchFilter(@NonNls String text) {
    if (text.isEmpty()) {
      myEmojiPicker.selectCategory(myCategoriesPanel.myCategories.get(0).myCategory, true);
    }
    else {
      myEmojiPicker.selectCategory(null, false);
      mySearchCategoryPanel.myCategory = new EmojiCategory(null, EmojiService.getInstance().findEmojiByPrefix(text.strip()));
      mySearchCategoryPanel.updateLayout(getWidth());
      if (mySearchCategoryPanel.myCategory.getEmoji().isEmpty()) {
        setCurrentItem(null, -1);
      }
      else {
        setCurrentItem(mySearchCategoryPanel, 0);
      }
      setViewportView(myCurrentPanel = mySearchCategoryPanel);
    }
  }

  void selectCurrentEmoji() {
    if (!hasCurrentItem()) return;
    Emoji emoji = myCurrentItemCategory.myCategory.getEmoji().get(myCurrentItemIndex);
    EmojiService.getInstance().saveRecentlyUsedEmoji(emoji);
    myEmojiPicker.selectEmoji(emoji.getTonedValue(myCurrentSkinTone));
  }

  void navigate(int dx, int dy) {
    if (myCurrentItemIndex < 0) return;
    int x = (myCurrentItemIndex % myItemsPerRow + dx + myItemsPerRow) % myItemsPerRow;
    int y = myCurrentItemIndex / myItemsPerRow + dy;
    int newIndex = myItemsPerRow * y + x;
    if (newIndex >= 0 && newIndex < myCurrentItemCategory.myCategory.getEmoji().size()) {
      setCurrentItem(myCurrentItemCategory, newIndex);
    }
    else {
      int cat = myCategoriesPanel.findCategoryIndex(myCurrentItemCategory);
      if (cat == -1) return;
      boolean up = newIndex < 0;
      cat += up ? -1 : 1;
      if (cat < 0 || cat >= myCategoriesPanel.myCategories.size()) return;
      Category c = myCategoriesPanel.myCategories.get(cat);
      int rows = (c.myCategory.getEmoji().size() + myItemsPerRow - 1) / myItemsPerRow;
      y = up ? rows - 1 : 0;
      if (y < 0 || myItemsPerRow * y + x >= c.myCategory.getEmoji().size()) {
        y--;
        if (y < 0) return;
      }
      setCurrentItem(c, myItemsPerRow * y + x);
    }
    int overscroll = (myCellSize.height + myCellGaps.height) * dy;
    int overscrollUp = Math.max(-overscroll, 0);
    int overscrollDown = Math.max(overscroll, 0);
    myCurrentItemCategory.scrollRectToVisible(new Rectangle(
      myCurrentItemCategory.xGridToVisible(x),
      myCurrentItemCategory.yGridToVisible(y) - JBUIScale.scale(Category.LABEL_HEIGHT) - overscrollUp,
      myCellSize.width,
      myCellSize.height + JBUIScale.scale(Category.LABEL_HEIGHT) + overscrollUp + overscrollDown
    ));
  }

  boolean hasCurrentItem() {
    return myCurrentItemCategory != null && myCurrentItemIndex >= 0;
  }

  private void setCurrentItem(Category category, int item) {
    myCurrentItemCategory = category;
    myCurrentItemIndex = item;
    myCurrentPanel.repaint();
    myEmojiPicker.updateEmojiInfo(category == null ? null : category.myCategory.getEmoji().get(item), myCurrentSkinTone);
  }


  private abstract class UpdatableLayoutPanel extends JPanel {

    private UpdatableLayoutPanel() {
      setBackground(myStyle.myBackgroundColor);
    }

    abstract void updateLayout(int width);
  }


  private class CategoriesListPanel extends UpdatableLayoutPanel {

    private final List<Category> myCategories;

    private CategoriesListPanel(List<Category> categories) {
      myCategories = categories;
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      add(Box.createRigidArea(new Dimension(0, JBUIScale.scale(15))));
      for (Category c : categories) add(c);
    }

    @Override
    void updateLayout(int width) {
      for (Category c : myCategories) c.updateLayout(width);
    }

    private int findCategoryIndex(Category c) {
      for (int i = 0; i < myCategories.size(); i++) {
        if (myCategories.get(i) == c) return i;
      }
      return -1;
    }
  }


  private class Category extends UpdatableLayoutPanel {

    private static final int NO_LABEL_PADDING = 12;
    private static final int LABEL_HEIGHT = 32;
    private EmojiCategory myCategory;
    private final @Nls String myName;
    private final Insets myPadding;

    private Category(EmojiCategory category) {
      myCategory = category;
      myName = EmojiCategoriesBundle.findNameForCategory(category);
      myPadding = JBUI.insets(myName == null ? NO_LABEL_PADDING : LABEL_HEIGHT, HORIZONTAL_PADDING, 8, HORIZONTAL_PADDING);
      MouseAdapter mouseAdapter = new MouseAdapter() {
        private int getItemIndexUnderCursor(Point point) {
          if (point.x < myPadding.left || point.y < myPadding.top) return -1;
          int x = xVisibleToGrid(point.x), y = yVisibleToGrid(point.y);
          if (x >= myItemsPerRow || point.x > xGridToVisible(x) + myCellSize.width || point.y > yGridToVisible(y) + myCellSize.height) {
            return -1;
          }
          int index = myItemsPerRow * y + x;
          return index >= 0 && index < myCategory.getEmoji().size() ? index : -1;
        }

        private boolean setCurrentItemUnderCursor(Point point) {
          int item = getItemIndexUnderCursor(point);
          if (item != -1) {
            setCurrentItem(Category.this, item);
            return true;
          }
          else {
            return false;
          }
        }

        @Override
        public void mouseClicked(MouseEvent e) {
          if (setCurrentItemUnderCursor(e.getPoint())) {
            selectCurrentEmoji();
          }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
          setCurrentItemUnderCursor(e.getPoint());
        }
      };
      addMouseMotionListener(mouseAdapter);
      addMouseListener(mouseAdapter);
    }

    private int xVisibleToGrid(int x) {
      return (x - myPadding.left) / (myCellSize.width + myCellGaps.width);
    }

    private int yVisibleToGrid(int y) {
      return (y - myPadding.top) / (myCellSize.height + myCellGaps.height);
    }

    private int xGridToVisible(int x) {
      return x * (myCellSize.width + myCellGaps.width) + myPadding.left;
    }

    private int yGridToVisible(int y) {
      return y * (myCellSize.height + myCellGaps.height) + myPadding.top;
    }

    @Override
    void updateLayout(int width) {
      int itemCount = myCategory.getEmoji().size();
      setPreferredSize(new Dimension(width, yGridToVisible((itemCount + myItemsPerRow - 1) / myItemsPerRow) + myPadding.bottom));
    }

    /**
     * Component needs to take full width in order to be able to draw under scrollbar, when it's inside CategoriesListPanel
     */
    @Override
    public int getWidth() {
      return EmojiListPanel.this.getWidth();
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);
      ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false));
      if (myName == null && myCategory.getEmoji().isEmpty()) {
        paintNoEmojiFound(g);
      }
      else {
        if (myItemsPerRow <= 0) return;
        paintSelection(g);
        paintItems(g);
        if (myName != null) paintCategoryLabel(g);
      }
    }

    private void paintNoEmojiFound(Graphics g) {
      if (g instanceof Graphics2D g2) {
        int x = (getWidth() - myNoEmojiFoundImage.getWidth(this)) / 2;
        int y = (getHeight() - myNoEmojiFoundImage.getHeight(this)) / 2;
        final Composite saveComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.5F));
        StartupUiUtil.drawImage(g, myNoEmojiFoundImage, x, y, null);
        g2.setComposite(saveComposite);
      }
      g.setColor(myStyle.myNoEmojiFoundTextColor);
      g.setFont(myStyle.myFont);
      @Nls String message = EmojipickerBundle.message("message.EmojiPicker.NoEmojiFound");
      int x = (getWidth() - g.getFontMetrics().stringWidth(message)) / 2;
      int y = (getHeight() + myNoEmojiFoundImage.getHeight(this)) / 2 + JBUIScale.scale(25);
      g.drawString(message, x, y);
    }

    private void paintSelection(Graphics g) {
      if (myCurrentItemCategory == this && myCurrentItemIndex != -1) {
        int x = xGridToVisible(myCurrentItemIndex % myItemsPerRow);
        int y = yGridToVisible(myCurrentItemIndex / myItemsPerRow);
        g.setColor(myStyle.myHoverBackgroundColor);
        RectanglePainter2D.FILL.paint((Graphics2D)g, x, y, myCellSize.width, myCellSize.height, 6.0);
      }
    }

    private void paintItems(Graphics g) {
      Rectangle bounds = g.getClipBounds();
      int from = yVisibleToGrid(bounds.y) * myItemsPerRow, to = yVisibleToGrid(bounds.y + bounds.height) * myItemsPerRow + myItemsPerRow;
      if (from < 0) from = 0;
      int itemCount = myCategory.getEmoji().size();
      if (to > itemCount) to = itemCount;
      if (from > to) from = to;
      FontMetrics metrics = g.getFontMetrics(myStyle.myEmojiFont);
      int verticalOffset = metrics.getHeight() / 2 - metrics.getDescent();
      for (int i = from; i < to; i++) {
        int x = xGridToVisible(i % myItemsPerRow), y = yGridToVisible(i / myItemsPerRow);
        @Nls String item = myCategory.getEmoji().get(i).getTonedValue(myCurrentSkinTone);
        int width = metrics.stringWidth(item);
        new TextLayout(item, myStyle.myEmojiFont, ((Graphics2D)g).getFontRenderContext())
          .draw((Graphics2D)g, x + (myCellSize.width - width) / 2F, y + myCellSize.height / 2F + verticalOffset);
      }
    }

    private void paintCategoryLabel(Graphics g) {
      int offset = Math.min(g.getClipBounds().y, getHeight() - JBUIScale.scale(LABEL_HEIGHT));
      g.setColor(myStyle.myBackgroundColor);
      RectanglePainter2D.FILL.paint((Graphics2D)g, 0, offset, getWidth(), JBUIScale.scale(LABEL_HEIGHT));
      g.setFont(myStyle.myFont);
      g.setColor(myStyle.myTextColor);
      g.drawString(myName, JBUIScale.scale(16), offset + JBUIScale.scale(20));
    }
  }
}
