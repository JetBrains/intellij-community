/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
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
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IdRunnable;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.switcher.SwitchTarget;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ActionToolbarImpl extends JPanel implements ActionToolbar {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.actionSystem.impl.ActionToolbarImpl");

  private static final List<ActionToolbarImpl> ourToolbars = new LinkedList<ActionToolbarImpl>();
  public static void updateAllToolbarsImmediately() {
    for (ActionToolbarImpl toolbar : new ArrayList<ActionToolbarImpl>(ourToolbars)) {
      toolbar.updateActionsImmediately();
      for (Component c : toolbar.getComponents()) {
        if (c instanceof ActionButton) {
          ((ActionButton)c).updateToolTipText();
          ((ActionButton)c).updateIcon();
        }
      }
    }
  }

  /**
   * This array contains Rectangles which define bounds of the corresponding
   * components in the toolbar. This list can be consider as a cache of the
   * Rectangle objects that are used in calculation of preferred sizes and
   * components layout.
   */
  private final List<Rectangle> myComponentBounds = new ArrayList<Rectangle>();

  private Dimension myMinimumButtonSize = new Dimension(0, 0);

  /**
   * @see ActionToolbar#getLayoutPolicy()
   */
  private int myLayoutPolicy;
  private int myOrientation;
  private final ActionGroup myActionGroup;
  private final String myPlace;
  private final MyKeymapManagerListener myKeymapManagerListener;
  private List<AnAction> myNewVisibleActions;
  protected List<AnAction> myVisibleActions;
  private final PresentationFactory myPresentationFactory;
  private final boolean myDecorateButtons;
  /**
   * @see ActionToolbar#adjustTheSameSize(boolean)
   */
  private boolean myAdjustTheSameSize;

  private final ActionButtonLook myButtonLook = null;
  private final ActionButtonLook myMinimalButtonLook = new InplaceActionButtonLook();
  private final DataManager myDataManager;
  protected final ActionManagerEx myActionManager;

  private Rectangle myAutoPopupRec;

  private final DefaultActionGroup mySecondaryActions = new DefaultActionGroup();
  private boolean myMinimalMode;
  private boolean myForceUseMacEnhancements;

  public ActionButton getSecondaryActionsButton() {
    return mySecondaryActionsButton;
  }

  private ActionButton mySecondaryActionsButton;
  private final KeymapManagerEx myKeymapManager;

  private int myFirstOutsideIndex = -1;
  private JBPopup myPopup;

  private JComponent myTargetComponent;
  private boolean myReservePlaceAutoPopupIcon = true;
  private boolean myAddSeparatorFirst;

  private final WeakTimerListener myWeakTimerListener;
  @SuppressWarnings({"FieldCanBeLocal"}) private final ActionToolbarImpl.MyTimerListener myTimerListener;
  public ActionToolbarImpl(final String place,
                           @NotNull final ActionGroup actionGroup,
                           final boolean horizontal,
                           DataManager dataManager,
                           ActionManagerEx actionManager,
                           KeymapManagerEx keymapManager) {
    this(place, actionGroup, horizontal, false, dataManager, actionManager, keymapManager, false);
  }
  public ActionToolbarImpl(final String place,
                           @NotNull final ActionGroup actionGroup,
                           final boolean horizontal,
                           final boolean decorateButtons,
                           DataManager dataManager,
                           ActionManagerEx actionManager,
                           KeymapManagerEx keymapManager) {
    this(place, actionGroup, horizontal, decorateButtons, dataManager, actionManager, keymapManager, false);
  }

  public ActionToolbarImpl(final String place,
                           @NotNull final ActionGroup actionGroup,
                           final boolean horizontal,
                           final boolean decorateButtons,
                           DataManager dataManager,
                           ActionManagerEx actionManager,
                           KeymapManagerEx keymapManager,
                           boolean updateActionsNow) {
    super(null);
    myActionManager = actionManager;
    myKeymapManager = keymapManager;
    myPlace = place;
    myActionGroup = actionGroup;
    myPresentationFactory = new PresentationFactory();
    myKeymapManagerListener = new MyKeymapManagerListener();
    myVisibleActions = new ArrayList<AnAction>();
    myNewVisibleActions = new ArrayList<AnAction>();
    myDataManager = dataManager;
    myDecorateButtons = decorateButtons;

    setLayout(new BorderLayout());
    setOrientation(horizontal ? SwingConstants.HORIZONTAL : SwingConstants.VERTICAL);

    mySecondaryActions.getTemplatePresentation().setIcon(AllIcons.General.SecondaryGroup);
    mySecondaryActions.setPopup(true);

    updateActions(updateActionsNow, false, false);

    //
    keymapManager.addWeakListener(myKeymapManagerListener);
    myTimerListener = new MyTimerListener();
    myWeakTimerListener = new WeakTimerListener(actionManager, myTimerListener);
    // If the panel doesn't handle mouse event then it will be passed to its parent.
    // It means that if the panel is in sliding mode then the focus goes to the editor
    // and panel will be automatically hidden.
    enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.COMPONENT_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK);
    setMiniMode(false);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    ourToolbars.add(this);
    myActionManager.addTimerListener(500, myWeakTimerListener);
    myActionManager.addTransparentTimerListener(500, myWeakTimerListener);
  }

  private boolean doMacEnhancementsForMainToolbar() {
    return UIUtil.isUnderAquaLookAndFeel() && (ActionPlaces.MAIN_TOOLBAR.equals(myPlace) || myForceUseMacEnhancements);
  }

  public void setForceUseMacEnhancements(boolean useMacEnhancements) {
    myForceUseMacEnhancements = useMacEnhancements;
  }

  private boolean isInsideNavBar() {
    return ActionPlaces.NAVIGATION_BAR.equals(myPlace);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    ourToolbars.remove(this);
    myActionManager.removeTimerListener(myWeakTimerListener);
    myActionManager.removeTransparentTimerListener(myWeakTimerListener);
    if (ScreenUtil.isStandardAddRemoveNotify(this))
      myKeymapManager.removeWeakListener(myKeymapManagerListener);
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @Override
  public int getLayoutPolicy() {
    return myLayoutPolicy;                                             
  }

  @Override
  public void setLayoutPolicy(final int layoutPolicy) {
    if (layoutPolicy != NOWRAP_LAYOUT_POLICY && layoutPolicy != WRAP_LAYOUT_POLICY && layoutPolicy != AUTO_LAYOUT_POLICY) {
      throw new IllegalArgumentException("wrong layoutPolicy: " + layoutPolicy);
    }
    myLayoutPolicy = layoutPolicy;
  }

  @Override
  protected void paintComponent(final Graphics g) {
    if (doMacEnhancementsForMainToolbar()) {
      final Rectangle r = getBounds();
      UIUtil.drawGradientHToolbarBackground(g, r.width, r.height);
    } else {
      super.paintComponent(g);
    }

    if (myLayoutPolicy == AUTO_LAYOUT_POLICY) {
      if (myAutoPopupRec != null) {
        if (myOrientation == SwingConstants.HORIZONTAL) {
          final int dy = myAutoPopupRec.height / 2 - AllIcons.Ide.Link.getIconHeight() / 2;
          AllIcons.Ide.Link.paintIcon(this, g, (int)myAutoPopupRec.getMaxX() - AllIcons.Ide.Link.getIconWidth() - 1, myAutoPopupRec.y + dy);
        }
        else {
          final int dx = myAutoPopupRec.width / 2 - AllIcons.Ide.Link.getIconWidth() / 2;
          AllIcons.Ide.Link.paintIcon(this, g, myAutoPopupRec.x + dx, (int)myAutoPopupRec.getMaxY() - AllIcons.Ide.Link.getIconWidth() - 1);
        }
      }
    }
  }

  private void fillToolBar(final List<AnAction> actions, boolean layoutSecondaries) {
    if (myAddSeparatorFirst) {
      add(new MySeparator());
    }
    for (int i = 0; i < actions.size(); i++) {
      final AnAction action = actions.get(i);
//      if (action instanceof Separator && isNavBar()) {
//        continue;
//      }

      //if (action instanceof ComboBoxAction) {
      //  ((ComboBoxAction)action).setSmallVariant(true);
      //}

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
        add(getCustomComponent(action));
      }
      else {
        add(createToolbarButton(action));
      }
    }

    if (mySecondaryActions.getChildrenCount() > 0) {
      mySecondaryActionsButton = new ActionButton(mySecondaryActions, myPresentationFactory.getPresentation(mySecondaryActions), myPlace, getMinimumButtonSize());
      mySecondaryActionsButton.setNoIconsInPopup(true);
      add(mySecondaryActionsButton);
    }

    if ((ActionPlaces.MAIN_TOOLBAR.equals(myPlace) || ActionPlaces.NAVIGATION_BAR.equals(myPlace))) {
      final AnAction searchEverywhereAction = ActionManager.getInstance().getAction("SearchEverywhere");
      if (searchEverywhereAction != null) {
        try {
          final CustomComponentAction searchEveryWhereAction = (CustomComponentAction)searchEverywhereAction;
          final JComponent searchEverywhere = searchEveryWhereAction.createCustomComponent(searchEverywhereAction.getTemplatePresentation());
          searchEverywhere.putClientProperty("SEARCH_EVERYWHERE", Boolean.TRUE);
          add(searchEverywhere);
        }
        catch (Exception ignore) {}
      }
    }
  }

  private JComponent getCustomComponent(AnAction action) {
    Presentation presentation = myPresentationFactory.getPresentation(action);
    JComponent customComponent = ((CustomComponentAction)action).createCustomComponent(presentation);
    if (ActionPlaces.EDITOR_TOOLBAR.equals(myPlace)) {
      // tweak font & color for editor toolbar to match editor tabs style
      Color foreground = customComponent.getForeground();
      customComponent.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
      if (foreground != null) customComponent.setForeground(ColorUtil.dimmer(foreground));
    }
    presentation.putClientProperty(CustomComponentAction.CUSTOM_COMPONENT_PROPERTY, customComponent);
    return customComponent;
  }

  private boolean isNavBar() {
    return myPlace == ActionPlaces.NAVIGATION_BAR;
  }

  private Dimension getMinimumButtonSize() {
    return isInsideNavBar() ? NAVBAR_MINIMUM_BUTTON_SIZE : DEFAULT_MINIMUM_BUTTON_SIZE;
  } 

  public ActionButton createToolbarButton(final AnAction action, final ActionButtonLook look, final String place, final Presentation presentation, final Dimension minimumSize) {
    if (action.displayTextInToolbar()) {
      return new ActionButtonWithText(action, presentation, place, minimumSize);
    }

    final ActionButton actionButton = new ActionButton(action, presentation, place, minimumSize) {
      @Override
      protected DataContext getDataContext() {
        return getToolbarDataContext();
      }
    };
    actionButton.setLook(look);
    return actionButton;
  }

  private ActionButton createToolbarButton(final AnAction action) {
    return createToolbarButton(action, myMinimalMode ? myMinimalButtonLook : myDecorateButtons ? new MacToolbarDecoratorButtonLook() : myButtonLook, myPlace, myPresentationFactory.getPresentation(action), myMinimumButtonSize);
  }

  @Override
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

  @Override
  public void validate() {
    if (!isValid()) {
      calculateBounds(getSize(), myComponentBounds);
      super.validate();
    }
  }

  private Dimension getChildPreferredSize(int index) {
    Component component = getComponent(index);
    return component.isVisible() ? component.getPreferredSize() : new Dimension();
  }

  /**
   * @return maximum button width
   */
  private int getMaxButtonWidth() {
    int width = 0;
    for (int i = 0; i < getComponentCount(); i++) {
      final Dimension dimension = getChildPreferredSize(i);
      width = Math.max(width, dimension.width);
    }
    return width;
  }

  /**
   * @return maximum button height
   */
  @Override
  public int getMaxButtonHeight() {
    int height = 0;
    for (int i = 0; i < getComponentCount(); i++) {
      final Dimension dimension = getChildPreferredSize(i);
      height = Math.max(height, dimension.height);
    }
    return height;
  }

  private void calculateBoundsNowrapImpl(List<Rectangle> bounds) {
    final int componentCount = getComponentCount();
    LOG.assertTrue(componentCount <= bounds.size());

    final int width = getWidth();
    final int height = getHeight();

    final Insets insets = getInsets();

    if (myAdjustTheSameSize) {
      final int maxWidth = getMaxButtonWidth();
      final int maxHeight = getMaxButtonHeight();

      if (myOrientation == SwingConstants.HORIZONTAL) {
        int xOffset = insets.left;
        for (int i = 0; i < componentCount; i++) {
          final Rectangle r = bounds.get(i);
          r.setBounds(xOffset, (height - maxHeight) / 2, maxWidth, maxHeight);
          xOffset += maxWidth;
        }
      }
      else {
        int yOffset = insets.top;
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

        int xOffset = insets.left;
        final int yOffset = insets.top;
        for (int i = 0; i < componentCount; i++) {
          final Dimension d = getChildPreferredSize(i);
          final Rectangle r = bounds.get(i);
          r.setBounds(xOffset, yOffset + (maxHeight - d.height) / 2, d.width, d.height);
          xOffset += d.width;
        }
      }
      else {
        final int maxWidth = getMaxButtonWidth();
        final int xOffset = insets.left;
        int yOffset = insets.top;
        for (int i = 0; i < componentCount; i++) {
          final Dimension d = getChildPreferredSize(i);
          final Rectangle r = bounds.get(i);
          r.setBounds(xOffset + (maxWidth - d.width) / 2, yOffset, d.width, d.height);
          yOffset += d.height;
        }
      }
    }
  }

  private void calculateBoundsAutoImp(Dimension sizeToFit, List<Rectangle> bounds) {
    final int componentCount = getComponentCount();
    LOG.assertTrue(componentCount <= bounds.size());

    final boolean actualLayout = bounds == myComponentBounds;

    if (actualLayout) {
      myAutoPopupRec = null;
    }
               
    int autoButtonSize = AllIcons.Ide.Link.getIconWidth();
    boolean full = false;

    final Insets insets = getInsets();

    if (myOrientation == SwingConstants.HORIZONTAL) {
      int eachX = insets.left;
      int eachY = insets.top;
      int maxHeight = 0;
      for (int i = 0; i < componentCount; i++) {
        final Component eachComp = getComponent(i);
        final boolean isLast = i == componentCount - 1;

        final Rectangle eachBound = new Rectangle(getChildPreferredSize(i));
        maxHeight = Math.max(eachBound.height, maxHeight);

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
            myFirstOutsideIndex = i;
          }
          eachBound.x = Integer.MAX_VALUE;
          eachBound.y = Integer.MAX_VALUE;
        }

        bounds.get(i).setBounds(eachBound);
      }

      for (final Rectangle r : bounds) {
        if (r.height < maxHeight) {
          r.y += (maxHeight - r.height) / 2;
        }
      }

    }
    else {
      int eachX = insets.left;
      int eachY = insets.top;
      for (int i = 0; i < componentCount; i++) {
        final Rectangle eachBound = new Rectangle(getChildPreferredSize(i));
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
            myFirstOutsideIndex = i;
          }
          eachBound.x = Integer.MAX_VALUE;
          eachBound.y = Integer.MAX_VALUE;
        }

        bounds.get(i).setBounds(eachBound);
      }
    }

  }

  private void calculateBoundsWrapImpl(Dimension sizeToFit, List<Rectangle> bounds) {
    // We have to graceful handle case when toolbar was not laid out yet.
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

    final Insets insets = getInsets();

    if (myAdjustTheSameSize) {
      if (myOrientation == SwingConstants.HORIZONTAL) {
        final int maxWidth = getMaxButtonWidth();
        final int maxHeight = getMaxButtonHeight();

        // Lay components out
        int xOffset = insets.left;
        int yOffset = insets.top;
        // Calculate max size of a row. It's not possible to make more than 3 row toolbar
        final int maxRowWidth = Math.max(sizeToFit.width, componentCount * maxWidth / 3);
        for (int i = 0; i < componentCount; i++) {
          if (xOffset + maxWidth > maxRowWidth) { // place component at new row
            xOffset = insets.left;
            yOffset += maxHeight;
          }

          final Rectangle each = bounds.get(i);
          each.setBounds(xOffset, yOffset, maxWidth, maxHeight);

          xOffset += maxWidth;
        }
      }
      else {
        final int maxWidth = getMaxButtonWidth();
        final int maxHeight = getMaxButtonHeight();

        // Lay components out
        int xOffset = insets.left;
        int yOffset = insets.top;
        // Calculate max size of a row. It's not possible to make more then 3 column toolbar
        final int maxRowHeight = Math.max(sizeToFit.height, componentCount * myMinimumButtonSize.height / 3);
        for (int i = 0; i < componentCount; i++) {
          if (yOffset + maxHeight > maxRowHeight) { // place component at new row
            yOffset = insets.top;
            xOffset += maxWidth;
          }

          final Rectangle each = bounds.get(i);
          each.setBounds(xOffset, yOffset, maxWidth, maxHeight);

          yOffset += maxHeight;
        }
      }
    }
    else {
      if (myOrientation == SwingConstants.HORIZONTAL) {
        // Calculate row height
        int rowHeight = 0;
        final Dimension[] dims = new Dimension[componentCount]; // we will use this dimensions later
        for (int i = 0; i < componentCount; i++) {
          dims[i] = getChildPreferredSize(i);
          final int height = dims[i].height;
          rowHeight = Math.max(rowHeight, height);
        }

        // Lay components out
        int xOffset = insets.left;
        int yOffset = insets.top;
        // Calculate max size of a row. It's not possible to make more then 3 row toolbar
        final int maxRowWidth = Math.max(getWidth(), componentCount * myMinimumButtonSize.width / 3);
        for (int i = 0; i < componentCount; i++) {
          final Dimension d = dims[i];
          if (xOffset + d.width > maxRowWidth) { // place component at new row
            xOffset = insets.left;
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
        final Dimension[] dims = new Dimension[componentCount]; // we will use this dimensions later
        for (int i = 0; i < componentCount; i++) {
          dims[i] = getChildPreferredSize(i);
          final int width = dims[i].width;
          rowWidth = Math.max(rowWidth, width);
        }

        // Lay components out
        int xOffset = insets.left;
        int yOffset = insets.top;
        // Calculate max size of a row. It's not possible to make more then 3 column toolbar
        final int maxRowHeight = Math.max(getHeight(), componentCount * myMinimumButtonSize.height / 3);
        for (int i = 0; i < componentCount; i++) {
          final Dimension d = dims[i];
          if (yOffset + d.height > maxRowHeight) { // place component at new row
            yOffset = insets.top;
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
  private void calculateBounds(Dimension size2Fit, List<Rectangle> bounds) {
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
      throw new IllegalStateException("unknown layoutPolicy: " + myLayoutPolicy);
    }

    if (getComponentCount() > 0 && size2Fit.width < Integer.MAX_VALUE) {
      final Component component = getComponent(getComponentCount() - 1);
      if (component instanceof JComponent && ((JComponent)component).getClientProperty("SEARCH_EVERYWHERE") == Boolean.TRUE) {
        int max = 0;
        for (int i = 0; i < bounds.size() - 2; i++) {
          max = Math.max(max, bounds.get(i).height);
        }
        bounds.set(bounds.size() - 1, new Rectangle(size2Fit.width - 25, 0, 25, max));
      }
    }
  }

  @Override
  public Dimension getPreferredSize() {
    final ArrayList<Rectangle> bounds = new ArrayList<Rectangle>();
    calculateBounds(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE), bounds);
    if (bounds.isEmpty()) return new Dimension(0, 0);
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
        dimension.width += AllIcons.Ide.Link.getIconWidth();
      }
      else {
        dimension.height += AllIcons.Ide.Link.getIconHeight();
      }
    }

    final Insets i = getInsets();

    return new Dimension(dimension.width + i.left + i.right, dimension.height + i.top + i.bottom);
  }

  @Override
  public Dimension getMinimumSize() {
    if (myLayoutPolicy == AUTO_LAYOUT_POLICY) {
      final Insets i = getInsets();
      return new Dimension(AllIcons.Ide.Link.getIconWidth() + i.left + i.right, myMinimumButtonSize.height + i.top + i.bottom);
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

    @Override
    public Dimension getPreferredSize() {
      return mySize;
    }

    @Override
    protected void paintComponent(final Graphics g) {
      final Insets i = getInsets();
      if (UIUtil.isUnderAquaBasedLookAndFeel() || UIUtil.isUnderDarcula()) {
        if (getParent() != null) {
          final JBColor col = new JBColor(Gray._128, Gray._111);
          final Graphics2D g2 = (Graphics2D)g;
          if (myOrientation == SwingConstants.HORIZONTAL) {
            UIUtil.drawDoubleSpaceDottedLine(g2, i.top + 2, getParent().getSize().height - 2 - i.top - i.bottom, 3, col, false);
          } else {
            UIUtil.drawDoubleSpaceDottedLine(g2, i.left + 2, getParent().getSize().width - 2 - i.left - i.right, 3, col, true);
          }
        }
      }
      else {
        g.setColor(UIUtil.getSeparatorColor());
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
  }

  private final class MyKeymapManagerListener implements KeymapManagerListener {
    @Override
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

    @Override
    public ModalityState getModalityState() {
      return ModalityState.stateForComponent(ActionToolbarImpl.this);
    }

    @Override
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

      updateActions(false, myActionManager.isTransparentOnlyActionsUpdateNow(), false);
    }
  }

  @Override
  public void adjustTheSameSize(final boolean value) {
    if (myAdjustTheSameSize == value) {
      return;
    }
    myAdjustTheSameSize = value;
    revalidate();
  }

  @Override
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

  @Override
  public void setOrientation(final int orientation) {
    if (SwingConstants.HORIZONTAL != orientation && SwingConstants.VERTICAL != orientation) {
      throw new IllegalArgumentException("wrong orientation: " + orientation);
    }
    myOrientation = orientation;
  }

  @Override
  public void updateActionsImmediately() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    updateActions(true, false, false);
  }

  private void updateActions(boolean now, final boolean transparentOnly, final boolean forced) {
    final IdRunnable updateRunnable = new IdRunnable(this) {
      @Override
      public void run() {
        if (!isVisible()) {
          return;
        }

        myNewVisibleActions.clear();
        final DataContext dataContext = getDataContext();

        Utils.expandActionGroup(myActionGroup, myNewVisibleActions, myPresentationFactory, dataContext, myPlace, myActionManager, transparentOnly);

        if (forced || !myNewVisibleActions.equals(myVisibleActions)) {
          // should rebuild UI

          final boolean changeBarVisibility = myNewVisibleActions.isEmpty() || myVisibleActions.isEmpty();

          final List<AnAction> temp = myVisibleActions;
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
            @Override
            public void run() {
              fm.doWhenFocusSettlesDown(updateRunnable);
            }
          });
        }
      }
    }
  }

  @Override
  public boolean hasVisibleActions() {
    return !myVisibleActions.isEmpty();
  }

  @Override
  public void setTargetComponent(final JComponent component) {
    myTargetComponent = component;

    if (myTargetComponent != null && myTargetComponent.isVisible()) {
      ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
        @Override
        public void run() {
          updateActions(false, false, false);
        }
      }, ModalityState.stateForComponent(myTargetComponent));
    }
  }

  @Override
  public DataContext getToolbarDataContext() {
    return getDataContext();
  }

  protected DataContext getDataContext() {
    return myTargetComponent != null ? myDataManager.getDataContext(myTargetComponent) : ((DataManagerImpl)myDataManager).getDataContextTest(this);
  }

  @Override
  protected void processMouseMotionEvent(final MouseEvent e) {
    super.processMouseMotionEvent(e);

    if (getLayoutPolicy() != AUTO_LAYOUT_POLICY) {
      return;
    }
    if (myAutoPopupRec != null && myAutoPopupRec.contains(e.getPoint())) {
      IdeFocusManager.getInstance(null).doWhenFocusSettlesDown(new Runnable() {
        @Override
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
      for (int i = myFirstOutsideIndex; i < myVisibleActions.size(); i++) {
        outside.add(myVisibleActions.get(i));
      }
      group = outside;
    }

    PopupToolbar popupToolbar = new PopupToolbar(myPlace, group, true, myDataManager, myActionManager, myKeymapManager, this) {
      @Override
      protected void onOtherActionPerformed() {
        hidePopup();
      }

      @Override
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
        @Override
        public Boolean compute() {
          final boolean toClose = myActionManager.isActionPopupStackEmpty();
          if (toClose) {
            updateActions(false, false, true);
          }
          return toClose;
        }
      })
      .setCancelOnMouseOutCallback(new MouseChecker() {
        @Override
        public boolean check(final MouseEvent event) {
          return myAutoPopupRec != null &&
                 myActionManager.isActionPopupStackEmpty() &&
                 !new RelativeRectangle(ActionToolbarImpl.this, myAutoPopupRec).contains(new RelativePoint(event));
        }
      });

    builder.addListener(new JBPopupAdapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        processClosed();
      }
    });
    myPopup = builder.createPopup();
    final AnActionListener.Adapter listener = new AnActionListener.Adapter() {
      @Override
      public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        final JBPopup popup = myPopup;
        if (popup != null && !popup.isDisposed() && popup.isVisible()) {
          popup.cancel();
        }
      }
    };
    ActionManager.getInstance().addAnActionListener(listener);
    Disposer.register(myPopup, popupToolbar);
    Disposer.register(popupToolbar, new Disposable() {
      @Override
      public void dispose() {
        ActionManager.getInstance().removeAnActionListener(listener);
      }
    });

    myPopup.showInScreenCoordinates(this, location);

    final Window window = SwingUtilities.getWindowAncestor(this);
    if (window != null) {
      final ComponentAdapter componentAdapter = new ComponentAdapter() {
        @Override
        public void componentResized(final ComponentEvent e) {
          hidePopup();
        }

        @Override
        public void componentMoved(final ComponentEvent e) {
          hidePopup();
        }

        @Override
        public void componentShown(final ComponentEvent e) {
          hidePopup();
        }

        @Override
        public void componentHidden(final ComponentEvent e) {
          hidePopup();
        }
      };
      window.addComponentListener(componentAdapter);
      Disposer.register(popupToolbar, new Disposable() {
        @Override
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

    updateActions(false, false, false);
  }

  abstract static class PopupToolbar extends ActionToolbarImpl implements AnActionListener, Disposable {
    private final JComponent myParent;

    public PopupToolbar(final String place,
                        final ActionGroup actionGroup,
                        final boolean horizontal,
                        final DataManager dataManager,
                        final ActionManagerEx actionManager,
                        final KeymapManagerEx keymapManager,
                        JComponent parent) {
      super(place, actionGroup, horizontal, false, dataManager, actionManager, keymapManager, true);
      myActionManager.addAnActionListener(this);
      myParent = parent;
    }

    @Override
    public Container getParent() {
      Container parent = super.getParent();
      return parent != null ? parent : myParent;
    }

    @Override
    public void dispose() {
      myActionManager.removeAnActionListener(this);
    }

    @Override
    public void beforeActionPerformed(final AnAction action, final DataContext dataContext, AnActionEvent event) {
    }

    @Override
    public void afterActionPerformed(final AnAction action, final DataContext dataContext, AnActionEvent event) {
      if (!myVisibleActions.contains(action)) {
        onOtherActionPerformed();
      }
    }

    protected abstract void onOtherActionPerformed();

    @Override
    public void beforeEditorTyping(final char c, final DataContext dataContext) {
    }
  }


  @Override
  public void setReservePlaceAutoPopupIcon(final boolean reserve) {
    myReservePlaceAutoPopupIcon = reserve;
  }

  @Override
  public void setSecondaryActionsTooltip(String secondaryActionsTooltip) {
    mySecondaryActions.getTemplatePresentation().setDescription(secondaryActionsTooltip);
  }

  @Override
  public List<SwitchTarget> getTargets(boolean onlyVisible, boolean originalProvider) {
    ArrayList<SwitchTarget> result = new ArrayList<SwitchTarget>();

    if (getBounds().width * getBounds().height <= 0) return result;

    for (int i = 0; i < getComponentCount(); i++) {
      Component each = getComponent(i);
      if (each instanceof ActionButton) {
        result.add(new ActionTarget((ActionButton)each));
      }
    }
    return result;
  }

  private class ActionTarget implements SwitchTarget {
    private final ActionButton myButton;

    private ActionTarget(ActionButton button) {
      myButton = button;
    }

    @Override
    public ActionCallback switchTo(boolean requestFocus) {
      myButton.click();
      return new ActionCallback.Done();
    }

    @Override
    public boolean isVisible() {
      return myButton.isVisible();
    }

    @Override
    public RelativeRectangle getRectangle() {
      return new RelativeRectangle(myButton.getParent(), myButton.getBounds());
    }

    @Override
    public Component getComponent() {
      return myButton;
    }

    @Override
    public String toString() {
      return myButton.getAction().toString();
    }
  }

  @Override
  public SwitchTarget getCurrentTarget() {
    return null;
  }

  @Override
  public boolean isCycleRoot() {
    return false;
  }

  @Override
  public List<AnAction> getActions(boolean originalProvider) {
    ArrayList<AnAction> result = new ArrayList<AnAction>();

    ArrayList<AnAction> secondary = new ArrayList<AnAction>();
    AnAction[] kids = myActionGroup.getChildren(null);
    for (AnAction each : kids) {
      if (myActionGroup.isPrimary(each)) {
        result.add(each);
      } else {
        secondary.add(each);
      }
    }
    result.add(new Separator());
    result.addAll(secondary);

    return result;
  }

  @Override
  public void setMiniMode(boolean minimalMode) {
    //if (myMinimalMode == minimalMode) return;

    myMinimalMode = minimalMode;
    if (myMinimalMode) {
      setMinimumButtonSize(new Dimension(0, 0));
      setLayoutPolicy(NOWRAP_LAYOUT_POLICY);
      setBorder(new EmptyBorder(0, 0, 0, 0));
      setOpaque(false);
    } else {
      if (isInsideNavBar()) {
        setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
      }
      else {
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
      }

      setMinimumButtonSize(myDecorateButtons ? new Dimension(30, 20) : DEFAULT_MINIMUM_BUTTON_SIZE);
      setOpaque(true);
      setLayoutPolicy(AUTO_LAYOUT_POLICY);
    }

    updateActions(false, false, true);
  }

  public void setAddSeparatorFirst(boolean addSeparatorFirst) {
    myAddSeparatorFirst = addSeparatorFirst;
    updateActions(false, false, true);
  }
}
