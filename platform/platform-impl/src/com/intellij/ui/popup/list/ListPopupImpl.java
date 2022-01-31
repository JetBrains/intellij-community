// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.list;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.ClosableByLeftArrow;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.popup.NextStepHandler;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.tree.TreePopupImpl;
import com.intellij.ui.popup.util.PopupImplUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
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

import static java.awt.event.InputEvent.CTRL_MASK;
import static java.awt.event.InputEvent.META_MASK;

public class ListPopupImpl extends WizardPopup implements ListPopup, NextStepHandler {
  public static final int NEXT_STEP_AREA_WIDTH = 20;

  private static final Logger LOG = Logger.getInstance(ListPopupImpl.class);

  private MyList myList;

  private MyMouseMotionListener myMouseMotionListener;
  private MyMouseListener myMouseListener;
  private final MouseMovementTracker myMouseMovementTracker = new MouseMovementTracker();

  private ListPopupModel myListModel;

  private int myIndexForShowingChild = -1;
  private int myMaxRowCount = 30;
  private boolean myAutoHandleBeforeShow;
  private boolean myShowSubmenuOnHover;

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
    int offset = -UIUtil.getListCellHPadding() - getListInsets().left;
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
      int[] indices = ((MultiSelectionListPopupStep<?>)listStep).getDefaultOptionIndices();
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
      ((ListPopupStepEx<?>)step).setEmptyText(myList.getEmptyText());
    }

    myList.setSelectionModel(new MyListSelectionModel());

    selectFirstSelectableItem();
    myList.setBorder(new EmptyBorder(getListInsets()));

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

  protected ListCellRenderer<?> getListElementRenderer() {
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

    PopupStep<?> nextStep;
    try (AccessToken ignore = PopupImplUtil.prohibitFocusEventsInHandleSelect()) {
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
      showNextStepPopup(nextStep, parentValue);
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

  protected void showNextStepPopup(PopupStep nextStep, Object parentValue) {
    if (nextStep == null) {
      String valueText = getListStep().getTextFor(parentValue);
      String message = String.format("Cannot open submenu for '%s' item. PopupStep is null", valueText);
      LOG.warn(message);
      return;
    }

    final Point point = myList.indexToLocation(myList.getSelectedIndex());
    SwingUtilities.convertPointToScreen(point, myList);
    myChild = createPopup(this, nextStep, parentValue);
    if (myChild instanceof ListPopup) {
      ListPopup child = (ListPopup)myChild;
      for (ListSelectionListener listener : myList.getListSelectionListeners()) {
        child.addListSelectionListener(listener);
      }
      child.setShowSubmenuOnHover(myShowSubmenuOnHover);
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
    myMouseMovementTracker.reset();

    if (Registry.is("ide.list.popup.separate.next.step.button")) {
      myList.setNextStepButtonSelected(true);
      Disposer.register(myChild, () -> {
        setIndexForShowingChild(-1);
        myList.setNextStepButtonSelected(false);
      });
    }
  }

  @Override
  public void addListSelectionListener(ListSelectionListener listSelectionListener) {
    myList.addListSelectionListener(listSelectionListener);
  }

  private enum ExtendMode {
    NO_EXTEND, EXTEND_ON_HOVER, EXTEND_ON_BUTTON
  }

  private class MyMouseMotionListener extends MouseMotionAdapter {

    private int myLastSelectedIndex = -2;
    private ExtendMode myExtendMode = ExtendMode.NO_EXTEND;
    private Point myLastMouseLocation;
    private Timer myShowSubmenuTimer;

    /**
     * this method should be changed only in par with
     * {@link TreePopupImpl.MyMouseMotionListener#isMouseMoved(Point)}
     */
    private boolean isMouseMoved(Point location) {
      if (myLastMouseLocation == null) {
        myLastMouseLocation = location;
        return false;
      }
      Point prev = myLastMouseLocation;
      myLastMouseLocation = location;
      return !prev.equals(location);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      Point lastPoint = myLastMouseLocation;
      if (!isMouseMoved(e.getLocationOnScreen())) return;

      Point point = e.getPoint();
      int index = myList.locationToIndex(point);

      if (isSelectableAt(index)) {
        if (index != myLastSelectedIndex && !isMovingToSubmenu(e)) {
          myExtendMode = calcExtendMode(index);
          if (!isMultiSelectionEnabled() || !UIUtil.isSelectionButtonDown(e) && myList.getSelectedIndices().length <= 1) {
            myList.setSelectedIndex(index);
            if (myShowSubmenuOnHover) {
              disposeChildren();
            }
            if (myExtendMode == ExtendMode.EXTEND_ON_HOVER) {
              showSubMenu(index, true);
            }
          }
          restartTimer();
          myLastSelectedIndex = index;
        }

        if (myExtendMode == ExtendMode.EXTEND_ON_BUTTON) {
          boolean nextStepButtonSelected = isOnNextStepButton(e);
          boolean changed = myList.setNextStepButtonSelected(nextStepButtonSelected);
          if (changed) {
            if (nextStepButtonSelected) {
              showSubMenu(index, false);
            }
            getContent().repaint();
          }
        }
      }
      else {
        myList.clearSelection();
        myLastSelectedIndex = -1;
      }

      notifyParentOnChildSelection();
    }

    @NotNull
    private ExtendMode calcExtendMode(int index) {
      ListPopupStep<Object> listStep = getListStep();
      Object selectedValue = myListModel.getElementAt(index);
      if (selectedValue == null || !listStep.hasSubstep(selectedValue)) return ExtendMode.NO_EXTEND;

      if (Registry.is("ide.list.popup.separate.next.step.button")) {
        return listStep.isFinal(selectedValue)
               ? ExtendMode.EXTEND_ON_BUTTON
               : myShowSubmenuOnHover ? ExtendMode.EXTEND_ON_HOVER : ExtendMode.NO_EXTEND;
      }

      return myShowSubmenuOnHover ? ExtendMode.EXTEND_ON_HOVER : ExtendMode.NO_EXTEND;
    }

    private boolean isMovingToSubmenu(MouseEvent e) {
      if (myChild == null || myChild.isDisposed()) return false;

      Rectangle childBounds = myChild.getBounds();
      childBounds.setLocation(myChild.getLocationOnScreen());

      return myMouseMovementTracker.isMovingTowards(e, childBounds);
    }

    private void showSubMenu(int forIndex, boolean withTimer) {
      if (getIndexForShowingChild() == forIndex) return;

      disposeChildren();

      if (myShowSubmenuTimer != null && myShowSubmenuTimer.isRunning()) {
        myShowSubmenuTimer.stop();
        myShowSubmenuTimer = null;
      }

      ListPopupStep<Object> listStep = getListStep();
      Object selectedValue = myListModel.getElementAt(forIndex);
      if (withTimer) {
        myShowSubmenuTimer = new Timer(250, e -> {
          if (!isDisposed() && myLastSelectedIndex == forIndex)
            showNextStepPopup(listStep.onChosen(selectedValue, false), selectedValue);
        });
        myShowSubmenuTimer.setRepeats(false);
        myShowSubmenuTimer.start();
      }
      else {
        showNextStepPopup(listStep.onChosen(selectedValue, false), selectedValue);
      }
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
    return bounds != null && point.getX() > bounds.width + bounds.getX() - NEXT_STEP_AREA_WIDTH;
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

  public interface NestedList {
    boolean isNextStepButtonSelected();
  }

  private class MyList extends JBList implements DataProvider, NestedList {

    private boolean myNextStepButtonSelected;

    MyList() {
      super(myListModel);
      HintUpdateSupply.installSimpleHintUpdateSupply(this);
    }

    boolean setNextStepButtonSelected(boolean nextStepButtonSelected) {
      if (nextStepButtonSelected == myNextStepButtonSelected) return false;

      myNextStepButtonSelected = nextStepButtonSelected;
      return true;
    }

    @Override
    public boolean isNextStepButtonSelected() {
      return myNextStepButtonSelected;
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
      if (!isClick || myList.locationToIndex(e.getPoint()) == myList.getSelectedIndex() ||
          isMultiSelectionEnabled() && hasMultiSelectionModifier(e)) {
        super.processMouseEvent(e);
      }
    }

    private boolean hasMultiSelectionModifier(@NotNull MouseEvent e) {
      return (e.getModifiers() & (SystemInfo.isMac ? META_MASK : CTRL_MASK)) != 0;
    }

    @Override
    public Object getData(@NotNull String dataId) {
      if (PlatformDataKeys.SPEED_SEARCH_COMPONENT.is(dataId)) {
        if (mySpeedSearchPatternField != null && mySpeedSearchPatternField.isVisible()) {
          return mySpeedSearchPatternField;
        }
      }
      return PopupImplUtil.getDataImplForList(myList, dataId);
    }
  }

  private final class MyListSelectionModel extends DefaultListSelectionModel {
    private MyListSelectionModel() {
      setSelectionMode(isMultiSelectionEnabled() ? MULTIPLE_INTERVAL_SELECTION : SINGLE_SELECTION);
    }

    @Override
    public void setSelectionInterval(int index0, int index1) {
      if (getSelectionMode() == SINGLE_SELECTION) {
        int index = findSelectableIndex(index0, getLeadSelectionIndex());
        if (0 <= index) super.setSelectionInterval(index, index);
        if (index == 0) fireValueChanged(0, 0); // enforce listeners to be notified about initial selection
      }
      else {
        super.setSelectionInterval(index0, index1); // TODO: support when needed
      }
    }
  }

  private int findSelectableIndex(int index, int lead) {
    int size = myListModel.getSize();
    if (index < 0 || size <= index) return -1;

    // iterate through the first part of the available items
    int found = findSelectableIndexInModel(index, index < lead ? -1 : size);
    if (found >= 0) return found;

    // iterate through the second part of the available items
    UISettings settings = UISettings.getInstanceOrNull();
    return settings != null && settings.getCycleScrolling() && 1 == Math.abs(index - lead)
           ? findSelectableIndexInModel(index < lead ? size - 1 : 0, index)
           : findSelectableIndexInModel(index, index < lead ? size : -1);
  }

  private int findSelectableIndexInModel(int index, int stop) {
    while (index != stop) {
      if (getListStep().isSelectable(myListModel.getElementAt(index))) return index;
      index += index > stop ? -1 : 1;
    }
    return -1;
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
  public void setShowSubmenuOnHover(boolean showSubmenuOnHover) {
    myShowSubmenuOnHover = showSubmenuOnHover;
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
    try {
      return getListStep().isSelectable(value);
    }
    catch (Exception exception) {
      LOG.error(getListStep().getClass().getName(), exception);
      return false;
    }
  }

  private boolean isSelectableAt(int index) {
    if (0 <= index && index < myListModel.getSize()) {
      Object value = myListModel.getElementAt(index);
      if (isSelectable(value)) return true;
    }
    return false;
  }

  private Insets getListInsets() {
    if (!ExperimentalUI.isNewUI()) {
      return UIUtil.getListViewportPadding(isAdVisible());
    }

    boolean isTitleAvailable = getStep().getTitle() != null;
    int topInset = isTitleAvailable ? 0 : JBUI.CurrentTheme.Popup.bodyTopInsetNoHeader();
    int bottomInset = isAdVisible() ? JBUI.CurrentTheme.Popup.bodyBottomInsetBeforeAd() : JBUI.CurrentTheme.Popup.bodyBottomInsetNoAd();
    return new JBInsets(topInset, 0, bottomInset, 0);
  }
}
