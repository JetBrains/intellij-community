/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui.popup;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.FocusTrackback;
import com.intellij.ui.HintHint;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.list.IconListPopupRenderer;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.mock.MockConfirmation;
import com.intellij.ui.popup.tree.TreePopupImpl;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.actionSystem.Presentation.*;

public class PopupFactoryImpl extends JBPopupFactory {

  /**
   * Allows to get an editor position for which a popup with auxiliary information might be shown.
   * <p/>
   * Primary intention for this key is to hint popup position for the non-caret location.
   */
  public static final Key<VisualPosition> ANCHOR_POPUP_POSITION = Key.create("popup.anchor.position");

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.popup.PopupFactoryImpl");

  private final Map<Disposable, List<Balloon>> myStorage = ContainerUtil.createWeakMap();

  @NotNull
  @Override
  public ListPopup createConfirmation(String title, final Runnable onYes, int defaultOptionIndex) {
    return createConfirmation(title, CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText(), onYes, defaultOptionIndex);
  }

  @NotNull
  @Override
  public ListPopup createConfirmation(String title, final String yesText, String noText, final Runnable onYes, int defaultOptionIndex) {
    return createConfirmation(title, yesText, noText, onYes, EmptyRunnable.getInstance(), defaultOptionIndex);
  }

  @NotNull
  @Override
  public JBPopup createMessage(String text) {
    return createListPopup(new BaseListPopupStep<>(null, new String[]{text}));
  }

  @Override
  public Balloon getParentBalloonFor(@Nullable Component c) {
    if (c == null) return null;
    Component eachParent = c;
    while (eachParent != null) {
      if (eachParent instanceof JComponent) {
        Object balloon = ((JComponent)eachParent).getClientProperty(Balloon.KEY);
        if (balloon instanceof Balloon) {
          return (Balloon)balloon;
        }
      }
      eachParent = eachParent.getParent();
    }

    return null;
  }

  @NotNull
  @Override
  public ListPopup createConfirmation(String title,
                                      final String yesText,
                                      String noText,
                                      final Runnable onYes,
                                      final Runnable onNo,
                                      int defaultOptionIndex)
  {

    final BaseListPopupStep<String> step = new BaseListPopupStep<String>(title, yesText, noText) {
      @Override
      public PopupStep onChosen(String selectedValue, final boolean finalChoice) {
        return doFinalStep(selectedValue.equals(yesText) ? onYes : onNo);
      }

      @Override
      public void canceled() {
        onNo.run();
      }

      @Override
      public boolean isMnemonicsNavigationEnabled() {
        return true;
      }
    };
    step.setDefaultOptionIndex(defaultOptionIndex);

    final ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    return app == null || !app.isUnitTestMode() ? new ListPopupImpl(step) : new MockConfirmation(step, yesText);
  }


  private static ListPopup createActionGroupPopup(final String title,
                                                  @NotNull ActionGroup actionGroup,
                                                  @NotNull DataContext dataContext,
                                                  boolean showNumbers,
                                                  boolean useAlphaAsNumbers,
                                                  boolean showDisabledActions,
                                                  boolean honorActionMnemonics,
                                                  final Runnable disposeCallback,
                                          final int maxRowCount) {
    return createActionGroupPopup(title, actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics, disposeCallback,
                                  maxRowCount, null, null);
  }

  public ListPopup createActionGroupPopup(final String title,
                                          final ActionGroup actionGroup,
                                          @NotNull DataContext dataContext,
                                          boolean showNumbers,
                                          boolean showDisabledActions,
                                          boolean honorActionMnemonics,
                                          final Runnable disposeCallback,
                                          final int maxRowCount) {
    return createActionGroupPopup(title, actionGroup, dataContext, showNumbers, showDisabledActions, honorActionMnemonics, disposeCallback,
                                  maxRowCount, null);
  }

  @NotNull
  public ListPopup createActionGroupPopup(String title,
                                          @NotNull ActionGroup actionGroup,
                                          @NotNull DataContext dataContext,
                                          ActionSelectionAid aid,
                                          boolean showDisabledActions,
                                          Runnable disposeCallback,
                                          int maxRowCount,
                                          Condition<AnAction> preselectActionCondition,
                                          @Nullable String actionPlace) {
    return new ActionGroupPopup(title,
                                actionGroup,
                                dataContext,
                                aid == ActionSelectionAid.ALPHA_NUMBERING || aid == ActionSelectionAid.NUMBERING,
                                aid == ActionSelectionAid.ALPHA_NUMBERING,
                                showDisabledActions,
                                aid == ActionSelectionAid.MNEMONICS,
                                disposeCallback,
                                maxRowCount,
                                preselectActionCondition,
                                actionPlace);
  }

  private static ListPopup createActionGroupPopup(String title,
                                                  @NotNull ActionGroup actionGroup,
                                                  @NotNull DataContext dataContext,
                                                  boolean showNumbers,
                                                  boolean useAlphaAsNumbers,
                                                  boolean showDisabledActions,
                                                  boolean honorActionMnemonics,
                                                  Runnable disposeCallback,
                                                  int maxRowCount,
                                                  Condition<AnAction> preselectActionCondition,
                                                  @Nullable String actionPlace) {
    return new ActionGroupPopup(title, actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics,
                                disposeCallback, maxRowCount, preselectActionCondition, actionPlace);
  }

  public static class ActionGroupPopup extends ListPopupImpl {

    private final Runnable myDisposeCallback;
    private final Component myComponent;
    private final String myActionPlace;
    private IconHoverListener myIconsHoverListener;

    public ActionGroupPopup(final String title,
                            @NotNull ActionGroup actionGroup,
                            @NotNull DataContext dataContext,
                            boolean showNumbers,
                            boolean useAlphaAsNumbers,
                            boolean showDisabledActions,
                            boolean honorActionMnemonics,
                            final Runnable disposeCallback,
                            final int maxRowCount,
                            final Condition<AnAction> preselectActionCondition,
                            @Nullable final String actionPlace) {
      this(title, actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics, disposeCallback,
           maxRowCount, preselectActionCondition, actionPlace, false);
    }

    public ActionGroupPopup(final String title,
                            @NotNull ActionGroup actionGroup,
                            @NotNull DataContext dataContext,
                            boolean showNumbers,
                            boolean useAlphaAsNumbers,
                            boolean showDisabledActions,
                            boolean honorActionMnemonics,
                            final Runnable disposeCallback,
                            final int maxRowCount,
                            final Condition<AnAction> preselectActionCondition,
                            @Nullable final String actionPlace,
                            boolean autoSelection) {
      this(null, createStep(title, actionGroup, dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics,
                            preselectActionCondition, actionPlace, autoSelection), disposeCallback, dataContext, actionPlace, maxRowCount);
    }

    protected ActionGroupPopup(@Nullable WizardPopup aParent,
                               @NotNull ListPopupStep step,
                               @Nullable Runnable disposeCallback,
                               @NotNull DataContext dataContext,
                               @Nullable String actionPlace,
                               int maxRowCount) {
      super(aParent, step, maxRowCount);
      myDisposeCallback = disposeCallback;
      myComponent = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
      myActionPlace = actionPlace == null ? ActionPlaces.UNKNOWN : actionPlace;

      registerAction("handleActionToggle1", KeyEvent.VK_SPACE, 0, new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          handleToggleAction();
        }
      });

      addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          final JList list = (JList)e.getSource();
          final ActionItem actionItem = (ActionItem)list.getSelectedValue();
          if (actionItem == null) return;
          Presentation presentation = updateActionItem(actionItem);
          ActionMenu.showDescriptionInStatusBar(true, myComponent, presentation.getDescription());
        }
      });
    }

    @NotNull
    private Presentation updateActionItem(@NotNull ActionItem actionItem) {
      AnAction action = actionItem.getAction();
      Presentation presentation = new Presentation();
      presentation.setDescription(action.getTemplatePresentation().getDescription());

      final AnActionEvent actionEvent =
        new AnActionEvent(null, DataManager.getInstance().getDataContext(myComponent), myActionPlace, presentation,
                          ActionManager.getInstance(), 0);
      actionEvent.setInjectedContext(action.isInInjectedContext());
      ActionUtil.performDumbAwareUpdate(LaterInvocator.isInModalContext(), action, actionEvent, false);
      return presentation;
    }

    private static ListPopupStep createStep(String title,
                                            @NotNull ActionGroup actionGroup,
                                            @NotNull DataContext dataContext,
                                            boolean showNumbers,
                                            boolean useAlphaAsNumbers,
                                            boolean showDisabledActions,
                                            boolean honorActionMnemonics,
                                            Condition<AnAction> preselectActionCondition,
                                            @Nullable String actionPlace, boolean autoSelection) {
      final Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
      LOG.assertTrue(component != null, "dataContext has no component for new ListPopupStep");

      final ActionStepBuilder builder =
        new ActionStepBuilder(dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions, honorActionMnemonics);
      if (actionPlace != null) {
        builder.setActionPlace(actionPlace);
      }
      builder.buildGroup(actionGroup);
      final List<ActionItem> items = builder.getItems();

      return new ActionPopupStep(items, title, component, showNumbers || honorActionMnemonics && itemsHaveMnemonics(items),
                                 preselectActionCondition, autoSelection, showDisabledActions);
    }

    @Override
    public void dispose() {
      if (myDisposeCallback != null) {
        myDisposeCallback.run();
      }
      getList().removeMouseMotionListener(myIconsHoverListener);
      getList().removeListSelectionListener(myIconsHoverListener);
      ActionMenu.showDescriptionInStatusBar(true, myComponent, null);
      super.dispose();
    }

    @Override
    public void handleSelect(boolean handleFinalChoices, InputEvent e) {
      final Object selectedValue = getList().getSelectedValue();
      final ActionPopupStep actionPopupStep = ObjectUtils.tryCast(getListStep(), ActionPopupStep.class);

      if (actionPopupStep != null) {
        KeepingPopupOpenAction dontClosePopupAction = getActionByClass(selectedValue, actionPopupStep, KeepingPopupOpenAction.class);
        if (dontClosePopupAction != null) {
          actionPopupStep.performAction((AnAction)dontClosePopupAction, e != null ? e.getModifiers() : 0, e);
          for (ActionItem item : actionPopupStep.myItems) {
            updateActionItem(item);
          }
          getList().repaint();
          return;
        }
      }

      super.handleSelect(handleFinalChoices, e);
    }

    protected void handleToggleAction() {
      final Object[] selectedValues = getList().getSelectedValues();

      ListPopupStep<Object> listStep = getListStep();
      final ActionPopupStep actionPopupStep = ObjectUtils.tryCast(listStep, ActionPopupStep.class);
      if (actionPopupStep == null) return;

      List<ToggleAction> filtered = ContainerUtil.mapNotNull(selectedValues, o -> getActionByClass(o, actionPopupStep, ToggleAction.class));

      for (ToggleAction action : filtered) {
        actionPopupStep.performAction(action, 0);
      }

      for (ActionItem item : actionPopupStep.myItems) {
        updateActionItem(item);
      }

      getList().repaint();
    }

    public void installOnHoverIconsSupport(@NotNull IconListPopupRenderer iconListPopupRenderer) {
      //OnHover icons listener should be installed once
      assert myIconsHoverListener == null;
      myIconsHoverListener = new IconHoverListener(iconListPopupRenderer);
    }

    @Override
    protected boolean beforeShow() {
      getList().addMouseMotionListener(myIconsHoverListener);
      getList().addListSelectionListener(myIconsHoverListener);
      return super.beforeShow();
    }

    @Nullable
    private static <T> T getActionByClass(@Nullable Object value, @NotNull ActionPopupStep actionPopupStep, @NotNull Class<T> actionClass) {
      ActionItem item = value instanceof ActionItem ? (ActionItem)value : null;
      if (item == null) return null;
      if (!actionPopupStep.isSelectable(item)) return null;
      return actionClass.isInstance(item.getAction()) ? actionClass.cast(item.getAction()) : null;
    }

    private class IconHoverListener extends MouseMotionAdapter implements ListSelectionListener {
      @NotNull private IconListPopupRenderer myRenderer;

      public IconHoverListener(@NotNull IconListPopupRenderer renderer) {
        myRenderer = renderer;
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        Point point = e.getPoint();
        int index = getList().locationToIndex(point);
        Rectangle bounds = getList().getCellBounds(index, index);
        Object selectedValue = getList().getSelectedValue();
        if (selectedValue instanceof ActionItem) {
          ((ActionItem)selectedValue).setIconHovered(myRenderer.isIconAt(point));
        }
        if (bounds != null) {
          getList().repaint(bounds);
        }
      }

      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          int selected = getSelectedIndex();
          int unselected = e.getFirstIndex() == selected ? e.getLastIndex() : e.getFirstIndex();
          Object elementAt = getList().getModel().getElementAt(unselected);
          if (elementAt instanceof ActionItem) {
            ActionItem actionItem = (ActionItem)elementAt;
            actionItem.setIconHovered(false);
            getList().repaint();
          }
        }
      }
    }
  }

  @NotNull
  @Override
  public ListPopup createActionGroupPopup(final String title,
                                          @NotNull final ActionGroup actionGroup,
                                          @NotNull DataContext dataContext,
                                          boolean showNumbers,
                                          boolean showDisabledActions,
                                          boolean honorActionMnemonics,
                                          final Runnable disposeCallback,
                                          final int maxRowCount,
                                          final Condition<AnAction> preselectActionCondition) {
    return createActionGroupPopup(title, actionGroup, dataContext, showNumbers, true, showDisabledActions, honorActionMnemonics,
                                  disposeCallback, maxRowCount, preselectActionCondition, null);
  }

  @NotNull
  @Override
  public ListPopup createActionGroupPopup(String title,
                                          @NotNull ActionGroup actionGroup,
                                          @NotNull DataContext dataContext,
                                          ActionSelectionAid selectionAidMethod,
                                          boolean showDisabledActions) {
    return createActionGroupPopup(title, actionGroup, dataContext,
                                  selectionAidMethod == ActionSelectionAid.NUMBERING || selectionAidMethod == ActionSelectionAid.ALPHA_NUMBERING,
                                  selectionAidMethod == ActionSelectionAid.ALPHA_NUMBERING,
                                  showDisabledActions,
                                  selectionAidMethod == ActionSelectionAid.MNEMONICS,
                                  null, -1);
  }

  @NotNull
  @Override
  public ListPopup createActionGroupPopup(String title,
                                          @NotNull ActionGroup actionGroup,
                                          @NotNull DataContext dataContext,
                                          ActionSelectionAid selectionAidMethod,
                                          boolean showDisabledActions,
                                          @Nullable String actionPlace) {
    return createActionGroupPopup(title, actionGroup, dataContext, selectionAidMethod, showDisabledActions, null, -1, null, actionPlace);
  }

  @NotNull
  @Override
  public ListPopup createActionGroupPopup(String title,
                                          @NotNull ActionGroup actionGroup,
                                          @NotNull DataContext dataContext,
                                          ActionSelectionAid selectionAidMethod,
                                          boolean showDisabledActions,
                                          Runnable disposeCallback,
                                          int maxRowCount) {
    return createActionGroupPopup(title, actionGroup, dataContext, selectionAidMethod, showDisabledActions, disposeCallback, maxRowCount, null, null);
  }

  @NotNull
  @Override
  public ListPopupStep createActionsStep(@NotNull final ActionGroup actionGroup,
                                         @NotNull DataContext dataContext,
                                         final boolean showNumbers,
                                         final boolean showDisabledActions,
                                         final String title,
                                         final Component component,
                                         final boolean honorActionMnemonics) {
    return createActionsStep(actionGroup, dataContext, showNumbers, showDisabledActions, title, component, honorActionMnemonics, 0, false);
  }

  private static ListPopupStep createActionsStep(@NotNull ActionGroup actionGroup, @NotNull DataContext dataContext,
                                                 boolean showNumbers, boolean useAlphaAsNumbers, boolean showDisabledActions,
                                                 String title, Component component, boolean honorActionMnemonics,
                                                 final int defaultOptionIndex, final boolean autoSelectionEnabled) {
    final List<ActionItem> items = makeActionItemsFromActionGroup(actionGroup, dataContext, showNumbers, useAlphaAsNumbers,
                                                                  showDisabledActions, honorActionMnemonics);
    return new ActionPopupStep(items, title, component, showNumbers || honorActionMnemonics && itemsHaveMnemonics(items),
                               action -> defaultOptionIndex >= 0 &&
                                      defaultOptionIndex < items.size() &&
                                      items.get(defaultOptionIndex).getAction().equals(action), autoSelectionEnabled, showDisabledActions);
  }

  @NotNull
  private static List<ActionItem> makeActionItemsFromActionGroup(@NotNull ActionGroup actionGroup,
                                                                 @NotNull DataContext dataContext,
                                                                 boolean showNumbers,
                                                                 boolean useAlphaAsNumbers,
                                                                 boolean showDisabledActions,
                                                                 boolean honorActionMnemonics) {
    final ActionStepBuilder builder = new ActionStepBuilder(dataContext, showNumbers, useAlphaAsNumbers, showDisabledActions,
                                                            honorActionMnemonics);
    builder.buildGroup(actionGroup);
    return builder.getItems();
  }

  @NotNull
  private static ListPopupStep createActionsStep(@NotNull ActionGroup actionGroup, @NotNull DataContext dataContext,
                                                 boolean showNumbers, boolean useAlphaAsNumbers, boolean showDisabledActions,
                                                 String title, Component component, boolean honorActionMnemonics,
                                                 Condition<AnAction> preselectActionCondition, boolean autoSelectionEnabled) {
    final List<ActionItem> items = makeActionItemsFromActionGroup(actionGroup, dataContext, showNumbers, useAlphaAsNumbers,
                                                                  showDisabledActions, honorActionMnemonics);
    return new ActionPopupStep(items, title, component, showNumbers || honorActionMnemonics && itemsHaveMnemonics(items), preselectActionCondition,
                               autoSelectionEnabled, showDisabledActions);
  }

  @NotNull
  @Override
  public ListPopupStep createActionsStep(@NotNull ActionGroup actionGroup, @NotNull DataContext dataContext, boolean showNumbers, boolean showDisabledActions,
                                         String title, Component component, boolean honorActionMnemonics, int defaultOptionIndex,
                                         final boolean autoSelectionEnabled) {
    return createActionsStep(actionGroup, dataContext, showNumbers, true, showDisabledActions, title, component, honorActionMnemonics,
                             defaultOptionIndex, autoSelectionEnabled);
  }

  private static boolean itemsHaveMnemonics(final List<ActionItem> items) {
    for (ActionItem item : items) {
      if (item.getAction().getTemplatePresentation().getMnemonic() != 0) return true;
    }

    return false;
  }

  @NotNull
  @Override
  public ListPopup createWizardStep(@NotNull PopupStep step) {
    return new ListPopupImpl((ListPopupStep)step);
  }

  @NotNull
  @Override
  public ListPopup createListPopup(@NotNull ListPopupStep step) {
    return new ListPopupImpl(step);
  }

  @NotNull
  @Override
  public ListPopup createListPopup(@NotNull ListPopupStep step, int maxRowCount) {
    return new ListPopupImpl(step, maxRowCount);
  }

  @NotNull
  @Override
  public TreePopup createTree(JBPopup parent, @NotNull TreePopupStep aStep, Object parentValue) {
    return new TreePopupImpl(parent, aStep, parentValue);
  }

  @NotNull
  @Override
  public TreePopup createTree(@NotNull TreePopupStep aStep) {
    return new TreePopupImpl(aStep);
  }

  @NotNull
  @Override
  public ComponentPopupBuilder createComponentPopupBuilder(@NotNull JComponent content, JComponent prefferableFocusComponent) {
    return new ComponentPopupBuilderImpl(content, prefferableFocusComponent);
  }


  @NotNull
  @Override
  public RelativePoint guessBestPopupLocation(@NotNull DataContext dataContext) {
    Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    JComponent focusOwner = component instanceof JComponent ? (JComponent)component : null;

    if (focusOwner == null) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      IdeFrameImpl frame = project == null ? null : ((WindowManagerEx)WindowManager.getInstance()).getFrame(project);
      focusOwner = frame == null ? null : frame.getRootPane();
      if (focusOwner == null) {
        throw new IllegalArgumentException("focusOwner cannot be null");
      }
    }

    final Point point = PlatformDataKeys.CONTEXT_MENU_POINT.getData(dataContext);
    if (point != null) {
      return new RelativePoint(focusOwner, point);
    }

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor != null && focusOwner == editor.getContentComponent()) {
      return guessBestPopupLocation(editor);
    }
    else {
      return guessBestPopupLocation(focusOwner);
    }
  }

  @NotNull
  @Override
  public RelativePoint guessBestPopupLocation(@NotNull final JComponent component) {
    Point popupMenuPoint = null;
    final Rectangle visibleRect = component.getVisibleRect();
    if (component instanceof JList) { // JList
      JList list = (JList)component;
      int firstVisibleIndex = list.getFirstVisibleIndex();
      int lastVisibleIndex = list.getLastVisibleIndex();
      int[] selectedIndices = list.getSelectedIndices();
      for (int index : selectedIndices) {
        if (firstVisibleIndex <= index && index <= lastVisibleIndex) {
          Rectangle cellBounds = list.getCellBounds(index, index);
          popupMenuPoint = new Point(visibleRect.x + visibleRect.width / 4, cellBounds.y + cellBounds.height);
          break;
        }
      }
    }
    else if (component instanceof JTree) { // JTree
      JTree tree = (JTree)component;
      int[] selectionRows = tree.getSelectionRows();
      if (selectionRows != null) {
        Arrays.sort(selectionRows);
        for (int i = 0; i < selectionRows.length; i++) {
          int row = selectionRows[i];
          Rectangle rowBounds = tree.getRowBounds(row);
          if (visibleRect.contains(rowBounds)) {
            popupMenuPoint = new Point(rowBounds.x + 2, rowBounds.y + rowBounds.height - 1);
            break;
          }
        }
        if (popupMenuPoint == null) {//All selected rows are out of visible rect
          Point visibleCenter = new Point(visibleRect.x + visibleRect.width / 2, visibleRect.y + visibleRect.height / 2);
          double minDistance = Double.POSITIVE_INFINITY;
          int bestRow = -1;
          Point rowCenter;
          double distance;
          for (int i = 0; i < selectionRows.length; i++) {
            int row = selectionRows[i];
            Rectangle rowBounds = tree.getRowBounds(row);
            rowCenter = new Point(rowBounds.x + rowBounds.width / 2, rowBounds.y + rowBounds.height / 2);
            distance = visibleCenter.distance(rowCenter);
            if (minDistance > distance) {
              minDistance = distance;
              bestRow = row;
            }
          }

          if (bestRow != -1) {
            Rectangle rowBounds = tree.getRowBounds(bestRow);
            tree.scrollRectToVisible(
              new Rectangle(rowBounds.x, rowBounds.y, Math.min(visibleRect.width, rowBounds.width), rowBounds.height));
            popupMenuPoint = new Point(rowBounds.x + 2, rowBounds.y + rowBounds.height - 1);
          }
        }
      }
    }
    else if (component instanceof JTable) {
      JTable table = (JTable)component;
      int column = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
      int row = Math.max(table.getSelectionModel().getLeadSelectionIndex(), table.getSelectionModel().getAnchorSelectionIndex());
      Rectangle rect = table.getCellRect(row, column, false);
      if (!visibleRect.intersects(rect)) {
        table.scrollRectToVisible(rect);
      }
      popupMenuPoint = new Point(rect.x, rect.y + rect.height);
    }
    else if (component instanceof PopupOwner) {
      popupMenuPoint = ((PopupOwner)component).getBestPopupPosition();
    }
    if (popupMenuPoint == null) {
      popupMenuPoint = new Point(visibleRect.x + visibleRect.width / 2, visibleRect.y + visibleRect.height / 2);
    }

    return new RelativePoint(component, popupMenuPoint);
  }

  @Override
  public boolean isBestPopupLocationVisible(@NotNull Editor editor) {
    return getVisibleBestPopupLocation(editor) != null;
  }

  @NotNull
  @Override
  public RelativePoint guessBestPopupLocation(@NotNull Editor editor) {
    Point p = getVisibleBestPopupLocation(editor);
    if (p == null) {
      final Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
      p = new Point(visibleArea.x + visibleArea.width / 3, visibleArea.y + visibleArea.height / 2);
    }
    return new RelativePoint(editor.getContentComponent(), p);
  }

  @Nullable
  private static Point getVisibleBestPopupLocation(@NotNull Editor editor) {
    VisualPosition visualPosition = editor.getUserData(ANCHOR_POPUP_POSITION);

    if (visualPosition == null) {
      CaretModel caretModel = editor.getCaretModel();
      if (caretModel.isUpToDate()) {
        visualPosition = caretModel.getVisualPosition();
      }
      else {
        visualPosition = editor.offsetToVisualPosition(caretModel.getOffset());
      }
    }

    Point p = editor.visualPositionToXY(new VisualPosition(visualPosition.line + 1, visualPosition.column));

    final Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    return !visibleArea.contains(p) && !visibleArea.contains(p.x, p.y - editor.getLineHeight())
           ? null : p;
  }

  @Override
  public Point getCenterOf(JComponent container, JComponent content) {
    return AbstractPopup.getCenterOf(container, content);
  }

  public static class ActionItem implements ShortcutProvider {
    private final AnAction myAction;
    private String myText;
    private final boolean myIsEnabled;
    @Nullable private ActionStepBuilder.IconWrapper myIcon;
    private final boolean myPrependWithSeparator;
    private final String mySeparatorText;
    private final String myDescription;

    private ActionItem(@NotNull AnAction action,
                       @NotNull String text,
                       @Nullable String description,
                       boolean enabled,
                       @Nullable ActionStepBuilder.IconWrapper icon,
                       final boolean prependWithSeparator,
                       String separatorText) {
      myAction = action;
      myText = text;
      myIsEnabled = enabled;
      myIcon = icon;
      myPrependWithSeparator = prependWithSeparator;
      mySeparatorText = separatorText;
      myDescription = description;
      myAction.getTemplatePresentation().addPropertyChangeListener(new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          if (evt.getPropertyName() == PROP_ICON || evt.getPropertyName() == PROP_HOVERED_ICON) {
            updateIcons();
          }
          else if (evt.getPropertyName() == PROP_TEXT) {
            myText = myAction.getTemplatePresentation().getText();
          }
        }
      });
    }

    private void updateIcons() {
      // we can't set icons if it hasn't existed before, because alignment will be destroyed; use IconWrapper with null icon instead of null
      if (myIcon == null) return;
      Presentation presentation = myAction.getTemplatePresentation();
      myIcon.setIcons(presentation.getIcon(), presentation.getHoveredIcon());
    }

    @NotNull
    public AnAction getAction() {
      return myAction;
    }

    @NotNull
    public String getText() {
      return myText;
    }

    @Nullable
    public ActionStepBuilder.IconWrapper getIcon() {
      return myIcon;
    }

    public boolean isPrependWithSeparator() {
      return myPrependWithSeparator;
    }

    public String getSeparatorText() {
      return mySeparatorText;
    }

    public boolean isEnabled() { return myIsEnabled; }

    public String getDescription() {
      return myDescription;
    }

    @Nullable
    @Override
    public ShortcutSet getShortcut() {
      return myAction.getShortcutSet();
    }

    @Override
    public String toString() {
      return myText;
    }

    public void setIconHovered(boolean isHovered) {
      if (myIcon != null) {
        myIcon.setHovered(isHovered);
      }
    }
  }

  private static class ActionPopupStep implements ListPopupStepEx<ActionItem>, MnemonicNavigationFilter<ActionItem>, SpeedSearchFilter<ActionItem> {
    private final List<ActionItem> myItems;
    private final String myTitle;
    private final Component myContext;
    private final boolean myEnableMnemonics;
    private final int myDefaultOptionIndex;
    private final boolean myAutoSelectionEnabled;
    private final boolean myShowDisabledActions;
    private Runnable myFinalRunnable;
    @Nullable private final Condition<AnAction> myPreselectActionCondition;

    private ActionPopupStep(@NotNull final List<ActionItem> items, final String title, Component context, boolean enableMnemonics,
                            @Nullable Condition<AnAction> preselectActionCondition, final boolean autoSelection, boolean showDisabledActions) {
      myItems = items;
      myTitle = title;
      myContext = context;
      myEnableMnemonics = enableMnemonics;
      myDefaultOptionIndex = getDefaultOptionIndexFromSelectCondition(preselectActionCondition, items);
      myPreselectActionCondition = preselectActionCondition;
      myAutoSelectionEnabled = autoSelection;
      myShowDisabledActions = showDisabledActions;
    }

    private static int getDefaultOptionIndexFromSelectCondition(@Nullable Condition<AnAction> preselectActionCondition,
                                                                @NotNull List<ActionItem> items) {
      int defaultOptionIndex = 0;
      if (preselectActionCondition != null) {
        for (int i = 0; i < items.size(); i++) {
          final AnAction action = items.get(i).getAction();
          if (preselectActionCondition.value(action)) {
            defaultOptionIndex = i;
            break;
          }
        }
      }
      return defaultOptionIndex;
    }

    @Override
    @NotNull
    public List<ActionItem> getValues() {
      return myItems;
    }

    @Override
    public boolean isSelectable(final ActionItem value) {
      return value.isEnabled();
    }

    @Override
    public int getMnemonicPos(final ActionItem value) {
      final String text = getTextFor(value);
      int i = text.indexOf(UIUtil.MNEMONIC);
      if (i < 0) {
        i = text.indexOf('&');
      }
      if (i < 0) {
        i = text.indexOf('_');
      }
      return i;
    }

    @Override
    public Icon getIconFor(final ActionItem aValue) {
      return aValue.getIcon();
    }

    @Override
    @NotNull
    public String getTextFor(final ActionItem value) {
      return value.getText();
    }

    @Nullable
    @Override
    public String getTooltipTextFor(ActionItem value) {
      return value.getDescription();
    }

    @Override
    public void setEmptyText(@NotNull StatusText emptyText) {
    }

    @Override
    public ListSeparator getSeparatorAbove(final ActionItem value) {
      return value.isPrependWithSeparator() ? new ListSeparator(value.getSeparatorText()) : null;
    }

    @Override
    public int getDefaultOptionIndex() {
      return myDefaultOptionIndex;
    }

    @Override
    public String getTitle() {
      return myTitle;
    }

    @Override
    public PopupStep onChosen(final ActionItem actionChoice, final boolean finalChoice) {
      return onChosen(actionChoice, finalChoice, 0);
    }

    @Override
    public PopupStep onChosen(ActionItem actionChoice, boolean finalChoice, final int eventModifiers) {
      if (!actionChoice.isEnabled()) return FINAL_CHOICE;
      final AnAction action = actionChoice.getAction();
      DataManager mgr = DataManager.getInstance();

      final DataContext dataContext = myContext != null ? mgr.getDataContext(myContext) : mgr.getDataContext();

      if (action instanceof ActionGroup && (!finalChoice || !((ActionGroup)action).canBePerformed(dataContext))) {
          return createActionsStep((ActionGroup)action, dataContext, myEnableMnemonics, true, myShowDisabledActions, null, myContext, false,
                                   myPreselectActionCondition, false);
      }
      else {
        myFinalRunnable = () -> performAction(action, eventModifiers);
        return FINAL_CHOICE;
      }
    }

    public void performAction(@NotNull AnAction action, int modifiers) {
      performAction(action, modifiers, null);
    }

    public void performAction(@NotNull AnAction action, int modifiers, InputEvent inputEvent) {
      final DataManager mgr = DataManager.getInstance();
      final DataContext dataContext = myContext != null ? mgr.getDataContext(myContext) : mgr.getDataContext();
      final AnActionEvent event = new AnActionEvent(inputEvent, dataContext, ActionPlaces.UNKNOWN, action.getTemplatePresentation().clone(),
                                                    ActionManager.getInstance(), modifiers);
      event.setInjectedContext(action.isInInjectedContext());
      if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
        ActionUtil.performActionDumbAware(action, event);
      }
    }

    @Override
    public Runnable getFinalRunnable() {
      return myFinalRunnable;
    }

    @Override
    public boolean hasSubstep(final ActionItem selectedValue) {
      return selectedValue != null && selectedValue.isEnabled() && selectedValue.getAction() instanceof ActionGroup;
    }

    @Override
    public void canceled() {
    }

    @Override
    public boolean isMnemonicsNavigationEnabled() {
      return myEnableMnemonics;
    }

    @Override
    public MnemonicNavigationFilter<ActionItem> getMnemonicNavigationFilter() {
      return this;
    }

    @Override
    public boolean canBeHidden(final ActionItem value) {
      return true;
    }

    @Override
    public String getIndexedString(final ActionItem value) {
      return getTextFor(value);
    }

    @Override
    public boolean isSpeedSearchEnabled() {
      return true;
    }

    @Override
    public boolean isAutoSelectionEnabled() {
      return myAutoSelectionEnabled;
    }

    @Override
    public SpeedSearchFilter<ActionItem> getSpeedSearchFilter() {
      return this;
    }
  }

  @Override
  @NotNull
  public List<JBPopup> getChildPopups(@NotNull final Component component) {
    return FocusTrackback.getChildPopups(component);
  }

  @Override
  public boolean isPopupActive() {
  return IdeEventQueue.getInstance().isPopupActive();
  }

  private static class ActionStepBuilder {
    private final List<ActionItem>                myListModel;
    private final DataContext                     myDataContext;
    private final boolean                         myShowNumbers;
    private final boolean                         myUseAlphaAsNumbers;
    private final boolean                         myShowDisabled;
    private final HashMap<AnAction, Presentation> myAction2presentation;
    private       int                             myCurrentNumber;
    private       boolean                         myPrependWithSeparator;
    private       String                          mySeparatorText;
    private final boolean                         myHonorActionMnemonics;
    private IconWrapper myEmptyIcon;
    private int myMaxIconWidth  = -1;
    private int myMaxIconHeight = -1;
    @NotNull private String myActionPlace;

    private ActionStepBuilder(@NotNull DataContext dataContext, final boolean showNumbers, final boolean useAlphaAsNumbers,
                              final boolean showDisabled, final boolean honorActionMnemonics)
    {
      myUseAlphaAsNumbers = useAlphaAsNumbers;
      myListModel = new ArrayList<>();
      myDataContext = dataContext;
      myShowNumbers = showNumbers;
      myShowDisabled = showDisabled;
      myAction2presentation = new HashMap<>();
      myCurrentNumber = 0;
      myPrependWithSeparator = false;
      mySeparatorText = null;
      myHonorActionMnemonics = honorActionMnemonics;
      myActionPlace = ActionPlaces.UNKNOWN;
    }

    public void setActionPlace(@NotNull String actionPlace) {
      myActionPlace = actionPlace;
    }

    @NotNull
    public List<ActionItem> getItems() {
      return myListModel;
    }

    public void buildGroup(@NotNull ActionGroup actionGroup) {
      calcMaxIconSize(actionGroup);
      myEmptyIcon = myMaxIconHeight != -1 && myMaxIconWidth != -1 ? createWrapper(null) : null;

      appendActionsFromGroup(actionGroup);

      if (myListModel.isEmpty()) {
        myListModel.add(new ActionItem(Utils.EMPTY_MENU_FILLER, Utils.NOTHING_HERE, null, false, null, false, null));
      }
    }

    private void calcMaxIconSize(final ActionGroup actionGroup) {
      AnAction[] actions = actionGroup.getChildren(createActionEvent(actionGroup));
      for (AnAction action : actions) {
        if (action == null) continue;
        if (action instanceof ActionGroup) {
          final ActionGroup group = (ActionGroup)action;
          if (!group.isPopup()) {
            calcMaxIconSize(group);
            continue;
          }
        }

        Icon icon = action.getTemplatePresentation().getIcon();
        if (icon == null && action instanceof Toggleable) icon = PlatformIcons.CHECK_ICON;
        if (icon != null) {
          final int width = icon.getIconWidth();
          final int height = icon.getIconHeight();
          if (myMaxIconWidth < width) {
            myMaxIconWidth = width;
          }
          if (myMaxIconHeight < height) {
            myMaxIconHeight = height;
          }
        }
      }
    }

    @NotNull
    private AnActionEvent createActionEvent(@NotNull AnAction actionGroup) {
      final AnActionEvent actionEvent =
        new AnActionEvent(null, myDataContext, myActionPlace, getPresentation(actionGroup), ActionManager.getInstance(), 0);
      actionEvent.setInjectedContext(actionGroup.isInInjectedContext());
      return actionEvent;
    }

    private void appendActionsFromGroup(@NotNull ActionGroup actionGroup) {
      AnAction[] actions = actionGroup.getChildren(createActionEvent(actionGroup));
      for (AnAction action : actions) {
        if (action == null) {
          LOG.error("null action in group " + actionGroup);
          continue;
        }
        if (action instanceof Separator) {
          myPrependWithSeparator = true;
          mySeparatorText = ((Separator)action).getText();
        }
        else {
          if (action instanceof ActionGroup) {
            ActionGroup group = (ActionGroup)action;
            if (group.isPopup()) {
              appendAction(group);
            }
            else {
              appendActionsFromGroup(group);
            }
          }
          else {
            appendAction(action);
          }
        }
      }
    }

    private void appendAction(@NotNull AnAction action) {
      Presentation presentation = getPresentation(action);
      AnActionEvent event = createActionEvent(action);

      ActionUtil.performDumbAwareUpdate(LaterInvocator.isInModalContext(), action, event, true);
      if ((myShowDisabled || presentation.isEnabled()) && presentation.isVisible()) {
        String text = presentation.getText();
        if (myShowNumbers) {
          if (myCurrentNumber < 9) {
            text = "&" + (myCurrentNumber + 1) + ". " + text;
          }
          else if (myCurrentNumber == 9) {
            text = "&" + 0 + ". " + text;
          }
          else if (myUseAlphaAsNumbers) {
            text = "&" + (char)('A' + myCurrentNumber - 10) + ". " + text;
          }
          myCurrentNumber++;
        }
        else if (myHonorActionMnemonics) {
          text = Presentation.restoreTextWithMnemonic(text, action.getTemplatePresentation().getMnemonic());
        }

        Icon icon = presentation.isEnabled() ? presentation.getIcon() : IconLoader.getDisabledIcon(presentation.getIcon());
        IconWrapper iconWrapper;
        if (icon == null && presentation.getHoveredIcon() == null) {
          @NonNls final String actionId = ActionManager.getInstance().getId(action);
          if (actionId != null && actionId.startsWith("QuickList.")) {
            iconWrapper = createWrapper(AllIcons.Actions.QuickList);
          }
          else if (action instanceof Toggleable) {
            boolean toggled = Boolean.TRUE.equals(presentation.getClientProperty(Toggleable.SELECTED_PROPERTY));
            iconWrapper = toggled ? createWrapper(PlatformIcons.CHECK_ICON) : myEmptyIcon;
          }
          else {
            iconWrapper = myEmptyIcon;
          }
        }
        else {
          iconWrapper = new IconWrapper(icon, presentation.getHoveredIcon(), myMaxIconWidth, myMaxIconHeight);
        }
        boolean prependSeparator = (!myListModel.isEmpty() || mySeparatorText != null) && myPrependWithSeparator;
        assert text != null : action + " has no presentation";
        myListModel.add(
          new ActionItem(action, text, (String)presentation.getClientProperty(JComponent.TOOL_TIP_TEXT_KEY), presentation.isEnabled(), iconWrapper,
                         prependSeparator, mySeparatorText));
        myPrependWithSeparator = false;
        mySeparatorText = null;
      }
    }

    /**
     * Adjusts icon size to maximum, so that icons with different sizes were aligned correctly.
     */
    public static class IconWrapper extends IconUtil.IconSizeWrapper {

      @Nullable private Icon myIcon;
      @Nullable private Icon myHoverIcon;

      private boolean isHovered;

      public IconWrapper(@Nullable Icon icon, @Nullable Icon hoverIcon, int width, int height) {
        super(null, width, height);
        setIcons(icon, hoverIcon);
      }

      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        paintIcon(myHoverIcon != null && isHovered ? myHoverIcon : myIcon, c, g, x, y);
      }

      public boolean isHovered() {
        return isHovered;
      }

      public void setHovered(boolean hovered) {
        isHovered = hovered;
      }

      public void setIcons(@Nullable Icon icon, @Nullable Icon hoveredIcon) {
        myIcon = icon;
        myHoverIcon = hoveredIcon;
      }
    }

    @NotNull
    public IconWrapper createWrapper(@Nullable Icon icon) {
      return new IconWrapper(icon, null, myMaxIconWidth, myMaxIconHeight);
    }

    private Presentation getPresentation(@NotNull AnAction action) {
      Presentation presentation = myAction2presentation.get(action);
      if (presentation == null) {
        presentation = action.getTemplatePresentation().clone();
        myAction2presentation.put(action, presentation);
      }
      return presentation;
    }
  }

  @NotNull
  @Override
  public BalloonBuilder createBalloonBuilder(@NotNull final JComponent content) {
    return new BalloonPopupBuilderImpl(myStorage, content);
  }

  @NotNull
  @Override
  public BalloonBuilder createDialogBalloonBuilder(@NotNull JComponent content, String title) {
    final BalloonPopupBuilderImpl builder = new BalloonPopupBuilderImpl(myStorage, content);
    final Color bg = UIManager.getColor("Panel.background");
    final Color borderOriginal = Color.darkGray;
    final Color border = ColorUtil.toAlpha(borderOriginal, 75);
    builder
      .setDialogMode(true)
      .setTitle(title)
      .setAnimationCycle(200)
      .setFillColor(bg).setBorderColor(border).setHideOnClickOutside(false)
      .setHideOnKeyOutside(false)
      .setHideOnAction(false)
      .setCloseButtonEnabled(true)
      .setShadow(true);

    return builder;
  }

  @NotNull
  @Override
  public BalloonBuilder createHtmlTextBalloonBuilder(@NotNull final String htmlContent, @Nullable final Icon icon, final Color fillColor,
                                                     @Nullable final HyperlinkListener listener) {
    JEditorPane text = IdeTooltipManager.initPane(htmlContent, new HintHint().setAwtTooltip(true), null);

    if (listener != null) {
      text.addHyperlinkListener(listener);
    }
    text.setEditable(false);
    NonOpaquePanel.setTransparent(text);
    text.setBorder(null);


    JLabel label = new JLabel();
    final JPanel content = new NonOpaquePanel(new BorderLayout((int)(label.getIconTextGap() * 1.5), (int)(label.getIconTextGap() * 1.5)));

    final NonOpaquePanel textWrapper = new NonOpaquePanel(new GridBagLayout());
    JScrollPane scrolledText = new JScrollPane(text);
    scrolledText.setBackground(fillColor);
    scrolledText.getViewport().setBackground(fillColor);
    scrolledText.getViewport().setBorder(null);
    scrolledText.setBorder(null);
    textWrapper.add(scrolledText);
    content.add(textWrapper, BorderLayout.CENTER);

    final NonOpaquePanel north = new NonOpaquePanel(new BorderLayout());
    north.add(new JLabel(icon), BorderLayout.NORTH);
    content.add(north, BorderLayout.WEST);

    content.setBorder(new EmptyBorder(2, 4, 2, 4));

    final BalloonBuilder builder = createBalloonBuilder(content);

    builder.setFillColor(fillColor);

    return builder;
  }

  @NotNull
  @Override
  public BalloonBuilder createHtmlTextBalloonBuilder(@NotNull String htmlContent,
                                                     MessageType messageType,
                                                     @Nullable HyperlinkListener listener)
  {
    return createHtmlTextBalloonBuilder(htmlContent, messageType.getDefaultIcon(), messageType.getPopupBackground(), listener);
  }
}
