// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.speedSearch.SpeedSearchAction;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.client.ClientSystemInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.ui.speedSearch.SpeedSearchActivator;
import com.intellij.ui.speedSearch.SpeedSearchInputMethodRequests;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.text.NameUtilCore;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.*;
import java.awt.im.InputMethodRequests;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.text.CharacterIterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * Use {@link com.intellij.ui.speedSearch.SpeedSearchUtil} in renderer to highlight matching results
 */
public abstract class SpeedSearchBase<Comp extends JComponent> extends SpeedSearchSupply implements SpeedSearchActivator {
  private static final Logger LOG = Logger.getInstance(SpeedSearchBase.class);

  private static JBInsets borderInsets() {
    return JBUI.insets("SpeedSearch.borderInsets", JBUI.emptyInsets());
  }

  private static final Key<String> SEARCH_TEXT_KEY = Key.create("SpeedSearch.searchText");

  private SearchPopup mySearchPopup;
  private JLayeredPane myPopupLayeredPane;
  protected final Comp myComponent;
  private final ToolWindowManagerListener myToolWindowListener = new ToolWindowManagerListener() {
    @Override
    public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
      if (!isInsideActiveToolWindow(toolWindowManager)) {
        manageSearchPopup(null);
      }
    }

    private boolean isInsideActiveToolWindow(@NotNull ToolWindowManager toolWindowManager) {
      ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowManager.getActiveToolWindowId());
      return toolWindow != null && SwingUtilities.isDescendingFrom(myComponent, toolWindow.getComponent());
    }
  };
  private final PropertyChangeSupport myChangeSupport = new PropertyChangeSupport(this);
  private String myRecentEnteredPrefix;
  private SpeedSearchComparator myComparator = new SpeedSearchComparator(false);
  private boolean myClearSearchOnNavigateNoMatch;

  private Disposable myListenerDisposable;

  /**
   * @param sig parameter is used to avoid clash with the deprecated constructor
   */
  protected SpeedSearchBase(Comp component, @SuppressWarnings("unused") Void sig) {
    myComponent = component;
  }

  /**
   * @deprecated Please use non-deprecated constructor with combination with "setup listeners" method
   * to get the behaviour of this constructor
   */
  @Deprecated
  public SpeedSearchBase(@NotNull Comp component) {
    myComponent = component;

    setupListeners();
  }

  @Override
  public boolean supportsNavigation() {
    return true;
  }

  public void setupListeners() {
    myComponent.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentHidden(ComponentEvent event) {
        manageSearchPopup(null);
      }

      @Override
      public void componentMoved(ComponentEvent event) {
        moveSearchPopup();
      }

      @Override
      public void componentResized(ComponentEvent event) {
        moveSearchPopup();
      }
    });
    myComponent.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        if (!keepEvenWhenFocusLost()) {
          manageSearchPopup(null);
        }
      }

      @Override
      public void focusGained(FocusEvent e) {
        if (!isStickySearch()) return;
        String text = ClientProperty.get(myComponent, SEARCH_TEXT_KEY);
        if (Strings.isEmpty(text)) {
          return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
          if (myComponent.hasFocus()) {
            manageSearchPopup(createPopup(text)); // keep selection
          }
        });
      }
    });
    myComponent.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        processKeyEvent(e);
      }

      @Override
      public void keyPressed(KeyEvent e) {
        processKeyEvent(e);
      }
    });

    if (allowInputMethodsInSpeedSearch()) {
      myComponent.addInputMethodListener(new InputMethodListener() {
        @Override
        public void inputMethodTextChanged(InputMethodEvent e) {
          processInputMethodEvent(e);
        }

        @Override
        public void caretPositionChanged(InputMethodEvent e) {
          processInputMethodEvent(e);
        }
      });

      myComponent.enableInputMethods(true);
    }

    new DumbAwareAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        String prefix = getEnteredPrefix();
        if (prefix == null) return;
        String[] strings = NameUtilCore.splitNameIntoWords(prefix);
        if (strings.length == 0) return; // "__" has no words
        String last = strings[strings.length - 1];
        int i = prefix.lastIndexOf(last);
        mySearchPopup.mySearchField.setText(prefix.substring(0, i).trim());
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(isPopupActive() && Strings.isNotEmpty(getEnteredPrefix()));
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString(ClientSystemInfo.isMac() ? "meta BACK_SPACE" : "control BACK_SPACE"),
                                myComponent);

    ActionManager.getInstance().getAction(SpeedSearchAction.ID).registerCustomShortcutSet(myComponent, null);

    installSupplyTo(myComponent);
  }

  protected boolean isStickySearch() {
    return false;
  }

  protected boolean keepEvenWhenFocusLost() {
    return false;
  }

  protected boolean allowInputMethodsInSpeedSearch() {
    return true;
  }

  private InputMethodRequests myInputMethodRequests;

  @Override
  public InputMethodRequests getInputMethodRequests() {
    if (!allowInputMethodsInSpeedSearch()) {
      return null;
    }

    if (myInputMethodRequests == null) {
      myInputMethodRequests = new SpeedSearchInputMethodRequests() {
        @Override
        protected void ensurePopupIsShown() {
          if (mySearchPopup == null) {
            showPopup();
          }
        }

        @Override
        protected InputMethodRequests getDelegate() {
          JTextField field = getSearchField();
          if (field == null) {
            return null;
          } else {
            return field.getInputMethodRequests();
          }
        }
      };
    }

    return myInputMethodRequests;
  }

  @Override
  public void selectTextRange(int begin, int length) {
    var field = getSearchField();
    if (field != null) {
      field.select(begin, begin + length);
    }
  }

  public @Nullable JTextField getSearchField() {
    if (mySearchPopup != null) {
      return mySearchPopup.mySearchField;
    }
    return null;
  }

  public static boolean hasActiveSpeedSearch(JComponent component) {
    return getSupply(component) != null;
  }

  public void setClearSearchOnNavigateNoMatch(boolean clearSearchOnNavigateNoMatch) {
    myClearSearchOnNavigateNoMatch = clearSearchOnNavigateNoMatch;
  }

  @Override
  public boolean isPopupActive() {
    ThreadingAssertions.assertEventDispatchThread();
    return mySearchPopup != null && mySearchPopup.isVisible() ||
           isStickySearch() &&
           Strings.isNotEmpty(myComponent == null ? null : ClientProperty.get(myComponent, SEARCH_TEXT_KEY));
  }


  @Override
  public Iterable<TextRange> matchingFragments(@NotNull String text) {
    if (!isPopupActive()) return null;
    final SpeedSearchComparator comparator = getComparator();
    final String recentSearchText = comparator.getRecentSearchText();
    return Strings.isNotEmpty(recentSearchText) ? comparator.matchingFragments(recentSearchText, text) : null;
  }

  /**
   * Returns visual (view) selection index.
   */
  protected abstract int getSelectedIndex();

  /** @deprecated Please implement {@link #getElementCount()} and {@link #getElementAt(int)} instead. */
  @Deprecated(forRemoval = true)
  protected Object @NotNull [] getAllElements() {
    throw new UnsupportedOperationException("See `SpeedSearchBase.getElementIterator(int)` javadoc");
  }

  protected abstract @Nullable String getElementText(Object element);

  protected int getElementCount() {
    LOG.warn("Please implement getElementCount() and getElementAt(int) in " + getClass().getName());
    return getAllElements().length;
  }

  protected Object getElementAt(int viewIndex) {
    throw new UnsupportedOperationException();
  }

  /** @deprecated Please implement {@link #getElementCount()} and {@link #getElementAt(int)} instead. */
  @Deprecated(forRemoval = true)
  protected int convertIndexToModel(final int viewIndex) {
    return viewIndex;
  }

  /**
   * @param element      Element to select. Don't forget to convert model index to view index if needed (i.e. table.convertRowIndexToView(modelIndex), etc).
   * @param selectedText search text
   */
  protected abstract void selectElement(Object element, String selectedText);

  /**
   * The main method for items traversal.
   * <p>
   * Implementations can override it or use the default implementation
   * that uses {@link #getElementAt(int)} and {@link #getElementCount()} methods.
   * <p>
   * The old and now deprecated API uses {@link #getAllElements()} and {@link #convertIndexToModel(int)} methods.
   */
  protected @NotNull ListIterator<Object> getElementIterator(int startingViewIndex) {
    return new MyListIterator(this, startingViewIndex);
  }

  @Override
  public void addChangeListener(@NotNull PropertyChangeListener listener) {
    myChangeSupport.addPropertyChangeListener(listener);
  }

  @Override
  public void removeChangeListener(@NotNull PropertyChangeListener listener) {
    myChangeSupport.removePropertyChangeListener(listener);
  }

  @ApiStatus.Internal
  protected void fireStateChanged() {
    String enteredPrefix = getEnteredPrefix();
    myChangeSupport.firePropertyChange(ENTERED_PREFIX_PROPERTY_NAME, myRecentEnteredPrefix, enteredPrefix);
    myRecentEnteredPrefix = enteredPrefix;
  }

  protected boolean isMatchingElement(Object element, String pattern) {
    String str = getElementText(element);
    return str != null && compare(str, pattern);
  }

  protected boolean compare(@NotNull String text, @Nullable String pattern) {
    return pattern != null && myComparator.matchingFragments(pattern, text) != null;
  }

  public SpeedSearchComparator getComparator() {
    return myComparator;
  }

  public void setComparator(final SpeedSearchComparator comparator) {
    myComparator = comparator;
  }

  protected @Nullable Object findNextElement(String s) {
    final int selectedIndex = getSelectedIndex();
    final ListIterator<?> it = getElementIterator(selectedIndex + 1);
    final Object current;
    if (it.hasPrevious()) {
      current = it.previous();
      it.next();
    }
    else {
      current = null;
    }
    final String _s = s.trim();
    while (it.hasNext()) {
      final Object element = it.next();
      if (isMatchingElement(element, _s)) return element;
    }

    if (UISettings.getInstance().getCycleScrolling()) {
      final ListIterator<Object> i = getElementIterator(0);
      while (i.hasNext()) {
        final Object element = i.next();
        if (isMatchingElement(element, _s)) return element;
      }
    }

    return current != null && isMatchingElement(current, _s) ? current : null;
  }

  protected @Nullable Object findPreviousElement(@NotNull String s) {
    final int selectedIndex = getSelectedIndex();
    if (selectedIndex < 0) return null;
    final ListIterator<?> it = getElementIterator(selectedIndex);
    final Object current;
    if (it.hasNext()) {
      current = it.next();
      it.previous();
    }
    else {
      current = null;
    }
    final String _s = s.trim();
    while (it.hasPrevious()) {
      final Object element = it.previous();
      if (isMatchingElement(element, _s)) return element;
    }

    if (UISettings.getInstance().getCycleScrolling()) {
      final ListIterator<Object> i = getElementIterator(getElementCount());
      while (i.hasPrevious()) {
        final Object element = i.previous();
        if (isMatchingElement(element, _s)) return element;
      }
    }

    return isMatchingElement(current, _s) ? current : null;
  }

  protected @Nullable Object findElement(@NotNull String s) {
    int selectedIndex = getSelectedIndex();
    if (selectedIndex < 0) {
      selectedIndex = 0;
    }
    final ListIterator<Object> it = getElementIterator(selectedIndex);
    final String _s = s.trim();
    while (it.hasNext()) {
      final Object element = it.next();
      if (isMatchingElement(element, _s)) return element;
    }
    if (selectedIndex > 0) {
      while (it.hasPrevious()) it.previous();
      while (it.hasNext() && it.nextIndex() != selectedIndex) {
        final Object element = it.next();
        if (isMatchingElement(element, _s)) return element;
      }
    }
    return null;
  }

  private @Nullable Object findFirstElement(String s) {
    final String _s = s.trim();
    for (ListIterator<?> it = getElementIterator(0); it.hasNext(); ) {
      final Object element = it.next();
      if (isMatchingElement(element, _s)) return element;
    }
    return null;
  }

  private @Nullable Object findLastElement(String s) {
    final String _s = s.trim();
    for (ListIterator<?> it = getElementIterator(getElementCount()); it.hasPrevious(); ) {
      final Object element = it.previous();
      if (isMatchingElement(element, _s)) return element;
    }
    return null;
  }

  public void showPopup(String searchText) {
    manageSearchPopup(createPopup(searchText));
    if (mySearchPopup != null && myComponent.isDisplayable()) {
      mySearchPopup.refreshSelection();
    }
  }

  public void showPopup() {
    showPopup("");
  }

  public void hidePopup() {
    JTextField field = getSearchField();
    if (field != null) field.setText("");
    manageSearchPopup(null);
  }

  protected void processKeyEvent(KeyEvent e) {
    if (e.isAltDown() && getNavigationKeyCode(e) == 0) return;
    if (e.isShiftDown() && isNavigationKey(e.getKeyCode())) return;
    if (mySearchPopup != null) {
      mySearchPopup.processKeyEvent(e);
      return;
    }
    if (!isSpeedSearchEnabled()) return;
    if (e.getID() == KeyEvent.KEY_TYPED) {
      if (!UIUtil.isReallyTypedEvent(e)) return;

      char c = e.getKeyChar();
      if (Character.isLetterOrDigit(c) || !Character.isWhitespace(c) && SpeedSearch.PUNCTUATION_MARKS.indexOf(c) != -1) {
        showPopup(String.valueOf(c));
        e.consume();
      }
    }
  }

  public void processInputMethodEvent(InputMethodEvent e) {
    if (!isSpeedSearchEnabled()) return;

    if (mySearchPopup == null && e.getID() == InputMethodEvent.INPUT_METHOD_TEXT_CHANGED && e.getText().current() != CharacterIterator.DONE) {
      showPopup();
    }

    if (mySearchPopup != null) {
      mySearchPopup.processInputMethodEvent(e);
    }
  }

  protected @NotNull SpeedSearchBase<Comp>.SearchPopup createPopup(String s) {
    return new SearchPopup(s);
  }

  public Comp getComponent() {
    return myComponent;
  }

  @Override
  public boolean isSupported() {
    return true;
  }

  protected boolean isSpeedSearchEnabled() {
    return true;
  }

  @Override
  @ApiStatus.Internal
  public boolean isAvailable() {
    return isSpeedSearchEnabled();
  }

  @Override
  public boolean isActive() {
    return isPopupActive();
  }

  @Override
  public @Nullable JComponent getTextField() {
    return getSearchField();
  }

  @Override
  public void activate() {
    showPopup();
  }

  @Override
  public @Nullable @NlsSafe String getEnteredPrefix() {
    return mySearchPopup != null ? mySearchPopup.mySearchField.getText() : null;
  }

  @Override
  public void refreshSelection() {
    if (mySearchPopup != null) mySearchPopup.refreshSelection();
  }

  @Override
  public void findAndSelectElement(@NotNull String searchQuery) {
    if (mySearchPopup != null) {
      // If there's a popup showing, we must let it handle selection, because
      // it also updates its own state (error / not error) in the process.
      mySearchPopup.updateSelection(findElement(searchQuery), searchQuery);
    }
    else {
      selectElement(findElement(searchQuery), searchQuery);
    }
  }

  public boolean adjustSelection(int keyCode, @NotNull String searchQuery) {
    if (isUpDownHomeEnd(keyCode)) {
      UIEventLogger.IncrementalSearchNextPrevItemSelected.log(myComponent.getClass());
      Object element = findTargetElement(keyCode, searchQuery);
      if (element != null) {
        selectElement(element, searchQuery);
        return true;
      }
    }
    return false;
  }

  private @Nullable Object findTargetElement(int keyCode, @NotNull String searchPrefix) {
    if (keyCode == KeyEvent.VK_UP) {
      return findPreviousElement(searchPrefix);
    }
    else if (keyCode == KeyEvent.VK_DOWN) {
      return findNextElement(searchPrefix);
    }
    else if (keyCode == KeyEvent.VK_HOME) {
      return findFirstElement(searchPrefix);
    }
    else {
      assert keyCode == KeyEvent.VK_END;
      return findLastElement(searchPrefix);
    }
  }

  private void mouseButtonInTheSearchFieldPressed() {
    if (keepEvenWhenFocusLost() && myComponent.isVisible() && !myComponent.isFocusOwner()) {
      myComponent.requestFocus();
    }
  }


  protected class SearchPopup extends JPanel {
    protected final @NotNull SearchField mySearchField;
    private String myLastPattern = "";
    private boolean myFiringCallback = false;

    protected SearchPopup(String initialString) {
      mySearchField = new SearchField();
      mySearchField.setBorder(null);
      mySearchField.setBackground(BACKGROUND_COLOR);
      mySearchField.setForeground(FOREGROUND_COLOR);

      mySearchField.setDocument(new PlainDocument() {
        @Override
        public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
          String oldText;
          try {
            oldText = getText(0, getLength());
          }
          catch (BadLocationException e1) {
            oldText = "";
          }

          String newText = oldText.substring(0, offs) + str + oldText.substring(offs);
          super.insertString(offs, str, a);
          handleInsert(newText);
          updateSelection(findElement(newText), mySearchField.getText());
        }
      });

      Border lineBorder = JBUI.Borders.customLine(BORDER_COLOR);
      if (ExperimentalUI.isNewUI()) {
        setBorder(JBUI.Borders.compound(lineBorder, new EmptyBorder(borderInsets())));
      }
      else {
        setBorder(lineBorder);
      }

      setBackground(BACKGROUND_COLOR);
      setLayout(new BorderLayout());
      add(mySearchField, BorderLayout.CENTER);
      mySearchField.setText(initialString);

      mySearchField.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          super.mousePressed(e);
          if (e.getButton() == MouseEvent.BUTTON1) mouseButtonInTheSearchFieldPressed();
        }
      });

      updateLastPattern();
    }

    private void updateLastPattern() {
      String pattern = Strings.notNullize(mySearchField.getText());
      if (!pattern.equals(myLastPattern)) {
        myLastPattern = pattern;
        onSearchFieldUpdated(pattern);
      }
    }

    protected void handleInsert(String newText) {
      if (findElement(newText) == null) {
        mySearchField.setForeground(ERROR_FOREGROUND_COLOR);
      }
      else {
        mySearchField.setForeground(FOREGROUND_COLOR);
      }
    }

    @Override
    public void processKeyEvent(KeyEvent e) {
      mySearchField.processKeyEvent(e);
      if (e.isConsumed()) {
        updateLastPattern();
        String s = mySearchField.getText();
        Object element;

        int navKeyCode = getNavigationKeyCode(e);
        if (navKeyCode != 0) {
          element = findTargetElement(navKeyCode, s);
          if (myClearSearchOnNavigateNoMatch && element == null) {
            manageSearchPopup(null);
            element = findTargetElement(navKeyCode, "");
          }
        }
        else {
          UIEventLogger.IncrementalSearchKeyTyped.log(myComponent.getClass());
          element = findElement(s);
        }
        updateSelection(element, mySearchField.getText());
      }
    }

    @Override
    public void processInputMethodEvent(InputMethodEvent e) {
      mySearchField.processInputMethodEvent(e);
      if (e.isConsumed()) {
        updateLastPattern();
        String s = mySearchField.getText();
        updateSelection(findElement(s), s);
      }
    }

    void refreshSelection() {
      findAndSelectElement(mySearchField.getText());
    }

    private void updateSelection(Object element, String selectedText) {
      if (element != null) {
        selectElement(element, selectedText);
        mySearchField.setForeground(FOREGROUND_COLOR);
      }
      else {
        mySearchField.setForeground(ERROR_FOREGROUND_COLOR);
      }
      if (mySearchPopup != null) {
        mySearchPopup.setSize(mySearchPopup.getPreferredSize());
        mySearchPopup.validate();
      }

      if (myFiringCallback) return;
      myFiringCallback = true;
      try {
        fireStateChanged();
      }
      finally {
        myFiringCallback = false;
      }
    }
  }

  private static int getNavigationKeyCode(KeyEvent e) {
    KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);
    if (isUpDownHomeEnd(e.getKeyCode())) {
      return e.getKeyCode();
    }
    KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager != null) {
      @NotNull String @NotNull [] actionIds = keymapManager.getActiveKeymap().getActionIds(keyStroke);
      for (String id : actionIds) {
        switch (id) {
          case IdeActions.ACTION_EDITOR_MOVE_CARET_UP, IdeActions.ACTION_FIND_PREVIOUS -> {
            return KeyEvent.VK_UP;
          }
          case IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN, IdeActions.ACTION_FIND_NEXT -> {
            return KeyEvent.VK_DOWN;
          }
          case IdeActions.ACTION_EDITOR_MOVE_LINE_START -> {
            return KeyEvent.VK_HOME;
          }
          case IdeActions.ACTION_EDITOR_MOVE_LINE_END -> {
            return KeyEvent.VK_END;
          }
        }
      }
    }
    return 0;
  }

  protected void onSearchFieldUpdated(String pattern) {
    if (Strings.isEmpty(pattern) && Registry.is("ide.speed.search.close.when.empty")) {
      hidePopup();
    }
  }

  protected final class SearchField extends ExtendableTextField {
    SearchField() {
      setFocusable(false);
      ExtendableTextField.Extension leftExtension = new Extension() {
        @Override
        public Icon getIcon(boolean hovered) {
          return AllIcons.Actions.Search;
        }

        @Override
        public boolean isIconBeforeText() {
          return true;
        }

        @Override
        public int getIconGap() {
          return JBUIScale.scale(10);
        }
      };

      addExtension(leftExtension);

      Extension rightExtension = createSearchFieldExtension();
      if (rightExtension != null) {
        addExtension(rightExtension);
      }
      getEmptyText().setText(ApplicationBundle.message("editorsearch.search.hint"));
    }

    @Override
    public void addNotify() {
      super.addNotify();
      getCaret().setVisible(true); // we still want it blinking even though the field isn't focusable
    }

    @Override
    public void removeNotify() {
      getCaret().setVisible(false); // doesn't happen automatically for some reason and causes Timer leaks
      super.removeNotify();
    }

    @Override
    public void setForeground(Color color) {
      super.setForeground(color);
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension dim = super.getPreferredSize();
      Insets m = getMargin();
      var fm = getFontMetrics(getFont());
      var text = getText();
      var emptyText = getEmptyText().getText();
      dim.width = Math.max(fm.stringWidth(text), fm.stringWidth(emptyText)) + 10 + m.left + m.right;
      return dim;
    }

    /**
     * I made this method public in order to be able to call it from the outside.
     * This is needed for delegating calls.
     */
    @Override
    public void processKeyEvent(KeyEvent e) {
      int i = e.getKeyCode();
      if (i == KeyEvent.VK_BACK_SPACE && getDocument().getLength() == 0) {
        e.consume();
        return;
      }
      if (
        i == KeyEvent.VK_ENTER ||
        i == KeyEvent.VK_PAGE_UP ||
        i == KeyEvent.VK_PAGE_DOWN ||
        i == KeyEvent.VK_LEFT ||
        i == KeyEvent.VK_RIGHT
      ) {
        if (!isStickySearch()) {
          manageSearchPopup(null);
        }
        return;
      }
      if (i == KeyEvent.VK_ESCAPE) {
        hidePopup();
        e.consume();
        return;
      }

      if (isUpDownHomeEnd(i)) {
        e.consume();
        return;
      }

      if (e.getID() == KeyEvent.KEY_TYPED && !UIUtil.isReallyTypedEvent(e)) {
        // Stuff like Ctrl+N / Ctrl+P is processed on KEY_PRESSED, and the subsequent KEY_TYPED screws it up.
        return;
      }

      super.processKeyEvent(e);

      if (!e.isConsumed() && getNavigationKeyCode(e) != 0) {
        // Some navigation action shortcuts aren't consumed by the field, e.g. if these are custom shortcuts the text field doesn't understand.
        e.consume();
      }

      if (i == KeyEvent.VK_BACK_SPACE) {
        e.consume();
      }
    }

    @Override
    public void processInputMethodEvent(InputMethodEvent e) {
      super.processInputMethodEvent(e);
    }

    @Override
    protected void processFocusEvent(FocusEvent e) {
      super.processFocusEvent(e);
      if (isShowing()) {
        getCaret().setVisible(true); // we want to keep it blinking even if the focus is lost
        // (never happens in practice, though, as the field isn't focusable, so this is just a precaution)
      }
    }
  }

  /**
   * Creates an additional extension.
   * SpeedSearch calls this method when creating the search text field.
   * If the result of this method is not null, the caller adds it as a serach text field extension.
   *
   * @return an extension, or null.
   */
  protected @Nullable ExtendableTextComponent.Extension createSearchFieldExtension() {
    return null;
  }

  private static boolean isUpDownHomeEnd(int keyCode) {
    return keyCode == KeyEvent.VK_HOME || keyCode == KeyEvent.VK_END || keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN;
  }

  private static boolean isPgUpPgDown(int keyCode) {
    return keyCode == KeyEvent.VK_PAGE_UP || keyCode == KeyEvent.VK_PAGE_DOWN;
  }

  private static boolean isNavigationKey(int keyCode) {
    return isPgUpPgDown(keyCode) || isUpDownHomeEnd(keyCode);
  }


  private void manageSearchPopup(@Nullable SearchPopup searchPopup) {
    if (mySearchPopup != null) {
      Project project = CommonDataKeys.PROJECT.getData(
        DataManager.getInstance().getDataContext(myComponent.getRootPane()));
      UIEventLogger.IncrementalSearchCancelled.log(project, myComponent.getClass());
      if (myPopupLayeredPane != null) {
        myPopupLayeredPane.remove(mySearchPopup);
        myPopupLayeredPane.validate();
        myPopupLayeredPane.repaint();
        myPopupLayeredPane = null;
      }

      if (myListenerDisposable != null) {
        Disposer.dispose(myListenerDisposable);
        myListenerDisposable = null;
      }
      if (isStickySearch()) {
        myComponent.putClientProperty(SEARCH_TEXT_KEY, Strings.nullize(getEnteredPrefix()));
      }
    }
    else if (searchPopup != null) {
      Project project = CommonDataKeys.PROJECT.getData(
        DataManager.getInstance().getDataContext(myComponent.getRootPane()));
      FeatureUsageTracker.getInstance().triggerFeatureUsed("ui.tree.speedsearch");
      UIEventLogger.IncrementalSearchActivated.log(project, myComponent.getClass());
    }

    mySearchPopup = myComponent.isShowing() ? searchPopup : null;

    fireStateChanged();

    //select here!

    if (mySearchPopup == null || !myComponent.isDisplayable()) return;

    JRootPane rootPane = myComponent.getRootPane();
    Project project = ProjectUtil.getProjectForComponent(rootPane);
    if (project != null && !project.isDefault() && !project.isDisposed()) {
      myListenerDisposable = Disposer.newDisposable();
      project.getMessageBus().connect(myListenerDisposable).subscribe(ToolWindowManagerListener.TOPIC, myToolWindowListener);
    }
    myPopupLayeredPane = rootPane == null ? null : rootPane.getLayeredPane();
    if (myPopupLayeredPane == null) {
      LOG.error(this + " in " + myComponent);
      return;
    }
    myPopupLayeredPane.add(mySearchPopup, JLayeredPane.POPUP_LAYER);
    moveSearchPopup();
  }

  private void moveSearchPopup() {
    if (myComponent == null || mySearchPopup == null || myPopupLayeredPane == null) return;
    Point lPaneP = myPopupLayeredPane.getLocationOnScreen();
    Point componentP = getComponentLocationOnScreen();
    Rectangle r = getComponentVisibleRect();
    Dimension prefSize = mySearchPopup.getPreferredSize();
    Window window = (Window)SwingUtilities.getAncestorOfClass(Window.class, myComponent);
    Point windowP;
    if (window instanceof JDialog) {
      windowP = ((JDialog)window).getContentPane().getLocationOnScreen();
    }
    else if (window instanceof JFrame) {
      windowP = ((JFrame)window).getContentPane().getLocationOnScreen();
    }
    else {
      windowP = window.getLocationOnScreen();
    }
    int y = r.y + componentP.y - lPaneP.y - prefSize.height;
    y = Math.max(y, windowP.y - lPaneP.y);
    Point location = new Point(componentP.x - lPaneP.x + r.x, y);

    if (Registry.is("ide.speed.search.allow.custom.location")) {
      SpeedSearchLocator locator = DataManager.getInstance().getDataContext(myComponent).getData(PlatformDataKeys.SPEED_SEARCH_LOCATOR);
      if (locator != null) {
        RelativeRectangle relativeRectangle = locator.getSizeAndLocation(myComponent);
        if (relativeRectangle != null) {
          Rectangle rect = relativeRectangle.getRectangleOn(myPopupLayeredPane);
          location = rect.getLocation();
          prefSize = rect.getSize();
          mySearchPopup.setPreferredSize(prefSize);
        }
      }
    }

    mySearchPopup.setLocation(location);
    mySearchPopup.setSize(prefSize);
    mySearchPopup.setVisible(true);
    mySearchPopup.validate();
  }

  protected Rectangle getComponentVisibleRect() {
    return myComponent.getVisibleRect();
  }

  protected Point getComponentLocationOnScreen() {
    return myComponent.getLocationOnScreen();
  }

  // TODO remove after the transition period
  private final boolean myElementAtImplemented;

  {
    boolean elementAtImplemented;
    try {
      elementAtImplemented = ReflectionUtil.getMethodDeclaringClass(getClass(), "getElementAt", Integer.TYPE) != SpeedSearchBase.class;
    }
    catch (Exception ex) {
      elementAtImplemented = false;
    }
    myElementAtImplemented = elementAtImplemented;
    boolean elementIteratorImplemented = false;
    boolean elementCountImplemented = false;
    try {
      elementCountImplemented = ReflectionUtil.getMethodDeclaringClass(getClass(), "getElementCount") != SpeedSearchBase.class;
      elementIteratorImplemented =
        ReflectionUtil.getMethodDeclaringClass(getClass(), "getElementIterator", Integer.TYPE) != SpeedSearchBase.class;
    }
    catch (Exception ignore) {
    }
    if (!elementIteratorImplemented && !(elementAtImplemented && elementCountImplemented)) {
      LOG.warn("Please implement getElementAt(int)" +
               (elementCountImplemented ? "" : " and getElementCount()") + " in " + getClass().getName());
    }
  }

  private static final class MyListIterator implements ListIterator<Object> {
    private final SpeedSearchBase<?> mySpeedSearch;
    private int myCurrentIndex;
    private final int myElementCount;

    private Object[] myElements;

    MyListIterator(@NotNull SpeedSearchBase<?> speedSearch, int startIndex) {
      mySpeedSearch = speedSearch;
      myCurrentIndex = startIndex;

      if (!mySpeedSearch.myElementAtImplemented) {
        myElements = speedSearch.getAllElements();
        myElementCount = myElements.length;
      }
      else {
        myElementCount = speedSearch.getElementCount();
      }

      if (startIndex < 0 || startIndex > myElementCount) {
        throw new IndexOutOfBoundsException("Index: " + startIndex + " in: " + speedSearch.getClass());
      }
    }

    @Override
    public boolean hasPrevious() {
      return myCurrentIndex != 0;
    }

    @Override
    public Object previous() {
      int i = myCurrentIndex - 1;
      if (i < 0) throw new NoSuchElementException();
      Object previous = getElementAt(i);
      myCurrentIndex = i;
      return previous;
    }

    private Object getElementAt(int i) {
      if (mySpeedSearch.myElementAtImplemented) {
        return mySpeedSearch.getElementAt(i);
      }
      int index = mySpeedSearch.convertIndexToModel(i);
      return myElements[index];
    }

    @Override
    public int nextIndex() {
      return myCurrentIndex;
    }

    @Override
    public int previousIndex() {
      return myCurrentIndex - 1;
    }

    @Override
    public boolean hasNext() {
      return myCurrentIndex != myElementCount;
    }

    @Override
    public Object next() {
      if (myCurrentIndex + 1 > myElementCount) throw new NoSuchElementException();
      return getElementAt(myCurrentIndex++);
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("Not implemented in: " + getClass().getCanonicalName());
    }

    @Override
    public void set(Object o) {
      throw new UnsupportedOperationException("Not implemented in: " + getClass().getCanonicalName());
    }

    @Override
    public void add(Object o) {
      throw new UnsupportedOperationException("Not implemented in: " + getClass().getCanonicalName());
    }
  }
}