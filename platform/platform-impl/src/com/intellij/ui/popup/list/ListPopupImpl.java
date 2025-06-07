// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.list;

import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.client.ClientSystemInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.*;
import com.intellij.ui.popup.async.AsyncPopupStep;
import com.intellij.ui.popup.async.AsyncPopupWaiter;
import com.intellij.ui.popup.tree.TreePopupImpl;
import com.intellij.ui.popup.util.PopupImplUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.util.Comparator;
import java.util.Objects;

import static java.awt.event.InputEvent.CTRL_MASK;
import static java.awt.event.InputEvent.META_MASK;

public class ListPopupImpl extends WizardPopup implements ListPopup, NextStepHandler, ListPopupStep.ListPopupModelListener {
  static final int NEXT_STEP_AREA_WIDTH = 20;
  private static final int DEFAULT_MAX_ROW_COUNT = 30;

  private static final Logger LOG = Logger.getInstance(ListPopupImpl.class);

  protected final PopupInlineActionsSupport myInlineActionsSupport = PopupInlineActionsSupportKt.createSupport(this);

  private MyList myList;

  private MyMouseMotionListener myMouseMotionListener;
  private MyMouseListener myMouseListener;
  private final MouseMovementTracker myMouseMovementTracker = new MouseMovementTracker();

  private ListPopupModel myListModel;

  private @Nullable AsyncPopupWaiter myAsyncPopupWaiter;

  private int myIndexForShowingChild = -1;
  private boolean myAutoHandleBeforeShow;
  private boolean myShowSubmenuOnHover;
  private boolean myExecuteExpandedItemOnClick;
  private boolean myRepackWhenEmptyStateChanges;
  private @Nullable Dimension myNonEmptySize;

  /**
   * @deprecated use {@link #ListPopupImpl(Project, ListPopupStep)}
   */
  @Deprecated(forRemoval = true)
  public ListPopupImpl(@NotNull ListPopupStep aStep) {
    this(CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext()), null, aStep, null);
  }

  public ListPopupImpl(@Nullable Project project, @NotNull ListPopupStep aStep) {
    this(project, null, aStep, null);
  }

  public ListPopupImpl(@Nullable Project project,
                       @Nullable WizardPopup aParent,
                       @NotNull ListPopupStep aStep,
                       Object parentValue) {
    super(project, aParent, aStep);
    setParentValue(parentValue);
    replacePasteAction();
    aStep.addListener(this);
  }

  public void setMaxRowCount(int maxRowCount) {
    if (maxRowCount <= 0) return;
    myList.setVisibleRowCount(maxRowCount);
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

    boolean shouldShow = super.beforeShow();
    if (myAutoHandleBeforeShow) {
      boolean toDispose = tryToAutoSelect(true);
      shouldShow &= !toDispose;
    }

    return shouldShow;
  }

  @Override
  public void goBack() {
    myList.clearSelection();
    super.goBack();
  }

  /**
   * @return index of the selected item, regardless of the applied filter
   */
  public int getOriginalSelectedIndex() {
    int index = myList.getSelectedIndex();
    return index == -1 ? -1 : myListModel.getOriginalIndex(index);
  }

  @Override
  protected void afterShowSync() {
    super.afterShowSync();
    tryToAutoSelect(false);
  }

  private boolean tryToAutoSelect(boolean handleFinalChoices) {
    ListPopupStep<Object> listStep = getListStep();
    boolean selected = false;
    if (listStep instanceof MultiSelectionListPopupStep<?> step) {
      int[] indices = step.getDefaultOptionIndices();
      if (indices.length > 0) {
        ScrollingUtil.ensureIndexIsVisible(myList, indices[0], 0);
        myList.setSelectedIndices(indices);
        selected = true;
      }
    }
    else {
      int defaultIndex = listStep.getDefaultOptionIndex();
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
    String filter = getSpeedSearch().getFilter();
    if (!StringUtil.isEmpty(filter)) {
      int maxUseCount = -1;
      int mostUsedValue = -1;
      int elementsCount = myListModel.getSize();
      for (int i = 0; i < elementsCount; i++) {
        Object value = myListModel.getElementAt(i);
        if (!isSelectable(value)) continue;
        String text = getListStep().getTextFor(value);
        int count =
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
      Object each = myListModel.getElementAt(i);
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
    myList.setVisibleRowCount(DEFAULT_MAX_ROW_COUNT);
    getSpeedSearch().installSupplyTo(myList, false);
    if (myStep.getTitle() != null) {
      myList.getAccessibleContext().setAccessibleName(myStep.getTitle());
    }
    if (step instanceof ListPopupStepEx<?> o) {
      o.setEmptyText(myList.getEmptyText());
    }

    myList.setSelectionModel(new MyListSelectionModel());

    selectFirstSelectableItem();
    myList.setBorder(new EmptyBorder(getListInsets()));

    ScrollingUtil.installActions(myList);

    myList.setCellRenderer(getListElementRenderer());

    registerAction("handleSelection1", KeyEvent.VK_ENTER, 0, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        handleSelect(true, createKeyEvent(e, KeyEvent.VK_ENTER));
      }
    });

    myList.getActionMap().put(ListActions.Right.ID, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object selected = myList.getSelectedValue();
        if (selected != null && myInlineActionsSupport.hasExtraButtons(selected)) {
          if (nextExtendedButton(selected)) return;
        }

        handleRightKeyPressed(createKeyEvent(e, KeyEvent.VK_RIGHT));
      }
    });

    myList.getActionMap().put(ListActions.Left.ID, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object selected = myList.getSelectedValue();
        if (selected != null && myInlineActionsSupport.hasExtraButtons(selected)) {
          if (prevExtendedButton(selected)) return;
        }

        disposeAsyncPopupWaiter();
        if (isClosableByLeftArrow()) {
          goBack();
        }
        else {
          handleLeftKeyPressed(createKeyEvent(e, KeyEvent.VK_LEFT));
        }
      }
    });

    myList.addListSelectionListener(new ListSelectionListener() {
      private int prevItemIndex = -1;

      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (prevItemIndex == myList.getSelectedIndex()) return;
        prevItemIndex = myList.getSelectedIndex();
        myList.setSelectedButtonIndex(null);
        disposeAsyncPopupWaiter();
      }
    });

    PopupUtil.applyNewUIBackground(myList);

    myList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return myList;
  }

  protected @NotNull KeyEvent createKeyEvent(@NotNull ActionEvent e, int keyCode) {
    return new KeyEvent(myList, KeyEvent.KEY_PRESSED, e.getWhen(), e.getModifiers(), keyCode, KeyEvent.CHAR_UNDEFINED);
  }

  private boolean nextExtendedButton(Object selected) {
    Integer currentIndex = myList.getSelectedButtonIndex();
    int buttonsCount = myInlineActionsSupport.calcExtraButtonsCount(selected);
    if (currentIndex == null) currentIndex = -1;
    if (currentIndex >= buttonsCount - 1) return false;

    boolean changed = myList.setSelectedButtonIndex(++currentIndex);
    if (changed) {
      getContent().repaint();
      if (myInlineActionsSupport.isMoreButton(selected, currentIndex) && buttonsCount == 1) {
        myInlineActionsSupport.performAction(selected, currentIndex, null);
      }
    }
    return true;
  }

  private boolean prevExtendedButton(Object selected) {
    Integer currentIndex = myList.getSelectedButtonIndex();
    if (currentIndex == null) return false;

    if (--currentIndex < 0) currentIndex = null;
    boolean changed = myList.setSelectedButtonIndex(currentIndex);
    if (changed) {
      getContent().repaint();
    }
    return true;
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
    getListStep().removeListener(this);
    disposeAsyncPopupWaiter();
    super.dispose();
  }

  private void disposeAsyncPopupWaiter() {
    if (myAsyncPopupWaiter != null) {
      Disposer.dispose(myAsyncPopupWaiter);
      myAsyncPopupWaiter = null;
    }
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

  private boolean _handleSelect(boolean handleFinalChoices, @Nullable InputEvent e) {
    if (myList.getSelectedIndex() == -1) return false;

    if (getSpeedSearch().isHoldingFilter() && myList.getModel().getSize() == 0) return false;

    java.util.List<Object> selection = myList.getSelectedValuesList();
    if (selection.isEmpty()) return false;
    ListPopupStep<Object> listStep = getListStep();
    Object selectedValue = selection.get(0);

    boolean selectable = listStep.isSelectable(selectedValue);
    boolean preferExecution = listStep.isFinal(selectedValue) && selectable && handleFinalChoices;
    if (!myExecuteExpandedItemOnClick && !preferExecution && myList.getSelectedIndex() == getIndexForShowingChild()) {
      // child was closed by StackingPopupDispatcherImpl by this very event
      // we should not re-open it again
      // BUG IJPL-180199: this will also happen if child was closed by an earlier 'Escape' key press event
      if (myChild != null && !myChild.isVisible()) {
        setIndexForShowingChild(-1);
      }
      return false;
    }
    if (!selectable) return false;

    if ((listStep instanceof MultiSelectionListPopupStep<Object> o && !o.hasSubstep(selection)
         || !listStep.hasSubstep(selectedValue)) &&
        !handleFinalChoices) {
      return false;
    }

    disposeChildren();

    if (myListModel.getSize() == 0) {
      disposePopup(e);
      return true;
    }

    valuesSelected(selection);

    Object onlyItem = ContainerUtil.getOnlyItem(selection);
    if (e instanceof MouseEvent me && onlyItem != null) {
      myMouseMotionListener.updateSelectedButtonIndex(me.getPoint());
    }

    Integer inlineButtonIndex = myList.getSelectedButtonIndex();
    if (inlineButtonIndex != null) {
      KeepPopupOnPerform keepPopup = myInlineActionsSupport.getKeepPopupOnPerform(selectedValue, inlineButtonIndex);
      if (!Utils.isKeepPopupOpen(keepPopup, e)) {
        disposePopup(e);
      }
      myInlineActionsSupport.performAction(selectedValue, inlineButtonIndex, e);
      return true;
    }

    PopupStep<?> nextStep;
    try (AccessToken ignore = PopupImplUtil.prohibitFocusEventsInHandleSelect();
         AccessToken ignore2 = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
      if (listStep instanceof MultiSelectionListPopupStep<Object> o) {
        nextStep = o.onChosen(selection, handleFinalChoices);
      }
      else if (e != null && listStep instanceof ListPopupStepEx<Object> o) {
        nextStep = o.onChosen(selectedValue, handleFinalChoices, e);
      }
      else {
        nextStep = listStep.onChosen(selectedValue, handleFinalChoices);
      }
    }

    if (nextStep == PopupStep.FINAL_CHOICE &&
        onlyItem instanceof PopupFactoryImpl.ActionItem item &&
        Utils.isKeepPopupOpen(item.getKeepPopupOnPerform(), e)) {
      ActionPopupStep actionPopupStep = ObjectUtils.tryCast(getListStep(), ActionPopupStep.class);
      if (actionPopupStep != null && actionPopupStep.isSelectable(item)) {
        actionPopupStep.updateStepItems(getList());
        return false;
      }
    }

    return handleNextStep(nextStep, onlyItem, e);
  }

  protected void handleRightKeyPressed(@NotNull KeyEvent keyEvent) {
    handleSelect(false, keyEvent);
  }

  protected void handleLeftKeyPressed(@NotNull KeyEvent keyEvent) {
  }

  private void disposePopup(@Nullable InputEvent e) {
    if (myStep.getFinalRunnable() != null) {
      setFinalRunnable(myStep.getFinalRunnable());
    }
    setOk(true);
    disposeAllParents(e);
    setIndexForShowingChild(-1);
  }

  private void valuesSelected(java.util.List<Object> values) {
    if (shouldUseStatistics()) {
      String filter = getSpeedSearch().getFilter();
      if (!StringUtil.isEmpty(filter)) {
        for (Object value : values) {
          String text = getListStep().getTextFor(value);
          StatisticsManager.getInstance().incUseCount(new StatisticsInfo("#list_popup:" + getListStep().getTitle() + "#" + filter, text));
        }
      }
    }
  }

  @Override
  public void handleNextStep(PopupStep nextStep, Object parentValue) {
    handleNextStep(nextStep, parentValue, null);
  }

  public boolean handleNextStep(PopupStep nextStep, Object parentValue, InputEvent e) {
    disposeAsyncPopupWaiter();
    if (nextStep == PopupStep.FINAL_CHOICE) {
      disposePopup(e);
      return true;
    }
    else if (nextStep instanceof AsyncPopupStep<?> async) {
      Rectangle bounds = getCellBounds(getSelectedIndex());
      // TODO: separator size calculation, see IDEA-327566
      RelativePoint point = new RelativePoint(getList(), new Point(bounds.width, bounds.y + bounds.height / 2));
      myAsyncPopupWaiter = new AsyncPopupWaiter(async, point, step -> {
        handleNextStep(step, parentValue, e);
        return Unit.INSTANCE;
      });
      return false;
    }
    else {
      showNextStepPopup(nextStep, parentValue);
      return false;
    }
  }

  void showNextStepPopup(PopupStep nextStep, Object parentValue) {
    if (nextStep == null) {
      String valueText = getListStep().getTextFor(parentValue);
      String message = String.format("Cannot open submenu for '%s' item. PopupStep is null", valueText);
      LOG.warn(message);
      return;
    }

    if ((PopupStep<?>)myStep instanceof ActionPopupStep o &&
        nextStep instanceof ActionPopupStep oo) {
      oo.setSubStepContextAdjuster(o.getSubStepContextAdjuster());
    }

    Point point = myList.indexToLocation(myList.getSelectedIndex());
    SwingUtilities.convertPointToScreen(point, myList);
    myChild = createPopup(this, nextStep, parentValue);
    if (ScreenReader.isActive()) myChild.setRequestFocus(true);
    if (myChild instanceof ListPopup child) {
      for (ListSelectionListener listener : myList.getListSelectionListeners()) {
        child.addListSelectionListener(listener);
      }
      child.setShowSubmenuOnHover(myShowSubmenuOnHover);
      child.setExecuteExpandedItemOnClick(myExecuteExpandedItemOnClick);
    }
    JComponent container = getContent();

    int y = point.y;
    if (parentValue != null && getListModel().isSeparatorAboveOf(parentValue)) {
      SeparatorWithText swt = new SeparatorWithText();
      swt.setCaption(getListModel().getCaptionAboveOf(parentValue));
      y += swt.getPreferredSize().height - 1;
    }

    if (!UiInterceptors.tryIntercept(myChild)) {
      // Intercept child popup in tests because it is impossible to calculate location on screen there
      myChild.show(container, container.getLocationOnScreen().x + container.getWidth() - STEP_X_PADDING, y, true);
    }
    setIndexForShowingChild(myList.getSelectedIndex());
    myMouseMovementTracker.reset();
  }

  @Override
  public void addListSelectionListener(ListSelectionListener listSelectionListener) {
    myList.addListSelectionListener(listSelectionListener);
  }

  @Override
  public void onModelChanged() {
    boolean updateEmptyModel = myListModel.getSize() == 0;
    myListModel.syncModel();
    if (updateEmptyModel) {
      selectFirstSelectableItem();
      pack(true, true);
    } else {
      resizePopupIfNeeded();
    }
  }

  private void resizePopupIfNeeded() {
    Dimension size = getSize();
    if (size == null) return;

    Dimension packedSize = calculateSizeForPack(true, true);
    if (packedSize == null) return;

    if (packedSize.width > size.width || packedSize.height > size.height) {
      Dimension desiredSize = new Dimension(Math.max(size.width, packedSize.width), Math.max(size.height, packedSize.height));
      setSize(desiredSize);
    }
  }

  private enum ExtendMode {
    NO_EXTEND, EXTEND_ON_HOVER
  }

  private final class MyMouseMotionListener extends MouseMotionAdapter {

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
      }
      else {
        myList.clearSelection();
        myLastSelectedIndex = -1;
      }
      if (updateSelectedButtonIndex(point)) {
        getContent().repaint();
      }

      notifyParentOnChildSelection();
    }

    boolean updateSelectedButtonIndex(Point point) {
      int index = myList.locationToIndex(point);
      if (index < 0 || !isSelectableAt(index)) {
        return myList.setSelectedButtonIndex(null);
      }
      else {
        Object element = myListModel.getElementAt(index);
        if (element != null && myInlineActionsSupport.hasExtraButtons(element)) {
          Integer buttonIndex = myInlineActionsSupport.calcButtonIndex(element, point);
          return myList.setSelectedButtonIndex(buttonIndex);
        }
      }
      return false;
    }

    private @NotNull ExtendMode calcExtendMode(int index) {
      ListPopupStep<Object> listStep = getListStep();
      Object selectedValue = myListModel.getElementAt(index);
      if (selectedValue == null || !listStep.hasSubstep(selectedValue)) return ExtendMode.NO_EXTEND;

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
        ClientId currentClientId = ClientId.getCurrent();
        myShowSubmenuTimer = new Timer(250, e -> {
          try (AccessToken ignore = ClientId.withClientId(currentClientId)) {
            if (!isDisposed() && myLastSelectedIndex == forIndex) {
              disposeChildren();
              showNextStepPopup(listStep.onChosen(selectedValue, false), selectedValue);
            }
          }
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

  public Object @NotNull [] getSelectedValues() {
    return myList.getSelectedValues();
  }

  private final class MyMouseListener extends MouseAdapter {

    @Override
    public void mouseReleased(MouseEvent e) {
      if (!isActionClick(e) || isMultiSelectionEnabled() && UIUtil.isSelectionButtonDown(e)) return;
      IdeEventQueue.getInstance().blockNextEvents(e); // sometimes, after popup close, MOUSE_RELEASE event delivers to other components
      Object selectedValue = myList.getSelectedValue();
      ListPopupStep<Object> listStep = getListStep();
      handleSelect(handleFinalChoices(e, selectedValue, listStep), e);
      stopTimer();
    }
  }

  protected boolean handleFinalChoices(MouseEvent e, Object selectedValue, ListPopupStep<Object> listStep) {
    return selectedValue == null || !listStep.hasSubstep(selectedValue) || !listStep.isSelectable(selectedValue) || !isOnNextStepButton(e);
  }

  private boolean isOnNextStepButton(MouseEvent e) {
    int index = myList.getSelectedIndex();
    Rectangle bounds = myList.getCellBounds(index, index);
    if (bounds != null) {
      JBInsets.removeFrom(bounds, UIUtil.getListCellPadding());
    }
    Point point = e.getPoint();
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

  interface ListWithInlineButtons {
    @Nullable Integer getSelectedButtonIndex();
  }

  private final class MyList extends JBList implements ListWithInlineButtons, UiDataProvider {

    private @Nullable Integer selectedButtonIndex;

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
      if (!isClick || myList.locationToIndex(e.getPoint()) == myList.getSelectedIndex() ||
          isMultiSelectionEnabled() && hasMultiSelectionModifier(e)) {
        super.processMouseEvent(e);
      }
    }

    private static boolean hasMultiSelectionModifier(@NotNull MouseEvent e) {
      return (e.getModifiers() & (ClientSystemInfo.isMac() ? META_MASK : CTRL_MASK)) != 0;
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      boolean lazy = getListStep() instanceof BaseListPopupStep<Object> listStep && listStep.isLazyUiSnapshot();
      PopupImplUtil.uiSnapshotForList(myList, sink, lazy);
      if (mySpeedSearchPatternField != null && mySpeedSearchPatternField.isVisible()) {
        sink.set(PlatformDataKeys.SPEED_SEARCH_COMPONENT, mySpeedSearchPatternField);
      }
    }

    @Override
    public @Nullable Integer getSelectedButtonIndex() {
      return selectedButtonIndex;
    }

    private boolean setSelectedButtonIndex(@Nullable Integer index) {
      if (Objects.compare(index, selectedButtonIndex, Comparator.nullsFirst(Integer::compare)) == 0) return false;

      selectedButtonIndex = index;
      return true;
    }
  }

  private final class MyListSelectionModel extends DefaultListSelectionModel {
    private boolean myInitialSelectionSet = false;

    private MyListSelectionModel() {
      setSelectionMode(isMultiSelectionEnabled() ? MULTIPLE_INTERVAL_SELECTION : SINGLE_SELECTION);
    }

    @Override
    public void clearSelection() {
      super.clearSelection();
      setAnchorSelectionIndex(-1);
      setLeadSelectionIndex(-1);
    }

    @Override
    public void setSelectionInterval(int index0, int index1) {
      if (getSelectionMode() == SINGLE_SELECTION) {
        int index = findSelectableIndex(index0, getLeadSelectionIndex());
        if (0 <= index) super.setSelectionInterval(index, index);
        if (index == 0 && !myInitialSelectionSet) {
          myInitialSelectionSet = true;
          fireValueChanged(0, 0); // enforce listeners to be notified about initial selection
        }
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
           : findSelectableIndexInModel(index, lead < -1 ? -1 : Math.min(lead, size));
  }

  private int findSelectableIndexInModel(int index, int stop) {
    while (index != stop) {
      if (getListStep().isSelectable(myListModel.getElementAt(index))) return index;
      index += index > stop ? -1 : 1;
    }
    return -1;
  }

  @ApiStatus.Internal
  public void setRepackWhenEmptyStateChanges(boolean repackWhenEmptyStateChanges) {
    this.myRepackWhenEmptyStateChanges = repackWhenEmptyStateChanges;
  }

  @Override
  protected void onSpeedSearchPatternChanged() {
    boolean wasEmpty = myListModel.getSize() == 0;
    ListPopupStep<?> step = getListStep();
    if (step instanceof FilterableListPopupStep<?> o) {
      o.updateFilter(mySpeedSearch.getFilter());
    }
    var before = myListModel.getSize();
    myListModel.refilter();
    var after = myListModel.getSize();
    var fusActivity = ActionGroupPopupActivity.getCurrentActivity(this);
    if (fusActivity != null) {
      fusActivity.filtered(StringUtil.length(mySpeedSearch.getFilter()), before, after);
    }
    boolean nowEmpty = myListModel.getSize() == 0;
    if (myListModel.getSize() > 0) {
      if (!(shouldUseStatistics() && autoSelectUsingStatistics())) {
        selectBestMatch();
      }
    }
    if (myRepackWhenEmptyStateChanges && wasEmpty != nowEmpty) {
      if (nowEmpty) {
        myNonEmptySize = getSize();
        pack(false, true);
      }
      else if (myNonEmptySize != null) {
        setSize(myNonEmptySize);
      }
    }
  }

  private void selectBestMatch() {
    int fullMatchIndex = myListModel.getClosestMatchIndex();
    if (fullMatchIndex != -1 && isSelectableAt(fullMatchIndex)) {
      myList.setSelectedIndex(fullMatchIndex);
    }
    else {
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

  public void selectAndExpandValue(Object value) {
    selectAndChooseNextStep(value, false);
  }

  public void selectAndExecuteValue(Object value) {
    selectAndChooseNextStep(value, true);
  }

  private void selectAndChooseNextStep(Object value, boolean finalChoice) {
    if (myListModel.isVisible(value) && isSelectable(value)) {
      myList.setSelectedValue(value, true);
      disposeChildren();
      PopupStep<Object> listStep = getListStep();
      PopupStep<?> nextStep = listStep.onChosen(value, finalChoice);
      handleNextStep(nextStep, value);
    }
  }

  @Override
  public void setHandleAutoSelectionBeforeShow(boolean autoHandle) {
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
  public void setExecuteExpandedItemOnClick(boolean executeExpandedItemOnClick) {
    myExecuteExpandedItemOnClick = executeExpandedItemOnClick;
  }

  @Override
  public void showInBestPositionFor(@NotNull Editor editor) {
    if (UiInterceptors.tryIntercept(this)) return;
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
    return PopupUtil.getListInsets(StringUtil.isNotEmpty(getStep().getTitle()), isAdVisible());
  }
}
