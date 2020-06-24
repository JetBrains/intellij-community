// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.list;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.ui.ListActions;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SeparatorWithText;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.ClosableByLeftArrow;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.popup.NextStepHandler;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class ListPopupImpl extends WizardPopup implements ListPopup, NextStepHandler {
  private static final Logger LOG = Logger.getInstance(ListPopupImpl.class);

  private MyList myList;

  private MyMouseMotionListener myMouseMotionListener;
  private MyMouseListener myMouseListener;

  private ListPopupModel myListModel;

  private int myIndexForShowingChild = -1;
  private int myMaxRowCount = 30;
  private boolean myAutoHandleBeforeShow;

  /**
   * @deprecated use {@link #ListPopupImpl(Project, ListPopupStep)} + {@link #setMaxRowCount(int)}
   */
  @Deprecated
  public ListPopupImpl(@NotNull ListPopupStep aStep, int maxRowCount) {
    this(aStep);
    setMaxRowCount(maxRowCount);
  }

  /**
   * @deprecated use {@link #ListPopupImpl(Project, ListPopupStep)}
   */
  @Deprecated
  public ListPopupImpl(@NotNull ListPopupStep aStep) {
    this(CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext()), null, aStep, null);
  }

  public ListPopupImpl(@Nullable Project project,
                       @NotNull ListPopupStep aStep) {
    this(project, null, aStep, null);
  }

  public ListPopupImpl(@Nullable Project project,
                       @Nullable WizardPopup aParent,
                       @NotNull ListPopupStep aStep,
                       Object parentValue) {
    super(project, aParent, aStep);
    setParentValue(parentValue);
    replacePasteAction();
  }

  public void setMaxRowCount(int maxRowCount) {
    if (maxRowCount <= 0) return;
    myMaxRowCount = maxRowCount;
  }

  public void showUnderneathOfLabel(@NotNull JLabel label) {
    int offset = -UIUtil.getListCellHPadding() - UIUtil.getListViewportPadding().left;
    if (label.getIcon() != null) {
      offset += label.getIcon().getIconWidth() + label.getIconTextGap();
    }
    show(new RelativePoint(label, new Point(offset, label.getHeight() + 1)));
  }

  protected ListPopupModel getListModel() {
    return myListModel;
  }

  @Override
  protected boolean beforeShow() {
    myList.addMouseMotionListener(myMouseMotionListener);
    myList.addMouseListener(myMouseListener);
    myList.setVisibleRowCount(myMaxRowCount);

    boolean shouldShow = super.beforeShow();
    if (myAutoHandleBeforeShow) {
      final boolean toDispose = tryToAutoSelect(true);
      shouldShow &= !toDispose;
    }

    return shouldShow;
  }

  @Override
  public void goBack() {
    myList.clearSelection();
    super.goBack();
  }

  @Override
  protected void afterShowSync() {
    super.afterShowSync();
    tryToAutoSelect(false);
  }

  private boolean tryToAutoSelect(boolean handleFinalChoices) {
    ListPopupStep<Object> listStep = getListStep();
    boolean selected = false;
    if (listStep instanceof MultiSelectionListPopupStep<?>) {
      int[] indices = ((MultiSelectionListPopupStep)listStep).getDefaultOptionIndices();
      if (indices.length > 0) {
        ScrollingUtil.ensureIndexIsVisible(myList, indices[0], 0);
        myList.setSelectedIndices(indices);
        selected = true;
      }
    }
    else {
      final int defaultIndex = listStep.getDefaultOptionIndex();
      if (isSelectableAt(defaultIndex)) {
        ScrollingUtil.selectItem(myList, defaultIndex);
        selected = true;
      }
    }

    if (!selected) {
      selectFirstSelectableItem();
    }

    if (listStep.isAutoSelectionEnabled()) {
      if (!isVisible() && getSelectableCount() == 1) {
        return _handleSelect(handleFinalChoices, null);
      } else if (isVisible() && hasSingleSelectableItemWithSubmenu()) {
        return _handleSelect(handleFinalChoices, null);
      }
    }

    return false;
  }

  protected boolean shouldUseStatistics() {
    return true;
  }

  private boolean autoSelectUsingStatistics() {
    final String filter = getSpeedSearch().getFilter();
    if (!StringUtil.isEmpty(filter)) {
      int maxUseCount = -1;
      int mostUsedValue = -1;
      int elementsCount = myListModel.getSize();
      for (int i = 0; i < elementsCount; i++) {
        Object value = myListModel.getElementAt(i);
        if (!isSelectable(value)) continue;
        final String text = getListStep().getTextFor(value);
        final int count =
            StatisticsManager.getInstance().getUseCount(new StatisticsInfo("#list_popup:" + myStep.getTitle() + "#" + filter, text));
        if (count > maxUseCount) {
          maxUseCount = count;
          mostUsedValue = i;
        }
      }

      if (mostUsedValue > 0) {
        ScrollingUtil.selectItem(myList, mostUsedValue);
        return true;
      }
    }

    return false;
  }

  private void selectFirstSelectableItem() {
    for (int i = 0; i < myListModel.getSize(); i++) {
      if (getListStep().isSelectable(myListModel.getElementAt(i))) {
        myList.setSelectedIndex(i);
        break;
      }
    }
  }

  private boolean hasSingleSelectableItemWithSubmenu() {
    boolean oneSubmenuFound = false;
    int countSelectables = 0;
    for (int i = 0; i < myListModel.getSize(); i++) {
      Object elementAt = myListModel.getElementAt(i);
      if (getListStep().isSelectable(elementAt) ) {
        countSelectables ++;
        if (getStep().hasSubstep(elementAt)) {
          if (oneSubmenuFound) {
            return false;
          }
          oneSubmenuFound = true;
        }
      }
    }
    return oneSubmenuFound && countSelectables == 1;
  }

  private int getSelectableCount() {
    int count = 0;
    for (int i = 0; i < myListModel.getSize(); i++) {
      final Object each = myListModel.getElementAt(i);
      if (getListStep().isSelectable(each)) {
        count++;
      }
    }

    return count;
  }

  public JList getList() {
    return myList;
  }

  @Override
  protected JComponent createContent() {
    myMouseMotionListener = new MyMouseMotionListener();
    myMouseListener = new MyMouseListener();

    ListPopupStep<Object> step = getListStep();
    myListModel = new ListPopupModel(this, getSpeedSearch(), step);
    myList = new MyList();
    if (myStep.getTitle() != null) {
      myList.getAccessibleContext().setAccessibleName(myStep.getTitle());
    }
    if (step instanceof ListPopupStepEx) {
      ((ListPopupStepEx)step).setEmptyText(myList.getEmptyText());
    }

    myList.setSelectionModel(new MyListSelectionModel());

    selectFirstSelectableItem();
    Insets padding = UIUtil.getListViewportPadding();
    myList.setBorder(new EmptyBorder(padding));

    ScrollingUtil.installActions(myList);

    myList.setCellRenderer(getListElementRenderer());

    registerAction("handleSelection1", KeyEvent.VK_ENTER, 0, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        handleSelect(true);
      }
    });

    myList.getActionMap().put(ListActions.Right.ID, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        handleSelect(false);
      }
    });

    myList.getActionMap().put(ListActions.Left.ID, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (isClosableByLeftArrow()) {
          goBack();
        }
      }
    });


    myList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    return myList;
  }

  private boolean isMultiSelectionEnabled() {
    return getListStep() instanceof MultiSelectionListPopupStep<?>;
  }

  private boolean isClosableByLeftArrow() {
    return getParent() != null || myStep instanceof ClosableByLeftArrow;
  }

  @Override
  protected ActionMap getActionMap() {
    return myList.getActionMap();
  }

  @Override
  protected InputMap getInputMap() {
    return myList.getInputMap();
  }

  protected ListCellRenderer getListElementRenderer() {
    return new PopupListElementRenderer(this);
  }

  @Override
  public ListPopupStep<Object> getListStep() {
    return (ListPopupStep<Object>) myStep;
  }

  @Override
  public void dispose() {
    myList.removeMouseMotionListener(myMouseMotionListener);
    myList.removeMouseListener(myMouseListener);
    super.dispose();
  }

  protected int getSelectedIndex() {
    return myList.getSelectedIndex();
  }

  protected Rectangle getCellBounds(int i) {
    return myList.getCellBounds(i, i);
  }

  @Override
  public void disposeChildren() {
    setIndexForShowingChild(-1);
    super.disposeChildren();
  }

  @Override
  protected void onAutoSelectionTimer() {
    if (myList.getModel().getSize() > 0 && !myList.isSelectionEmpty() ) {
      handleSelect(false);
    }
    else {
      disposeChildren();
      setIndexForShowingChild(-1);
    }
  }

  @Override
  public void handleSelect(boolean handleFinalChoices) {
    _handleSelect(handleFinalChoices, null);
  }

  @Override
  public void handleSelect(boolean handleFinalChoices, InputEvent e) {
    _handleSelect(handleFinalChoices, e);
  }

  private boolean _handleSelect(final boolean handleFinalChoices, @Nullable InputEvent e) {
    if (myList.getSelectedIndex() == -1) return false;

    if (getSpeedSearch().isHoldingFilter() && myList.getModel().getSize() == 0) return false;

    if (myList.getSelectedIndex() == getIndexForShowingChild()) {
      if (myChild != null && !myChild.isVisible()) setIndexForShowingChild(-1);
      return false;
    }

    final Object[] selectedValues = myList.getSelectedValues();
    final ListPopupStep<Object> listStep = getListStep();
    if (!listStep.isSelectable(selectedValues[0])) return false;

    if ((listStep instanceof MultiSelectionListPopupStep<?> && !((MultiSelectionListPopupStep<Object>)listStep).hasSubstep(Arrays.asList(selectedValues))
         || !listStep.hasSubstep(selectedValues[0])) && !handleFinalChoices) return false;

    disposeChildren();

    if (myListModel.getSize() == 0) {
      setFinalRunnable(myStep.getFinalRunnable());
      setOk(true);
      disposeAllParents(e);
      setIndexForShowingChild(-1);
      return true;
    }

    valuesSelected(selectedValues);

    AtomicBoolean insideOnChosen = new AtomicBoolean(true);
    ApplicationManager.getApplication().invokeLater(() -> {
      if (insideOnChosen.get()) {
        LOG.error("Showing dialogs from popup onChosen can result in focus issues. Please put the handler into BaseStep.doFinalStep or PopupStep.getFinalRunnable.");
      }
    }, ModalityState.any());

    final PopupStep nextStep;
    try {
      if (listStep instanceof MultiSelectionListPopupStep<?>) {
        nextStep = ((MultiSelectionListPopupStep<Object>)listStep).onChosen(Arrays.asList(selectedValues), handleFinalChoices);
      }
      else if (e != null && listStep instanceof ListPopupStepEx<?>) {
        nextStep = ((ListPopupStepEx<Object>)listStep).onChosen(selectedValues[0], handleFinalChoices, e.getModifiers());
      }
      else {
        nextStep = listStep.onChosen(selectedValues[0], handleFinalChoices);
      }
    }
    finally {
      insideOnChosen.set(false);
    }
    return handleNextStep(nextStep, selectedValues.length == 1 ? selectedValues[0] : null, e);
  }

  private void valuesSelected(final Object[] values) {
    if (shouldUseStatistics()) {
      final String filter = getSpeedSearch().getFilter();
      if (!StringUtil.isEmpty(filter)) {
        for (Object value : values) {
          final String text = getListStep().getTextFor(value);
          StatisticsManager.getInstance().incUseCount(new StatisticsInfo("#list_popup:" + getListStep().getTitle() + "#" + filter, text));
        }
      }
    }
  }

  @Override
  public void handleNextStep(final PopupStep nextStep, Object parentValue) {
    handleNextStep(nextStep, parentValue, null);
  }

  public boolean handleNextStep(final PopupStep nextStep, Object parentValue, InputEvent e) {
    if (nextStep != PopupStep.FINAL_CHOICE) {
      final Point point = myList.indexToLocation(myList.getSelectedIndex());
      SwingUtilities.convertPointToScreen(point, myList);
      myChild = createPopup(this, nextStep, parentValue);
      if (myChild instanceof ListPopupImpl) {
        for (ListSelectionListener listener : myList.getListSelectionListeners()) {
          ((ListPopupImpl)myChild).addListSelectionListener(listener);
        }
      }
      final JComponent container = getContent();

      int y = point.y;
      if (parentValue != null && getListModel().isSeparatorAboveOf(parentValue)) {
        SeparatorWithText swt = new SeparatorWithText();
        swt.setCaption(getListModel().getCaptionAboveOf(parentValue));
        y += swt.getPreferredSize().height - 1;
      }

      myChild.show(container, container.getLocationOnScreen().x + container.getWidth() - STEP_X_PADDING, y, true);
      setIndexForShowingChild(myList.getSelectedIndex());
      return false;
    }
    else {
      setOk(true);
      setFinalRunnable(myStep.getFinalRunnable());
      disposeAllParents(e);
      setIndexForShowingChild(-1);
      return true;
    }
  }


  @Override
  public void addListSelectionListener(ListSelectionListener listSelectionListener) {
    myList.addListSelectionListener(listSelectionListener);
  }

  private class MyMouseMotionListener extends MouseMotionAdapter {
    private int myLastSelectedIndex = -2;
    private Point myLastMouseLocation;

    private boolean isMouseMoved(Point location) {
      if (myLastMouseLocation == null) {
        myLastMouseLocation = location;
        return false;
      }
      return !myLastMouseLocation.equals(location);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (!isMouseMoved(e.getLocationOnScreen())) return;

      Point point = e.getPoint();
      int index = myList.locationToIndex(point);

      if (isSelectableAt(index)) {
        if (index != myLastSelectedIndex) {
          if (!isMultiSelectionEnabled() || !UIUtil.isSelectionButtonDown(e) && myList.getSelectedIndices().length <= 1) {
            myList.setSelectedIndex(index);
          }
          restartTimer();
          myLastSelectedIndex = index;
        }
      }
      else {
        myList.clearSelection();
        myLastSelectedIndex = -1;
      }

      notifyParentOnChildSelection();
    }
  }

  protected boolean isActionClick(MouseEvent e) {
    return UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED, true);
  }

  public Object[] getSelectedValues() {
    return myList.getSelectedValues();
  }

  private class MyMouseListener extends MouseAdapter {

    @Override
    public void mouseReleased(MouseEvent e) {
      if (!isActionClick(e) || isMultiSelectionEnabled() && UIUtil.isSelectionButtonDown(e)) return;
      IdeEventQueue.getInstance().blockNextEvents(e); // sometimes, after popup close, MOUSE_RELEASE event delivers to other components
      final Object selectedValue = myList.getSelectedValue();
      final ListPopupStep<Object> listStep = getListStep();
      handleSelect(handleFinalChoices(e, selectedValue, listStep), e);
      stopTimer();
    }
  }

  protected boolean handleFinalChoices(MouseEvent e, Object selectedValue, ListPopupStep<Object> listStep) {
    return selectedValue == null || !listStep.hasSubstep(selectedValue) || !listStep.isSelectable(selectedValue) || !isOnNextStepButton(e);
  }

  private boolean isOnNextStepButton(MouseEvent e) {
    final int index = myList.getSelectedIndex();
    final Rectangle bounds = myList.getCellBounds(index, index);
    if (bounds != null) {
      JBInsets.removeFrom(bounds, UIUtil.getListCellPadding());
    }
    final Point point = e.getPoint();
    return bounds != null && point.getX() > bounds.width + bounds.getX() - AllIcons.Icons.Ide.NextStep.getIconWidth();
  }

  @Override
  protected void process(KeyEvent aEvent) {
    myList.processKeyEvent(aEvent);
  }

  private int getIndexForShowingChild() {
    return myIndexForShowingChild;
  }

  private void setIndexForShowingChild(int aIndexForShowingChild) {
    myIndexForShowingChild = aIndexForShowingChild;
  }

  private class MyList extends JBList implements DataProvider {
    MyList() {
      super(myListModel);
      HintUpdateSupply.installSimpleHintUpdateSupply(this);
    }

    @Override
    public void processKeyEvent(KeyEvent e) {
      e.setSource(this);
      super.processKeyEvent(e);
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
      if (!isMultiSelectionEnabled() &&
          (e.getModifiers() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) != 0) {
        // do not toggle selection with ctrl+click event in single-selection mode
        e.consume();
      }
      if (UIUtil.isActionClick(e, MouseEvent.MOUSE_PRESSED) && isOnNextStepButton(e)) {
        e.consume();
      }

      boolean isClick = UIUtil.isActionClick(e, MouseEvent.MOUSE_PRESSED) || UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED);
      if (!isClick || myList.locationToIndex(e.getPoint()) == myList.getSelectedIndex()) {
        super.processMouseEvent(e);
      }
    }

    @Override
    public Object getData(@NotNull String dataId) {
       if (PlatformDataKeys.SELECTED_ITEM.is(dataId)){
        return myList.getSelectedValue();
      }
      if (PlatformDataKeys.SELECTED_ITEMS.is(dataId)){
         return myList.getSelectedValues();
      }
      if (PlatformDataKeys.SPEED_SEARCH_COMPONENT.is(dataId)) {
        if (mySpeedSearchPatternField != null && mySpeedSearchPatternField.isVisible()) {
          return mySpeedSearchPatternField;
        }
      }
      return null;
    }
  }

  private final class MyListSelectionModel extends DefaultListSelectionModel {
    private MyListSelectionModel() {
      setSelectionMode(isMultiSelectionEnabled() ? MULTIPLE_INTERVAL_SELECTION : SINGLE_SELECTION);
    }

    @Override
    public void setSelectionInterval(int index0, int index1) {
      if (getSelectionMode() == SINGLE_SELECTION) {
        if (index0 > getLeadSelectionIndex()) {
          for (int i = index0; i < myListModel.getSize(); i++) {
            if (getListStep().isSelectable(myListModel.getElementAt(i))) {
              super.setSelectionInterval(i, i);
              break;
            }
          }
        }
        else {
          for (int i = index0; i >= 0; i--) {
            if (getListStep().isSelectable(myListModel.getElementAt(i))) {
              super.setSelectionInterval(i, i);
              break;
            }
          }
        }
      }
      else {
        super.setSelectionInterval(index0, index1); // TODO: support when needed
      }
    }
  }

  @Override
  protected void onSpeedSearchPatternChanged() {
    myListModel.refilter();
    if (myListModel.getSize() > 0) {
      if (!(shouldUseStatistics() && autoSelectUsingStatistics())) {
        selectBestMatch();
      }
    }
  }

  private void selectBestMatch() {
    int fullMatchIndex = myListModel.getClosestMatchIndex();
    if (fullMatchIndex != -1 && isSelectableAt(fullMatchIndex)) {
      myList.setSelectedIndex(fullMatchIndex);
    }

    if (myListModel.getSize() <= myList.getSelectedIndex() || !myListModel.isVisible(myList.getSelectedValue())) {
      selectFirstSelectableItem();
    }
  }

  @Override
  protected void onSelectByMnemonic(Object value) {
    if (myListModel.isVisible(value) && isSelectable(value)) {
      myList.setSelectedValue(value, true);
      myList.repaint();
      handleSelect(true);
    }
  }

  @Override
  protected JComponent getPreferredFocusableComponent() {
    return myList;
  }

  @Override
  protected void onChildSelectedFor(Object value) {
    if (myList.getSelectedValue() != value && isSelectable(value)) {
      myList.setSelectedValue(value, false);
    }
  }

  @Override
  public void setHandleAutoSelectionBeforeShow(final boolean autoHandle) {
    myAutoHandleBeforeShow = autoHandle;
  }

  @Override
  public boolean isModalContext() {
    return true;
  }

  @Override
  public void showInBestPositionFor(@NotNull Editor editor) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      handleSelect(true);
      if (!Disposer.isDisposed(this)) {
        Disposer.dispose(this);
      }
    }
    else {
      super.showInBestPositionFor(editor);
    }
  }

  private void replacePasteAction() {
    if (myStep.isSpeedSearchEnabled()) {
      getList().getActionMap().put(TransferHandler.getPasteAction().getValue(Action.NAME), new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          getSpeedSearch().type(CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor));
          getSpeedSearch().update();
        }
      });
    }
  }

  private boolean isSelectable(@Nullable Object value) {
    // it is possible to use null elements in list model
    return getListStep().isSelectable(value);
  }

  private boolean isSelectableAt(int index) {
    if (0 <= index && index < myListModel.getSize()) {
      Object value = myListModel.getElementAt(index);
      if (isSelectable(value)) return true;
    }
    return false;
  }
}
