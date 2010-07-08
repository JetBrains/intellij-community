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
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.ex.WeakKeymapManagerListener;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.switcher.SwitchTarget;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class ActionToolbarImpl extends JPanel implements ActionToolbar {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.actionSystem.impl.ActionToolbarImpl");

  /**
   * This array contains Rectangles which define bounds of the corresponding
   * components in the toolbar. This list can be considerer as a cache of the
   * Rectangle objects that are used in calculation of preferred sizes and
   * layouting of components.
   */
  private final ArrayList<Rectangle> myComponentBounds = new ArrayList<Rectangle>();

  private Dimension myMinimumButtonSize;
  /**
   * @see ActionToolbar#getLayoutPolicy()
   */
  private int myLayoutPolicy;
  private int myOrientation;
  private final ActionGroup myActionGroup;
  private final String myPlace;
  @SuppressWarnings({"FieldCanBeLocal"}) private final MyKeymapManagerListener myKeymapManagerListener;
  private ArrayList<AnAction> myNewVisibleActions;
  protected ArrayList<AnAction> myVisibleActions;
  private final PresentationFactory myPresentationFactory;
  /**
   * @see ActionToolbar#adjustTheSameSize(boolean)
   */
  private boolean myAdjustTheSameSize;

  private final ActionButtonLook myButtonLook = null;
  private final DataManager myDataManager;
  protected final ActionManagerEx myActionManager;

  private Rectangle myAutoPopupRec;

  private static final Icon myAutoPopupIcon = IconLoader.getIcon("/ide/link.png");
  private static final Icon mySecondaryGroupIcon = IconLoader.getIcon("/general/secondaryGroup.png");
  private final DefaultActionGroup mySecondaryActions = new DefaultActionGroup();
  private ActionButton mySecondaryActionsButton;

  private final KeymapManagerEx myKeymapManager;
  private int myFirstOusideIndex = -1;

  private JBPopup myPopup;
  private JComponent myTargetComponent;

  private boolean myReservePlaceAutoPopupIcon = true;
  private WeakTimerListener myWeakTimerListener;
  private ActionToolbarImpl.MyTimerListener myTimerListener;

  public ActionToolbarImpl(final String place,
                           final ActionGroup actionGroup,
                           final boolean horizontal,
                           DataManager dataManager,
                           ActionManagerEx actionManager,
                           KeymapManagerEx keymapManager) {
    this(place, actionGroup, horizontal, dataManager, actionManager, keymapManager, false);
  }

  public ActionToolbarImpl(final String place,
                           final ActionGroup actionGroup,
                           final boolean horizontal,
                           DataManager dataManager,
                           ActionManagerEx actionManager,
                           KeymapManagerEx keymapManager,
                           boolean updateActionsNow) {
    super(null);
    myActionManager = actionManager;
    myKeymapManager = keymapManager;
    setMinimumButtonSize(DEFAULT_MINIMUM_BUTTON_SIZE);
    setLayoutPolicy(AUTO_LAYOUT_POLICY);
    setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    myPlace = place;
    myActionGroup = actionGroup;
    myPresentationFactory = new PresentationFactory();
    myKeymapManagerListener = new MyKeymapManagerListener();
    myVisibleActions = new ArrayList<AnAction>();
    myNewVisibleActions = new ArrayList<AnAction>();
    myDataManager = dataManager;

    setLayout(new BorderLayout());
    setOrientation(horizontal ? SwingConstants.HORIZONTAL : SwingConstants.VERTICAL);

    mySecondaryActions.getTemplatePresentation().setIcon(mySecondaryGroupIcon);
    mySecondaryActions.setPopup(true);

    updateActions(updateActionsNow, false);

    //
    keymapManager.addKeymapManagerListener(new WeakKeymapManagerListener(keymapManager, myKeymapManagerListener));
    myTimerListener = new MyTimerListener();
    myWeakTimerListener = new WeakTimerListener(actionManager, myTimerListener);
    // If the panel doesn't handle mouse event then it will be passed to its parent.
    // It means that if the panel is in slidindg mode then the focus goes to the editor
    // and panel will be automatically hidden.
    enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.COMPONENT_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    myActionManager.addTimerListener(500, myWeakTimerListener);
    myActionManager.addTransparrentTimerListener(500, myWeakTimerListener);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    myActionManager.removeTimerListener(myWeakTimerListener);
    myActionManager.removeTransparrentTimerListener(myWeakTimerListener);
  }

  public JComponent getComponent() {
    return this;
  }

  public int getLayoutPolicy() {
    return myLayoutPolicy;
  }

  public void setLayoutPolicy(final int layoutPolicy) {
    if (layoutPolicy != NOWRAP_LAYOUT_POLICY && layoutPolicy != WRAP_LAYOUT_POLICY && layoutPolicy != AUTO_LAYOUT_POLICY) {
      throw new IllegalArgumentException("wrong layoutPolicy: " + layoutPolicy);
    }
    myLayoutPolicy = layoutPolicy;
  }

  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);

    if (myLayoutPolicy == AUTO_LAYOUT_POLICY) {
      if (myAutoPopupRec != null) {
        if (myOrientation == SwingConstants.HORIZONTAL) {
          final int dy = myAutoPopupRec.height / 2 - myAutoPopupIcon.getIconHeight() / 2;
          myAutoPopupIcon.paintIcon(this, g, (int)myAutoPopupRec.getMaxX() - myAutoPopupIcon.getIconWidth() - 1, myAutoPopupRec.y + dy);
        }
        else {
          final int dx = myAutoPopupRec.width / 2 - myAutoPopupIcon.getIconWidth() / 2;
          myAutoPopupIcon.paintIcon(this, g, myAutoPopupRec.x + dx, (int)myAutoPopupRec.getMaxY() - myAutoPopupIcon.getIconWidth() - 1);
        }
      }
    }
  }

  private void fillToolBar(final ArrayList<AnAction> actions, boolean layoutSecondaries) {
    for (int i = 0; i < actions.size(); i++) {
      final AnAction action = actions.get(i);

      if (layoutSecondaries) {
        if (!myActionGroup.isPrimary(action)) {
          mySecondaryActions.add(action);
          continue;
        }
      }

      if (action instanceof Separator) {
        if (i > 0 && i < actions.size() - 1) {
          add(new MySeparator());
        }
      }
      else if (action instanceof CustomComponentAction) {
        add(((CustomComponentAction)action).createCustomComponent(myPresentationFactory.getPresentation(action)));
      }
      else {
        final ActionButton button = createToolbarButton(action);
        add(button);
      }
    }

    if (mySecondaryActions.getChildrenCount() > 0) {
      mySecondaryActionsButton = new SecondaryButton(mySecondaryActions, myPresentationFactory.getPresentation(mySecondaryActions), myPlace, DEFAULT_MINIMUM_BUTTON_SIZE);
      mySecondaryActionsButton.setNoIconsInPopup(true);
      add(mySecondaryActionsButton);
    }
  }

  public ActionButton createToolbarButton(final AnAction action, final ActionButtonLook look, final String place, final Presentation presentation, final Dimension minimumSize) {
    if (action.displayTextInToolbar()) {
      return new ActionButtonWithText(action, presentation, place, minimumSize);
    }

    final ActionButton actionButton = new ActionButton(action, presentation, place, minimumSize) {
      protected DataContext getDataContext() {
        return getToolbarDataContext();
      }
    };
    actionButton.setLook(look);
    return actionButton;
  }

  private ActionButton createToolbarButton(final AnAction action) {
    return createToolbarButton(action, myButtonLook, myPlace, myPresentationFactory.getPresentation(action), myMinimumButtonSize);
  }

  public void doLayout() {
    if (!isValid()) {
      calculateBounds(getSize(), myComponentBounds);
    }
    final int componentCount = getComponentCount();
    LOG.assertTrue(componentCount <= myComponentBounds.size());
    for (int i = componentCount - 1; i >= 0; i--) {
      final Component component = getComponent(i);
      component.setBounds(myComponentBounds.get(i));
    }
  }

  public void validate() {
    if (!isValid()) {
      calculateBounds(getSize(), myComponentBounds);
      super.validate();
    }
  }

  /**
   * @return maximum button width
   */
  private int getMaxButtonWidth() {
    int width = 0;
    for (int i = 0; i < getComponentCount(); i++) {
      final Dimension dimension = getComponent(i).getPreferredSize();
      width = Math.max(width, dimension.width);
    }
    return width;
  }

  /**
   * @return maximum button height
   */
  public int getMaxButtonHeight() {
    int height = 0;
    for (int i = 0; i < getComponentCount(); i++) {
      final Dimension dimension = getComponent(i).getPreferredSize();
      height = Math.max(height, dimension.height);
    }
    return height;
  }

  private void calculateBoundsNowrapImpl(ArrayList<Rectangle> bounds) {
    final int componentCount = getComponentCount();
    LOG.assertTrue(componentCount <= bounds.size());

    final int width = getWidth();
    final int height = getHeight();

    if (myAdjustTheSameSize) {
      final int maxWidth = getMaxButtonWidth();
      final int maxHeight = getMaxButtonHeight();

      if (myOrientation == SwingConstants.HORIZONTAL) {
        int xOffset = 0;
        for (int i = 0; i < componentCount; i++) {
          final Rectangle r = bounds.get(i);
          r.setBounds(xOffset, (height - maxHeight) / 2, maxWidth, maxHeight);
          xOffset += maxWidth;
        }
      }
      else {
        int yOffset = 0;
        for (int i = 0; i < componentCount; i++) {
          final Rectangle r = bounds.get(i);
          r.setBounds((width - maxWidth) / 2, yOffset, maxWidth, maxHeight);
          yOffset += maxHeight;
        }
      }
    }
    else {
      if (myOrientation == SwingConstants.HORIZONTAL) {
        final int maxHeight = getMaxButtonHeight();
        int xOffset = 0;
        final int yOffset = 0;
        for (int i = 0; i < componentCount; i++) {
          final Component component = getComponent(i);
          final Dimension d = component.getPreferredSize();
          final Rectangle r = bounds.get(i);
          r.setBounds(xOffset, yOffset + (maxHeight - d.height) / 2, d.width, d.height);
          xOffset += d.width;
        }
      }
      else {
        final int maxWidth = getMaxButtonWidth();
        final int xOffset = 0;
        int yOffset = 0;
        for (int i = 0; i < componentCount; i++) {
          final Component component = getComponent(i);
          final Dimension d = component.getPreferredSize();
          final Rectangle r = bounds.get(i);
          r.setBounds(xOffset + (maxWidth - d.width) / 2, yOffset, d.width, d.height);
          yOffset += d.height;
        }
      }
    }
  }

  private void calculateBoundsAutoImp(Dimension sizeToFit, ArrayList<Rectangle> bounds) {
    final int componentCount = getComponentCount();
    LOG.assertTrue(componentCount <= bounds.size());

    final boolean actualLayout = bounds == myComponentBounds;

    if (actualLayout) {
      myAutoPopupRec = null;
    }
               
    int autoButtonSize = myAutoPopupIcon.getIconWidth();
    boolean full = false;

    if (myOrientation == SwingConstants.HORIZONTAL) {
      int eachX = 0;
      int eachY = 0;
      for (int i = 0; i < componentCount; i++) {
        final Component eachComp = getComponent(i);
        final boolean isLast = i == componentCount - 1;

        final Rectangle eachBound = new Rectangle(eachComp.getPreferredSize());
        if (!full) {
          boolean inside;
          if (isLast) {
            inside = eachX + eachBound.width <= sizeToFit.width;
          } else {
            inside = eachX + eachBound.width + autoButtonSize <= sizeToFit.width;
          }

          if (inside) {
            if (eachComp == mySecondaryActionsButton) {
              assert isLast;
              if (sizeToFit.width != Integer.MAX_VALUE) {
                eachBound.x = sizeToFit.width - eachBound.width;
                eachX = (int)eachBound.getMaxX();
              }
              else {
                eachBound.x = eachX;
              }
            } else {
              eachBound.x = eachX;
              eachX += eachBound.width;
            }
            eachBound.y = eachY;
          }
          else {
            full = true;
          }
        }

        if (full) {
          if (myAutoPopupRec == null) {
            myAutoPopupRec = new Rectangle(eachX, eachY, sizeToFit.width - eachX - 1, sizeToFit.height - 1);
            myFirstOusideIndex = i;
          }
          eachBound.x = Integer.MAX_VALUE;
          eachBound.y = Integer.MAX_VALUE;
        }

        bounds.get(i).setBounds(eachBound);
      }
    }
    else {
      int eachX = 0;
      int eachY = 0;
      for (int i = 0; i < componentCount; i++) {
        final Rectangle eachBound = new Rectangle(getComponent(i).getPreferredSize());
        if (!full) {
          boolean outside;
          if (i < componentCount - 1) {
            outside = eachY + eachBound.height + autoButtonSize < sizeToFit.height;
          }
          else {
            outside = eachY + eachBound.height < sizeToFit.height;
          }
          if (outside) {
            eachBound.x = eachX;
            eachBound.y = eachY;
            eachY += eachBound.height;
          }
          else {
            full = true;
          }
        }

        if (full) {
          if (myAutoPopupRec == null) {
            myAutoPopupRec = new Rectangle(eachX, eachY, sizeToFit.width - 1, sizeToFit.height - eachY - 1);
            myFirstOusideIndex = i;
          }
          eachBound.x = Integer.MAX_VALUE;
          eachBound.y = Integer.MAX_VALUE;
        }

        bounds.get(i).setBounds(eachBound);
      }
    }

  }

  private void calculateBoundsWrapImpl(Dimension sizeToFit, ArrayList<Rectangle> bounds) {
    // We have to gracefull handle case when toolbar was not layed out yet.
    // In this case we calculate bounds as it is a NOWRAP toolbar.
    if (getWidth() == 0 || getHeight() == 0) {
      try {
        setLayoutPolicy(NOWRAP_LAYOUT_POLICY);
        calculateBoundsNowrapImpl(bounds);
      }
      finally {
        setLayoutPolicy(WRAP_LAYOUT_POLICY);
      }
      return;
    }


    final int componentCount = getComponentCount();
    LOG.assertTrue(componentCount <= bounds.size());

    if (myAdjustTheSameSize) {
      if (myOrientation == SwingConstants.HORIZONTAL) {
        final int maxWidth = getMaxButtonWidth();
        final int maxHeight = getMaxButtonHeight();

        // Lay components out
        int xOffset = 0;
        int yOffset = 0;
        // Calculate max size of a row. It's not possible to make more then 3 row toolbar
        final int maxRowWidth = Math.max(sizeToFit.width, componentCount * maxWidth / 3);
        for (int i = 0; i < componentCount; i++) {
          if (xOffset + maxWidth > maxRowWidth) { // place component at new row
            xOffset = 0;
            yOffset += maxHeight;
          }

          final Rectangle each = bounds.get(i);
          each.setBounds(xOffset, maxWidth, yOffset, maxHeight);

          xOffset += maxWidth;
        }
      }
      else {
        final int maxWidth = getMaxButtonWidth();
        final int maxHeight = getMaxButtonHeight();

        // Lay components out
        int xOffset = 0;
        int yOffset = 0;
        // Calculate max size of a row. It's not possible to make more then 3 column toolbar
        final int maxRowHeight = Math.max(sizeToFit.height, componentCount * myMinimumButtonSize.height / 3);
        for (int i = 0; i < componentCount; i++) {
          if (yOffset + maxHeight > maxRowHeight) { // place component at new row
            yOffset = 0;
            xOffset += maxWidth;
          }

          final Rectangle each = bounds.get(i);
          each.setBounds(xOffset, maxWidth, yOffset, maxHeight);

          yOffset += maxHeight;
        }
      }
    }
    else {
      if (myOrientation == SwingConstants.HORIZONTAL) {
        // Calculate row height
        int rowHeight = 0;
        final Dimension[] dims = new Dimension[componentCount]; // we will use this dimesions later
        for (int i = 0; i < componentCount; i++) {
          dims[i] = getComponent(i).getPreferredSize();
          final int height = dims[i].height;
          rowHeight = Math.max(rowHeight, height);
        }

        // Lay components out
        int xOffset = 0;
        int yOffset = 0;
        // Calculate max size of a row. It's not possible to make more then 3 row toolbar
        final int maxRowWidth = Math.max(getWidth(), componentCount * myMinimumButtonSize.width / 3);
        for (int i = 0; i < componentCount; i++) {
          final Dimension d = dims[i];
          if (xOffset + d.width > maxRowWidth) { // place component at new row
            xOffset = 0;
            yOffset += rowHeight;
          }

          final Rectangle each = bounds.get(i);
          each.setBounds(xOffset, yOffset + (rowHeight - d.height) / 2, d.width, d.height);

          xOffset += d.width;
        }
      }
      else {
        // Calculate row width
        int rowWidth = 0;
        final Dimension[] dims = new Dimension[componentCount]; // we will use this dimesions later
        for (int i = 0; i < componentCount; i++) {
          dims[i] = getComponent(i).getPreferredSize();
          final int width = dims[i].width;
          rowWidth = Math.max(rowWidth, width);
        }

        // Lay components out
        int xOffset = 0;
        int yOffset = 0;
        // Calculate max size of a row. It's not possible to make more then 3 column toolbar
        final int maxRowHeight = Math.max(getHeight(), componentCount * myMinimumButtonSize.height / 3);
        for (int i = 0; i < componentCount; i++) {
          final Dimension d = dims[i];
          if (yOffset + d.height > maxRowHeight) { // place component at new row
            yOffset = 0;
            xOffset += rowWidth;
          }

          final Rectangle each = bounds.get(i);
          each.setBounds(xOffset + (rowWidth - d.width) / 2, yOffset, d.width, d.height);

          yOffset += d.height;
        }
      }
    }
  }

  /**
   * Calculates bounds of all the components in the toolbar
   */
  private void calculateBounds(Dimension size2Fit, ArrayList<Rectangle> bounds) {
    bounds.clear();
    for (int i = 0; i < getComponentCount(); i++) {
      bounds.add(new Rectangle());
    }

    if (myLayoutPolicy == NOWRAP_LAYOUT_POLICY) {
      calculateBoundsNowrapImpl(bounds);
    }
    else if (myLayoutPolicy == WRAP_LAYOUT_POLICY) {
      calculateBoundsWrapImpl(size2Fit, bounds);
    }
    else if (myLayoutPolicy == AUTO_LAYOUT_POLICY) {
      calculateBoundsAutoImp(size2Fit, bounds);
    }
    else {
      throw new IllegalStateException("unknonw layoutPolicy: " + myLayoutPolicy);
    }
  }

  public Dimension getPreferredSize() {
    final ArrayList<Rectangle> bounds = new ArrayList<Rectangle>();
    calculateBounds(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE), bounds);

    int xLeft = Integer.MAX_VALUE;
    int yTop = Integer.MAX_VALUE;
    int xRight = Integer.MIN_VALUE;
    int yBottom = Integer.MIN_VALUE;
    for (int i = bounds.size() - 1; i >= 0; i--) {
      final Rectangle each = bounds.get(i);
      if (each.x == Integer.MAX_VALUE) continue;
      xLeft = Math.min(xLeft, each.x);
      yTop = Math.min(yTop, each.y);
      xRight = Math.max(xRight, each.x + each.width);
      yBottom = Math.max(yBottom, each.y + each.height);
    }
    final Dimension dimension = new Dimension(xRight - xLeft, yBottom - yTop);

    if (myLayoutPolicy == AUTO_LAYOUT_POLICY && myReservePlaceAutoPopupIcon) {
      if (myOrientation == SwingConstants.HORIZONTAL) {
        dimension.width += myAutoPopupIcon.getIconWidth();
      }
      else {
        dimension.height += myAutoPopupIcon.getIconHeight();
      }
    }

    return dimension;
  }

  public Dimension getMinimumSize() {
    if (myLayoutPolicy == AUTO_LAYOUT_POLICY) {
      return new Dimension(myAutoPopupIcon.getIconWidth(), myMinimumButtonSize.height);
    }
    else {
      return super.getMinimumSize();
    }
  }

  private final class MySeparator extends JComponent {
    private final Dimension mySize;

    public MySeparator() {
      if (myOrientation == SwingConstants.HORIZONTAL) {
        mySize = new Dimension(6, 24);
      }
      else {
        mySize = new Dimension(24, 6);
      }
    }

    public Dimension getPreferredSize() {
      return mySize;
    }

    protected void paintComponent(final Graphics g) {
      g.setColor(UIUtil.getSeparatorShadow());
      if (getParent() != null) {
        if (myOrientation == SwingConstants.HORIZONTAL) {
          UIUtil.drawLine(g, 3, 2, 3, getParent().getSize().height - 2);
        }
        else {
          UIUtil.drawLine(g, 2, 3, getParent().getSize().width - 2, 3);
        }
      }
    }
  }

  private final class MyKeymapManagerListener implements KeymapManagerListener {
    public void activeKeymapChanged(final Keymap keymap) {
      final int componentCount = getComponentCount();
      for (int i = 0; i < componentCount; i++) {
        final Component component = getComponent(i);
        if (component instanceof ActionButton) {
          ((ActionButton)component).updateToolTipText();
        }
      }
    }
  }

  private final class MyTimerListener implements TimerListener {

    public ModalityState getModalityState() {
      return ModalityState.stateForComponent(ActionToolbarImpl.this);
    }

    public void run() {
      if (!isShowing()) {
        return;
      }

      // do not update when a popup menu is shown (if popup menu contains action which is also in the toolbar, it should not be enabled/disabled)
      final MenuSelectionManager menuSelectionManager = MenuSelectionManager.defaultManager();
      final MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
      if (selectedPath.length > 0) {
        return;
      }

      // don't update toolbar if there is currently active modal dialog

      final Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      if (window instanceof Dialog) {
        final Dialog dialog = (Dialog)window;
        if (dialog.isModal() && !SwingUtilities.isDescendingFrom(ActionToolbarImpl.this, dialog)) {
          return;
        }
      }

      updateActions(false, myActionManager.isTransparrentOnlyActionsUpdateNow());
    }
  }

  public void adjustTheSameSize(final boolean value) {
    if (myAdjustTheSameSize == value) {
      return;
    }
    myAdjustTheSameSize = value;
    revalidate();
  }

  public void setMinimumButtonSize(@NotNull final Dimension size) {
    myMinimumButtonSize = size;
    for (int i = getComponentCount() - 1; i >= 0; i--) {
      final Component component = getComponent(i);
      if (component instanceof ActionButton) {
        final ActionButton button = (ActionButton)component;
        button.setMinimumButtonSize(size);
      }
    }
    revalidate();
  }

  public void setOrientation(final int orientation) {
    if (SwingConstants.HORIZONTAL != orientation && SwingConstants.VERTICAL != orientation) {
      throw new IllegalArgumentException("wrong orientation: " + orientation);
    }
    myOrientation = orientation;
  }

  public void updateActionsImmediately() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    updateActions(true, false);
  }

  private void updateActions(boolean now, final boolean transparrentOnly) {
    final Runnable updateRunnable = new Runnable() {
      public void run() {
        myNewVisibleActions.clear();
        final DataContext dataContext = getDataContext();

        Utils.expandActionGroup(myActionGroup, myNewVisibleActions, myPresentationFactory, dataContext, myPlace, myActionManager, transparrentOnly);

        if (!myNewVisibleActions.equals(myVisibleActions)) {
          // should rebuild UI

          final boolean changeBarVisibility = myNewVisibleActions.isEmpty() || myVisibleActions.isEmpty();

          final ArrayList<AnAction> temp = myVisibleActions;
          myVisibleActions = myNewVisibleActions;
          myNewVisibleActions = temp;

          Dimension oldSize = getPreferredSize();

          removeAll();
          mySecondaryActions.removeAll();
          mySecondaryActionsButton = null;
          fillToolBar(myVisibleActions, getLayoutPolicy() == AUTO_LAYOUT_POLICY && myOrientation == SwingConstants.HORIZONTAL);

          Dimension newSize = getPreferredSize();

          if (changeBarVisibility) {
            revalidate();
          }
          else {
            final Container parent = getParent();
            if (parent != null) {
              parent.invalidate();
              parent.validate();
            }
          }

          ((WindowManagerEx)WindowManager.getInstance()).adjustContainerWindow(ActionToolbarImpl.this, oldSize, newSize);

          repaint();
        }
      }
    };

    if (now) {
      updateRunnable.run();
    } else {
      final Application app = ApplicationManager.getApplication();
      final IdeFocusManager fm = IdeFocusManager.getInstance(null);

      if (!app.isUnitTestMode() && !app.isHeadlessEnvironment()) {
        if (app.isDispatchThread()) {
          fm.doWhenFocusSettlesDown(updateRunnable);
        } else {
          UiNotifyConnector.doWhenFirstShown(this, new Runnable() {
            public void run() {
              fm.doWhenFocusSettlesDown(updateRunnable);
            }
          });
        }
      }
    }
  }

  public void setTargetComponent(final JComponent component) {
    myTargetComponent = component;

    if (myTargetComponent != null && myTargetComponent.isVisible()) {
      ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
        public void run() {
          updateActions(false, false);
        }
      }, ModalityState.stateForComponent(myTargetComponent));
    }
  }

  protected DataContext getToolbarDataContext() {
    return getDataContext();
  }

  protected DataContext getDataContext() {
    return myTargetComponent != null ? myDataManager.getDataContext(myTargetComponent) : ((DataManagerImpl)myDataManager).getDataContextTest(this);
  }

  protected void processMouseMotionEvent(final MouseEvent e) {
    super.processMouseMotionEvent(e);

    if (getLayoutPolicy() != AUTO_LAYOUT_POLICY) {
      return;
    }
    if (myAutoPopupRec != null && myAutoPopupRec.contains(e.getPoint())) {
      IdeFocusManager.getInstance(null).doWhenFocusSettlesDown(new Runnable() {
        public void run() {
          showAutoPopup();
        }
      });
    }
  }

  private void showAutoPopup() {
    if (isPopupShowing()) return;

    final ActionGroup group;
    if (myOrientation == SwingConstants.HORIZONTAL) {
      group = myActionGroup;
    }
    else {
      final DefaultActionGroup outside = new DefaultActionGroup();
      for (int i = myFirstOusideIndex; i < myVisibleActions.size(); i++) {
        outside.add(myVisibleActions.get(i));
      }
      group = outside;
    }

    PopupToolbar popupToolbar = new PopupToolbar(myPlace, group, true, myDataManager, myActionManager, myKeymapManager) {
      protected void onOtherActionPerformed() {
        hidePopup();
      }

      protected DataContext getDataContext() {
        return ActionToolbarImpl.this.getDataContext();
      }
    };
    popupToolbar.setLayoutPolicy(NOWRAP_LAYOUT_POLICY);

    Point location;
    if (myOrientation == SwingConstants.HORIZONTAL) {
      location = getLocationOnScreen();
    }
    else {
      location = getLocationOnScreen();
      location.y = location.y + getHeight() - popupToolbar.getPreferredSize().height;
    }


    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(popupToolbar, null);
    builder.setResizable(false)
      .setRequestFocus(false)
      .setTitle(null)
      .setCancelOnClickOutside(true)
      .setCancelOnOtherWindowOpen(true)
      .setCancelCallback(new Computable<Boolean>() {
        public Boolean compute() {
          return myActionManager.isActionPopupStackEmpty();
        }
      })
      .setCancelOnMouseOutCallback(new MouseChecker() {
        public boolean check(final MouseEvent event) {
          return myAutoPopupRec != null &&
                 myActionManager.isActionPopupStackEmpty() &&
                 !new RelativeRectangle(ActionToolbarImpl.this, myAutoPopupRec).contains(new RelativePoint(event));
        }
      });

    builder.addListener(new JBPopupAdapter() {
      public void onClosed(LightweightWindowEvent event) {
        processClosed();
      }
    });
    myPopup = builder.createPopup();

    Disposer.register(myPopup, popupToolbar);

    myPopup.showInScreenCoordinates(this, location);

    final Window window = SwingUtilities.getWindowAncestor(this);
    if (window != null) {
      final ComponentAdapter componentAdapter = new ComponentAdapter() {
        public void componentResized(final ComponentEvent e) {
          hidePopup();
        }

        public void componentMoved(final ComponentEvent e) {
          hidePopup();
        }

        public void componentShown(final ComponentEvent e) {
          hidePopup();
        }

        public void componentHidden(final ComponentEvent e) {
          hidePopup();
        }
      };
      window.addComponentListener(componentAdapter);
      Disposer.register(popupToolbar, new Disposable() {
        public void dispose() {
          window.removeComponentListener(componentAdapter);
        }
      });
    }
  }


  private boolean isPopupShowing() {
    if (myPopup != null) {
      if (myPopup.getContent() != null) {
        return true;
      }
    }
    return false;
  }

  private void hidePopup() {
    if (myPopup != null) {
      myPopup.cancel();
      processClosed();
    }
  }

  private void processClosed() {
    if (myPopup == null) return;

    Disposer.dispose(myPopup);
    myPopup = null;

    updateActions(false, false);
  }

  abstract static class PopupToolbar extends ActionToolbarImpl implements AnActionListener, Disposable {
    public PopupToolbar(final String place,
                        final ActionGroup actionGroup,
                        final boolean horizontal,
                        final DataManager dataManager,
                        final ActionManagerEx actionManager,
                        final KeymapManagerEx keymapManager) {
      super(place, actionGroup, horizontal, dataManager, actionManager, keymapManager, true);
      myActionManager.addAnActionListener(this);
    }

    public void dispose() {
      myActionManager.removeAnActionListener(this);
    }

    public void beforeActionPerformed(final AnAction action, final DataContext dataContext, AnActionEvent event) {
    }

    public void afterActionPerformed(final AnAction action, final DataContext dataContext, AnActionEvent event) {
      if (!myVisibleActions.contains(action)) {
        onOtherActionPerformed();
      }
    }

    protected abstract void onOtherActionPerformed();

    public void beforeEditorTyping(final char c, final DataContext dataContext) {
    }
  }


  public void setReservePlaceAutoPopupIcon(final boolean reserve) {
    myReservePlaceAutoPopupIcon = reserve;
  }

  public void setSecondaryActionsTooltip(String secondaryActionsTooltip) {
    mySecondaryActions.getTemplatePresentation().setDescription(secondaryActionsTooltip);
  }

  private static class SecondaryButton extends ActionButton {
    private SecondaryButton(AnAction action, Presentation presentation, String place, @NotNull Dimension minimumSize) {
      super(action, presentation, place, minimumSize);
    }

    @Override
    protected void paintButtonLook(Graphics g) {
      final Color bright = new Color(255, 255, 255, 200);
      final Color dark = new Color(64, 64, 64, 110);

      int padding = 3;

      g.setColor(bright);
      g.drawLine(0, padding, 0, getHeight() - padding - 1);
      g.setColor(dark);
      g.drawLine(1, padding, 1, getHeight() - padding - 1);

      super.paintButtonLook(g);
    }
  }

  public List<SwitchTarget> getTargets(boolean onlyVisible, boolean originalProvider) {
    ArrayList<SwitchTarget> result = new ArrayList<SwitchTarget>();

    if ((getBounds().width * getBounds().height) <= 0) return result;

    for (int i = 0; i < getComponentCount(); i++) {
      Component each = getComponent(i);
      if (each instanceof ActionButton) {
        result.add(new ActionTarget((ActionButton)each));
      }
    }
    return result;
  }

  private class ActionTarget implements SwitchTarget {
    private ActionButton myButton;

    private ActionTarget(ActionButton button) {
      myButton = button;
    }

    public ActionCallback switchTo(boolean requestFocus) {
      myButton.click();
      return new ActionCallback.Done();
    }

    public boolean isVisible() {
      return myButton.isVisible();
    }

    public RelativeRectangle getRectangle() {
      return new RelativeRectangle(myButton.getParent(), myButton.getBounds());
    }

    public Component getComponent() {
      return myButton;
    }

    @Override
    public String toString() {
      return myButton.getAction().toString();
    }
  }

  public SwitchTarget getCurrentTarget() {
    return null;
  }

  public boolean isCycleRoot() {
    return false;
  }

  public List<AnAction> getActions(boolean originalProvider) {
    ArrayList<AnAction> result = new ArrayList<AnAction>();

    ArrayList<AnAction> secondary = new ArrayList<AnAction>();
    if (myActionGroup != null) {
      AnAction[] kids = myActionGroup.getChildren(null);
      for (AnAction each : kids) {
        if (myActionGroup.isPrimary(each)) {
          result.add(each);
        } else {
          secondary.add(each);
        }
      }
    }
    result.add(new Separator());
    result.addAll(secondary);

    return result;
  }
}
