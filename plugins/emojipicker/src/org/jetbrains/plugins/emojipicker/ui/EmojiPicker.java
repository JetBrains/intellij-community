// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.emojipicker.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.WindowMoveListener;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.emojipicker.Emoji;
import org.jetbrains.plugins.emojipicker.EmojiCategory;
import org.jetbrains.plugins.emojipicker.EmojiSkinTone;
import org.jetbrains.plugins.emojipicker.service.EmojiService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


public class EmojiPicker extends JLayeredPane {
  private static final JBDimension DEFAULT_SIZE = new JBDimension(358, 415);

  private final List<EmojiCategory> myCategories;
  private final EmojiSearchField mySearchField;
  private final EmojiCategoryPanel myCategoryPanel;
  private final EmojiListPanel myEmojiListPanel;
  private final EmojiInfoPanel myEmojiInfoPanel;
  private final JPanel myEmojiSkinTonesModal;
  private final EmojiSkinTonesPanel myEmojiSkinTonesPanel;
  private final KeyboardManager myKeyboardManager;
  private Consumer<String> myInputCallback = emoji -> {
  };


  private EmojiPicker() {
    myCategories = EmojiService.getInstance().getCategories();

    setPreferredSize(DEFAULT_SIZE.size());
    setLayout(new JPanelFillLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1;
    gbc.gridx = 0;
    GridBagConstraints fillerGbc = (GridBagConstraints)gbc.clone();
    fillerGbc.weighty = 1;

    EmojiPickerStyle style = new EmojiPickerStyle();
    JPanel contentPanel = new JPanel();
    myCategoryPanel = new EmojiCategoryPanel(this, style, myCategories);
    mySearchField = new EmojiSearchField(this, style);
    myEmojiListPanel = new EmojiListPanel(this, style, myCategories);
    myEmojiInfoPanel = new EmojiInfoPanel(style);
    myEmojiSkinTonesModal = new JPanel();
    myEmojiSkinTonesPanel = new EmojiSkinTonesPanel(this, style);

    contentPanel.setLayout(new GridBagLayout());
    contentPanel.add(myCategoryPanel, gbc);
    contentPanel.add(mySearchField, gbc);
    contentPanel.add(myEmojiListPanel, fillerGbc);
    contentPanel.add(myEmojiInfoPanel, gbc);
    add(contentPanel);

    myEmojiSkinTonesModal.setOpaque(false);
    myEmojiSkinTonesModal.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) { selectSkinToneFromPanel(); }
    });
    add(myEmojiSkinTonesModal, JLayeredPane.MODAL_LAYER);
    add(myEmojiSkinTonesPanel, JLayeredPane.POPUP_LAYER);

    applyWindowMoveListener(myCategoryPanel);
    applyWindowMoveListener(myEmojiInfoPanel);

    myKeyboardManager = new KeyboardManager();

    selectCategory(myCategories.get(0), true);
    selectSkinToneFromPanel();
  }

  private static void applyWindowMoveListener(Component c) {
    WindowMoveListener listener = new WindowMoveListener(c);
    c.addMouseListener(listener);
    c.addMouseMotionListener(listener);
  }

  void selectCategory(EmojiCategory category, boolean scrollToCategory) {
    myCategoryPanel.selectCategory(category);
    if (scrollToCategory) {
      if (!mySearchField.getText().isEmpty()) mySearchField.setText("");
      myEmojiListPanel.selectCategory(category);
      myKeyboardManager.resetFocus();
    }
  }

  void search(@NonNls String text) {
    myEmojiListPanel.updateSearchFilter(text);
    myKeyboardManager.resetFocus();
    myKeyboardManager.emojiNavigationMode = false;
  }

  void updateEmojiInfo(Emoji emoji, EmojiSkinTone skinTone) {
    myEmojiInfoPanel.showEmojiInfo(emoji, skinTone);
  }

  void selectEmoji(@NonNls String emoji) {
    myInputCallback.accept(emoji);
  }

  boolean handleKey(int key, int modifiers) {
    return myKeyboardManager.handleKey(key, modifiers);
  }

  Object getCurrentFocusTarget() {
    return myKeyboardManager.focusTargets.get(myKeyboardManager.currentFocusTarget);
  }

  Icon getCurrentSkinToneIcon(boolean hover) {
    return myEmojiSkinTonesPanel.getCurrentItem().getIcon(hover);
  }

  void selectSkinToneFromPanel() {
    myEmojiSkinTonesModal.setVisible(false);
    myEmojiSkinTonesPanel.setVisible(false);
    myEmojiListPanel.updateSkinTone(myEmojiSkinTonesPanel.getCurrentSkinTone());
    mySearchField.update();
    EmojiService.getInstance().setCurrentSkinTone(myEmojiSkinTonesPanel.getCurrentSkinTone());
    myKeyboardManager.resetFocus();
  }

  void openSkinToneSelectionPanel() {
    myEmojiSkinTonesModal.setVisible(true);
    myEmojiSkinTonesPanel.open(mySearchField);
  }


  public static boolean isAvailable() {
    return EmojiPickerStyle.isEmojiFontAvailable();
  }

  public static JBPopup createPopup(Project project, Consumer<String> inputCallback) {
    EmojiPicker picker = new EmojiPicker();
    JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(picker, picker.mySearchField)
      .setProject(project)
      .setModalContext(false)
      .setCancelOnClickOutside(true)
      .setRequestFocus(true)
      .setCancelKeyEnabled(false)
      .setResizable(false)
      .setMovable(true)
      .setLocateWithinScreenBounds(true)
      .setMinSize(DEFAULT_SIZE.size())
      .setShowBorder(false)
      .createPopup();
    picker.myInputCallback = emoji -> {
      if (emoji != null) {
        inputCallback.accept(emoji);
        popup.closeOk(null);
      }
      else {
        popup.cancel();
      }
    };
    return popup;
  }


  private class KeyboardManager {

    private final List<Object> focusTargets = new ArrayList<>();
    private int currentFocusTarget = 0;
    private boolean emojiNavigationMode = false;

    private KeyboardManager() {
      focusTargets.add(mySearchField);
      focusTargets.add(myEmojiSkinTonesPanel);
      focusTargets.addAll(myCategories);
    }

    private void resetFocus() {
      if (currentFocusTarget != 0) {
        currentFocusTarget = 0;
        emojiNavigationMode = false;
        myCategoryPanel.repaint();
        mySearchField.repaint();
      }
    }

    private boolean handleKey(int k, int modifiers) {
      Object focus = focusTargets.get(currentFocusTarget);
      if (focus == myEmojiSkinTonesPanel) {
        if (myEmojiSkinTonesPanel.isVisible()) {
          switch (k) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_DOWN:
              myEmojiSkinTonesPanel.focusItem(k == KeyEvent.VK_DOWN ? 1 : -1);
              return true;
            case KeyEvent.VK_ENTER:
            case KeyEvent.VK_SPACE:
            case KeyEvent.VK_TAB:
              myEmojiSkinTonesPanel.setCurrentItemFromFocus();
            case KeyEvent.VK_ESCAPE:
              selectSkinToneFromPanel();
              return true;
          }
        }
        else if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) {
          openSkinToneSelectionPanel();
          myEmojiSkinTonesPanel.focusItem(1);
          return true;
        }
      }
      switch (k) {
        case KeyEvent.VK_TAB -> {
          int direction = (modifiers & InputEvent.SHIFT_DOWN_MASK) == InputEvent.SHIFT_DOWN_MASK ? -1 : 1;
          currentFocusTarget = (currentFocusTarget + direction + focusTargets.size()) % focusTargets.size();
          myCategoryPanel.repaint();
          mySearchField.repaint();
          return true;
        }
        case KeyEvent.VK_ESCAPE -> {
          myInputCallback.accept(null);
          return true;
        }
        case KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT -> {
          resetFocus();
          if (!emojiNavigationMode) {
            emojiNavigationMode = k == KeyEvent.VK_UP || k == KeyEvent.VK_DOWN ||
                                  (k == KeyEvent.VK_LEFT && mySearchField.getCaretPosition() == 0) ||
                                  (k == KeyEvent.VK_RIGHT &&
                                   mySearchField.getCaretPosition() == mySearchField.getText().length());
          }
          if (!myEmojiListPanel.hasCurrentItem()) emojiNavigationMode = false;
          if (emojiNavigationMode) {
            myEmojiListPanel.navigate(
              keyToOffset(k, KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT),
              keyToOffset(k, KeyEvent.VK_DOWN, KeyEvent.VK_UP)
            );
            return true;
          }
          else {
            return false;
          }
        }
      }
      if (focus instanceof EmojiCategory) {
        if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) {
          selectCategory((EmojiCategory)focus, true);
          return true;
        }
      }
      else if (focus == mySearchField && k == KeyEvent.VK_ENTER) {
        myEmojiListPanel.selectCurrentEmoji();
        return true;
      }
      return false;
    }

    private int keyToOffset(int key, int positive, int negative) {
      if (key == positive) return 1;
      if (key == negative) return -1;
      return 0;
    }
  }


  private static class JPanelFillLayout implements LayoutManager2 {
    @Override
    public void addLayoutComponent(Component comp, Object constraints) {}

    @Override
    public Dimension maximumLayoutSize(Container target) { return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE); }

    @Override
    public float getLayoutAlignmentX(Container target) { return 0.5f; }

    @Override
    public float getLayoutAlignmentY(Container target) { return 0.5f; }

    @Override
    public void invalidateLayout(Container target) {}

    @Override
    public void addLayoutComponent(String name, Component comp) {}

    @Override
    public void removeLayoutComponent(Component comp) {}

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      synchronized (parent.getTreeLock()) {
        return parent.getSize();
      }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }

    @Override
    public void layoutContainer(Container parent) {
      synchronized (parent.getTreeLock()) {
        Dimension size = parent.getSize();
        for (int i = 0; i < parent.getComponentCount(); i++) {
          if (parent.getComponent(i).getClass() == JPanel.class) parent.getComponent(i).setBounds(0, 0, size.width, size.height);
        }
      }
    }
  }
}