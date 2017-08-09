/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ActionToolbarImpl extends JPanel implements ActionToolbar, QuickActionProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.actionSystem.impl.ActionToolbarImpl");

  private static final List<ActionToolbarImpl> ourToolbars = new LinkedList<>();
  private static final String RIGHT_ALIGN_KEY = "RIGHT_ALIGN";

  static {
    JBUI.addPropertyChangeListener(JBUI.USER_SCALE_FACTOR_PROPERTY, new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent e) {
        ((JBDimension)ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE).update();
        ((JBDimension)ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE).update();
      }
    });
  }

  public static void updateAllToolbarsImmediately() {
    for (ActionToolbarImpl toolbar : new ArrayList<>(ourToolbars)) {
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
  private final List<Rectangle> myComponentBounds = new ArrayList<>();

  private Dimension myMinimumButtonSize = JBUI.emptySize();

  /**
   * @see ActionToolbar#getLayoutPolicy()
   */
  private int myLayoutPolicy;
  private int myOrientation;
  private final ActionGroup myActionGroup;
  private final String myPlace;
  protected List<AnAction> myVisibleActions;
  private final PresentationFactory myPresentationFactory = new PresentationFactory();
  private final boolean myDecorateButtons;

  private final ToolbarUpdater myUpdater;

  /**
   * @see ActionToolbar#adjustTheSameSize(boolean)
   */
  private boolean myAdjustTheSameSize;

  private final ActionButtonLook myButtonLook = null;
  private final ActionButtonLook myMinimalButtonLook = ActionButtonLook.INPLACE_LOOK;
  private final DataManager myDataManager;
  protected final ActionManagerEx myActionManager;

  private Rectangle myAutoPopupRec;

  private final DefaultActionGroup mySecondaryActions = new DefaultActionGroup();
  private PopupStateModifier mySecondaryButtonPopupStateModifier = null;
  private boolean myForceMinimumSize = false;
  private boolean myMinimalMode;
  private boolean myForceUseMacEnhancements;

  public ActionButton getSecondaryActionsButton() {
    return mySecondaryActionsButton;
  }

  private ActionButton mySecondaryActionsButton;

  private int myFirstOutsideIndex = -1;
  private JBPopup myPopup;

  private JComponent myTargetComponent;
  private boolean myReservePlaceAutoPopupIcon = true;
  private boolean myAddSeparatorFirst;

  public ActionToolbarImpl(String place,
                           @NotNull final ActionGroup actionGroup,
                           boolean horizontal,
                           @NotNull DataManager dataManager,
                           @NotNull ActionManagerEx actionManager,
                           @NotNull KeymapManagerEx keymapManager) {
    this(place, actionGroup, horizontal, false, dataManager, actionManager, keymapManager, false);
  }

  public ActionToolbarImpl(String place,
                           @NotNull ActionGroup actionGroup,
                           boolean horizontal,
                           boolean decorateButtons,
                           @NotNull DataManager dataManager,
                           @NotNull ActionManagerEx actionManager,
                           @NotNull KeymapManagerEx keymapManager) {
    this(place, actionGroup, horizontal, decorateButtons, dataManager, actionManager, keymapManager, false);
  }

  public ActionToolbarImpl(String place,
                           @NotNull ActionGroup actionGroup,
                           final boolean horizontal,
                           final boolean decorateButtons,
                           @NotNull DataManager dataManager,
                           @NotNull ActionManagerEx actionManager,
                           @NotNull KeymapManagerEx keymapManager,
                           boolean updateActionsNow) {
    super(null);
    myActionManager = actionManager;
    myPlace = place;
    myActionGroup = actionGroup;
    myVisibleActions = new ArrayList<>();
    myDataManager = dataManager;
    myDecorateButtons = decorateButtons;
    myUpdater = new ToolbarUpdater(actionManager, keymapManager, this) {
      @Override
      protected void updateActionsImpl(boolean transparentOnly, boolean forced) {
        ActionToolbarImpl.this.updateActionsImpl(transparentOnly, forced);
      }
    };

    setLayout(new BorderLayout());
    setOrientation(horizontal ? SwingConstants.HORIZONTAL : SwingConstants.VERTICAL);

    mySecondaryActions.getTemplatePresentation().setIcon(AllIcons.General.SecondaryGroup);
    mySecondaryActions.setPopup(true);

    myUpdater.updateActions(updateActionsNow, false);

    // If the panel doesn't handle mouse event then it will be passed to its parent.
    // It means that if the panel is in sliding mode then the focus goes to the editor
    // and panel will be automatically hidden.
    enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.COMPONENT_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK);
    setMiniMode(false);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    for (Component component : getComponents()) {
      tweakActionComponentUI(component);
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();
    ourToolbars.add(this);

    // should update action right on the showing, otherwise toolbar may not be displayed at all,
    // since by default all updates are postponed until frame gets focused.  
    updateActionsImmediately();
  }

  private boolean doMacEnhancementsForMainToolbar() {
    return UIUtil.isUnderAquaLookAndFeel() && (ActionPlaces.MAIN_TOOLBAR.equals(myPlace) || myForceUseMacEnhancements);
  }

  public void setForceUseMacEnhancements(boolean useMacEnhancements) {
    myForceUseMacEnhancements = useMacEnhancements;
  }

  private boolean isInsideNavBar() {
    return ActionPlaces.NAVIGATION_BAR_TOOLBAR.equals(myPlace);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    ourToolbars.remove(this);
  }

  @NotNull
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
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
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

  public void setSecondaryButtonPopupStateModifier(PopupStateModifier popupStateModifier) {
    mySecondaryButtonPopupStateModifier = popupStateModifier;
  }

  private void fillToolBar(final List<AnAction> actions, boolean layoutSecondaries) {
    final List<AnAction> rightAligned = new ArrayList<>();
    if (myAddSeparatorFirst) {
      add(new MySeparator());
    }
    for (int i = 0; i < actions.size(); i++) {
      final AnAction action = actions.get(i);
      if (action instanceof RightAlignedToolbarAction) {
        rightAligned.add(action);
        continue;
      }
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
      mySecondaryActionsButton = new ActionButton(mySecondaryActions, myPresentationFactory.getPresentation(mySecondaryActions), myPlace, getMinimumButtonSize()) {
        @Override
        @ButtonState
        public int getPopState() {
          return mySecondaryButtonPopupStateModifier != null && mySecondaryButtonPopupStateModifier.willModify()
                 ? mySecondaryButtonPopupStateModifier.getModifiedPopupState()
                 : super.getPopState();
        }
      };
      mySecondaryActionsButton.setNoIconsInPopup(true);
      add(mySecondaryActionsButton);
    }

    for (AnAction action : rightAligned) {
      JComponent button = action instanceof CustomComponentAction ? getCustomComponent(action) : createToolbarButton(action);
      if (!isInsideNavBar()) {
        button.putClientProperty(RIGHT_ALIGN_KEY, Boolean.TRUE);
      }
      add(button);
    }
    //if ((ActionPlaces.MAIN_TOOLBAR.equals(myPlace) || ActionPlaces.NAVIGATION_BAR_TOOLBAR.equals(myPlace))) {
    //  final AnAction searchEverywhereAction = ActionManager.getInstance().getAction("SearchEverywhere");
    //  if (searchEverywhereAction != null) {
    //    try {
    //      final CustomComponentAction searchEveryWhereAction = (CustomComponentAction)searchEverywhereAction;
    //      final JComponent searchEverywhere = searchEveryWhereAction.createCustomComponent(searchEverywhereAction.getTemplatePresentation());
    //      searchEverywhere.putClientProperty("SEARCH_EVERYWHERE", Boolean.TRUE);
    //      add(searchEverywhere);
    //    }
    //    catch (Exception ignore) {}
    //  }
    //}
  }

  private JComponent getCustomComponent(AnAction action) {
    Presentation presentation = myPresentationFactory.getPresentation(action);
    JComponent customComponent = ObjectUtils.tryCast(presentation.getClientProperty(CustomComponentAction.CUSTOM_COMPONENT_PROPERTY), JComponent.class);
    if (customComponent == null) {
      customComponent = ((CustomComponentAction)action).createCustomComponent(presentation);
      presentation.putClientProperty(CustomComponentAction.CUSTOM_COMPONENT_PROPERTY, customComponent);
    }
    if (customComponent instanceof JCheckBox) {
      customComponent.setBorder(JBUI.Borders.emptyLeft(9));
    }
    tweakActionComponentUI(customComponent);
    return customComponent;
  }

  private void tweakActionComponentUI(@NotNull Component actionComponent) {
    if (ActionPlaces.EDITOR_TOOLBAR.equals(myPlace)) {
      // tweak font & color for editor toolbar to match editor tabs style
      actionComponent.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
      actionComponent.setForeground(ColorUtil.dimmer(JBColor.BLACK));
    }
  }

  private Dimension getMinimumButtonSize() {
    return isInsideNavBar() ? NAVBAR_MINIMUM_BUTTON_SIZE : DEFAULT_MINIMUM_BUTTON_SIZE;
  } 

  public ActionButton createToolbarButton(final AnAction action, final ActionButtonLook look, final String place, final Presentation presentation, final Dimension minimumSize) {
    if (action.displayTextInToolbar()) {
      int mnemonic = KeyEvent.getExtendedKeyCodeForChar(action.getTemplatePresentation().getMnemonic());

      ActionButtonWithText buttonWithText = new ActionButtonWithText(action, presentation, place, minimumSize);
      if (mnemonic != KeyEvent.VK_UNDEFINED) {
        buttonWithText.registerKeyboardAction(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            buttonWithText.click();
          }
        }, KeyStroke.getKeyStroke(mnemonic,
                                /*SystemInfo.isMac
                                ? InputEvent.CTRL_DOWN_MASK |
                                  InputEvent.ALT_DOWN_MASK
                                :*/ InputEvent.ALT_DOWN_MASK), WHEN_IN_FOCUSED_WINDOW);
      }
      tweakActionComponentUI(buttonWithText);
      return buttonWithText;
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
    return createToolbarButton(
      action,
      myMinimalMode ? myMinimalButtonLook : myDecorateButtons ? new ActionButtonLook() {
        @Override
        public void paintBorder(Graphics g, JComponent c, int state) {
          g.setColor(JBColor.border());
          g.drawLine(c.getWidth()-1, 0, c.getWidth()-1, c.getHeight());
        }

        @Override
        public void paintBackground(Graphics g, JComponent component, int state) {
          if (state == ActionButtonComponent.PUSHED) {
            g.setColor(component.getBackground().darker());
            ((Graphics2D)g).fill(g.getClip());
          }
        }
      } : myButtonLook,
      myPlace, myPresentationFactory.getPresentation(action),
      myMinimumButtonSize);
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
        int offset = 0;
        for (int i = 0; i < componentCount; i++) {
          final Rectangle r = bounds.get(i);
          r.setBounds(insets.left + offset, insets.top + (height - maxHeight) / 2, maxWidth, maxHeight);
          offset += maxWidth;
        }
      }
      else {
        int offset = 0;
        for (int i = 0; i < componentCount; i++) {
          final Rectangle r = bounds.get(i);
          r.setBounds(insets.left + (width - maxWidth) / 2, insets.top + offset, maxWidth, maxHeight);
          offset += maxHeight;
        }
      }
    }
    else {
      if (myOrientation == SwingConstants.HORIZONTAL) {
        final int maxHeight = getMaxButtonHeight();
        int offset = 0;
        for (int i = 0; i < componentCount; i++) {
          final Dimension d = getChildPreferredSize(i);
          final Rectangle r = bounds.get(i);
          r.setBounds(insets.left + offset, insets.top + (maxHeight - d.height) / 2, d.width, d.height);
          offset += d.width;
        }
      }
      else {
        final int maxWidth = getMaxButtonWidth();
        int offset = 0;
        for (int i = 0; i < componentCount; i++) {
          final Dimension d = getChildPreferredSize(i);
          final Rectangle r = bounds.get(i);
          r.setBounds(insets.left + (maxWidth - d.width) / 2, insets.top + offset, d.width, d.height);
          offset += d.height;
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
    int widthToFit = sizeToFit.width - insets.left - insets.right;
    int heightToFit = sizeToFit.height - insets.top - insets.bottom;

    if (myOrientation == SwingConstants.HORIZONTAL) {
      int eachX = 0;
      int maxHeight = heightToFit;
      for (int i = 0; i < componentCount; i++) {
        final Component eachComp = getComponent(i);
        final boolean isLast = i == componentCount - 1;

        final Rectangle eachBound = new Rectangle(getChildPreferredSize(i));
        maxHeight = Math.max(eachBound.height, maxHeight);

        if (!full) {
          boolean inside;
          if (isLast) {
            inside = eachX + eachBound.width <= widthToFit;
          } else {
            inside = eachX + eachBound.width + autoButtonSize <= widthToFit;
          }

          if (inside) {
            if (eachComp == mySecondaryActionsButton) {
              assert isLast;
              if (sizeToFit.width != Integer.MAX_VALUE) {
                eachBound.x = sizeToFit.width - insets.right - eachBound.width;
                eachX = (int)eachBound.getMaxX() - insets.left;
              }
              else {
                eachBound.x = insets.left + eachX;
              }
            } else {
              eachBound.x = insets.left + eachX;
              eachX += eachBound.width;
            }
            eachBound.y = insets.top;
          }
          else {
            full = true;
          }
        }

        if (full) {
          if (myAutoPopupRec == null) {
            myAutoPopupRec = new Rectangle(insets.left + eachX, insets.top, widthToFit - eachX, heightToFit);
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
      int eachY = 0;
      for (int i = 0; i < componentCount; i++) {
        final Rectangle eachBound = new Rectangle(getChildPreferredSize(i));
        if (!full) {
          boolean outside;
          if (i < componentCount - 1) {
            outside = eachY + eachBound.height + autoButtonSize < heightToFit;
          }
          else {
            outside = eachY + eachBound.height < heightToFit;
          }
          if (outside) {
            eachBound.x = insets.left;
            eachBound.y = insets.top + eachY;
            eachY += eachBound.height;
          }
          else {
            full = true;
          }
        }

        if (full) {
          if (myAutoPopupRec == null) {
            myAutoPopupRec = new Rectangle(insets.left, insets.top + eachY, widthToFit, heightToFit - eachY);
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
    int widthToFit = sizeToFit.width - insets.left - insets.right;
    int heightToFit = sizeToFit.height - insets.top - insets.bottom;

    if (myAdjustTheSameSize) {
      if (myOrientation == SwingConstants.HORIZONTAL) {
        final int maxWidth = getMaxButtonWidth();
        final int maxHeight = getMaxButtonHeight();

        // Lay components out
        int xOffset = 0;
        int yOffset = 0;
        // Calculate max size of a row. It's not possible to make more than 3 row toolbar
        final int maxRowWidth = Math.max(widthToFit, componentCount * maxWidth / 3);
        for (int i = 0; i < componentCount; i++) {
          if (xOffset + maxWidth > maxRowWidth) { // place component at new row
            xOffset = 0;
            yOffset += maxHeight;
          }

          final Rectangle each = bounds.get(i);
          each.setBounds(insets.left + xOffset, insets.top + yOffset, maxWidth, maxHeight);

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
        final int maxRowHeight = Math.max(heightToFit, componentCount * myMinimumButtonSize.height / 3);
        for (int i = 0; i < componentCount; i++) {
          if (yOffset + maxHeight > maxRowHeight) { // place component at new row
            yOffset = 0;
            xOffset += maxWidth;
          }

          final Rectangle each = bounds.get(i);
          each.setBounds(insets.left + xOffset, insets.top + yOffset, maxWidth, maxHeight);

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
        int xOffset = 0;
        int yOffset = 0;
        // Calculate max size of a row. It's not possible to make more then 3 row toolbar
        final int maxRowWidth = Math.max(widthToFit, componentCount * myMinimumButtonSize.width / 3);
        for (int i = 0; i < componentCount; i++) {
          final Dimension d = dims[i];
          if (xOffset + d.width > maxRowWidth) { // place component at new row
            xOffset = 0;
            yOffset += rowHeight;
          }

          final Rectangle each = bounds.get(i);
          each.setBounds(insets.left + xOffset, insets.top + yOffset + (rowHeight - d.height) / 2, d.width, d.height);

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
        int xOffset = 0;
        int yOffset = 0;
        // Calculate max size of a row. It's not possible to make more then 3 column toolbar
        final int maxRowHeight = Math.max(heightToFit, componentCount * myMinimumButtonSize.height / 3);
        for (int i = 0; i < componentCount; i++) {
          final Dimension d = dims[i];
          if (yOffset + d.height > maxRowHeight) { // place component at new row
            yOffset = 0;
            xOffset += rowWidth;
          }

          final Rectangle each = bounds.get(i);
          each.setBounds(insets.left + xOffset + (rowWidth - d.width) / 2, insets.top + yOffset, d.width, d.height);

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
      int maxHeight = 0;
      for (int i = 0; i < bounds.size() - 2; i++) {
        maxHeight = Math.max(maxHeight, bounds.get(i).height);
      }

      int rightOffset = 0;
      Insets insets = getInsets();
      for (int i = getComponentCount() - 1, j = 1; i > 0; i--, j++) {
        final Component component = getComponent(i);
        if (component instanceof JComponent && ((JComponent)component).getClientProperty(RIGHT_ALIGN_KEY) == Boolean.TRUE) {
          rightOffset += bounds.get(i).width;
          Rectangle r = bounds.get(bounds.size() - j);
          r.x = size2Fit.width - rightOffset;
          r.y = insets.top + (getHeight() - insets.top - insets.bottom - bounds.get(i).height) / 2;
        }
      }
    }
  }

  @Override
  public Dimension getPreferredSize() {
    final ArrayList<Rectangle> bounds = new ArrayList<>();
    calculateBounds(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE), bounds);
    if (bounds.isEmpty()) return JBUI.emptySize();
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

    if (myLayoutPolicy == AUTO_LAYOUT_POLICY && myReservePlaceAutoPopupIcon && !isInsideNavBar()) {
      if (myOrientation == SwingConstants.HORIZONTAL) {
        dimension.width += AllIcons.Ide.Link.getIconWidth();
      }
      else {
        dimension.height += AllIcons.Ide.Link.getIconHeight();
      }
    }

    JBInsets.addTo(dimension, getInsets());

    return dimension;
  }
  public void setForceMinimumSize(boolean force) {
    myForceMinimumSize = force;
  }

  @Override
  public Dimension getMinimumSize() {
    if (myForceMinimumSize) {
      return getPreferredSize();
    }
    if (myLayoutPolicy == AUTO_LAYOUT_POLICY) {
      final Insets i = getInsets();
      return new Dimension(AllIcons.Ide.Link.getIconWidth() + i.left + i.right, myMinimumButtonSize.height + i.top + i.bottom);
    }
    else {
      return super.getMinimumSize();
    }
  }

  private static class ToolbarReference extends WeakReference<ActionToolbarImpl> {
    private static final ReferenceQueue<ActionToolbarImpl> ourQueue = new ReferenceQueue<>();
    private volatile Disposable myDisposable;
    
    ToolbarReference(ActionToolbarImpl toolbar) {
      super(toolbar, ourQueue);
      processQueue();
    }

    private static void processQueue() {
      while (true) {
        ToolbarReference ref = (ToolbarReference)ourQueue.poll();
        if (ref == null) break;
        ref.disposeReference();
      }
    }

    private void disposeReference() {
      Disposable disposable = myDisposable;
      if (disposable != null) {
        myDisposable = null;
        Disposer.dispose(disposable);
      }
    }
  }

  private final class MySeparator extends JComponent {
    private final Dimension mySize;

    public MySeparator() {
      if (myOrientation == SwingConstants.HORIZONTAL) {
        mySize = JBUI.size(6, 24);
      }
      else {
        mySize = JBUI.size(24, 6);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return mySize;
    }

    @Override
    protected void paintComponent(final Graphics g) {
      final Insets i = getInsets();
      int gap = JBUI.scale(2);
      int offset = JBUI.scale(3);

      if (UIUtil.isUnderAquaBasedLookAndFeel() || UIUtil.isUnderDarcula()) {
        if (getParent() != null) {
          final JBColor col = new JBColor(Gray._128, Gray._111);
          final Graphics2D g2 = (Graphics2D)g;
          if (myOrientation == SwingConstants.HORIZONTAL) {
            UIUtil.drawDoubleSpaceDottedLine(g2, i.top + gap, getParent().getSize().height - gap - i.top - i.bottom, offset, col, false);
          } else {
            UIUtil.drawDoubleSpaceDottedLine(g2, i.left + gap, getParent().getSize().width - gap - i.left - i.right, offset, col, true);
          }
        }
      }
      else {
        g.setColor(UIUtil.getSeparatorColor());
        if (getParent() != null) {
          if (myOrientation == SwingConstants.HORIZONTAL) {
            UIUtil.drawLine(g, offset, gap, offset, getParent().getSize().height - gap);
          }
          else {
            UIUtil.drawLine(g, gap, offset, getParent().getSize().width - gap, offset);
          }
        }
      }
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
    myUpdater.updateActions(true, false);
  }

  private void updateActionsImpl(boolean transparentOnly, boolean forced) {
    List<AnAction> newVisibleActions = ContainerUtil.newArrayListWithCapacity(myVisibleActions.size());
    DataContext dataContext = getDataContext();

    Utils.expandActionGroup(LaterInvocator.isInModalContext(), myActionGroup, newVisibleActions, myPresentationFactory, dataContext,
                            myPlace, myActionManager, transparentOnly, false, false, true);

    if (forced || !newVisibleActions.equals(myVisibleActions)) {
      boolean shouldRebuildUI = newVisibleActions.isEmpty() || myVisibleActions.isEmpty();
      myVisibleActions = newVisibleActions;

      Dimension oldSize = getPreferredSize();

      removeAll();
      mySecondaryActions.removeAll();
      mySecondaryActionsButton = null;
      fillToolBar(myVisibleActions, getLayoutPolicy() == AUTO_LAYOUT_POLICY && myOrientation == SwingConstants.HORIZONTAL);

      Dimension newSize = getPreferredSize();

      ((WindowManagerEx)WindowManager.getInstance()).adjustContainerWindow(this, oldSize, newSize);

      if (shouldRebuildUI) {
        revalidate();
      }
      else {
        Container parent = getParent();
        if (parent != null) {
          parent.invalidate();
          parent.validate();
        }
      }

      repaint();
    }
  }


  @Override
  public boolean hasVisibleActions() {
    return !myVisibleActions.isEmpty();
  }

  @Override
  public void setTargetComponent(final JComponent component) {
    myTargetComponent = component;

    if (myTargetComponent != null) {
      updateWhenFirstShown(myTargetComponent, new ToolbarReference(this));
    }
  }

  private static void updateWhenFirstShown(JComponent targetComponent, final ToolbarReference ref) {
    Activatable activatable = new Activatable.Adapter() {
      public void showNotify() {
        ActionToolbarImpl toolbar = ref.get();
        if (toolbar != null) {
          toolbar.myUpdater.updateActions(false, false);
        }
      }
    };

    ref.myDisposable = new UiNotifyConnector(targetComponent, activatable) {
      @Override
      protected void showNotify() {
        super.showNotify();
        ref.disposeReference();
      }
    };
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
      IdeFocusManager.getInstance(null).doWhenFocusSettlesDown(() -> showAutoPopup());
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

    PopupToolbar popupToolbar = new PopupToolbar(myPlace, group, true, myDataManager, myActionManager, myUpdater.getKeymapManager(), this) {
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
    popupToolbar.updateActionsImmediately();

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
      .setMovable(true) // fit the screen automatically
      .setRequestFocus(false)
      .setTitle(null)
      .setCancelOnClickOutside(true)
      .setCancelOnOtherWindowOpen(true)
      .setCancelCallback(() -> {
        final boolean toClose = myActionManager.isActionPopupStackEmpty();
        if (toClose) {
          myUpdater.updateActions(false, true);
        }
        return toClose;
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

    myUpdater.updateActions(false, false);
  }

  abstract static class PopupToolbar extends ActionToolbarImpl implements AnActionListener, Disposable {
    private final JComponent myParent;

    public PopupToolbar(final String place,
                        final ActionGroup actionGroup,
                        final boolean horizontal,
                        final DataManager dataManager,
                        @NotNull ActionManagerEx actionManager,
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
  public void setSecondaryActionsIcon(Icon icon) {
    mySecondaryActions.getTemplatePresentation().setIcon(icon);
  }

  @NotNull
  @Override
  public List<AnAction> getActions(boolean originalProvider) {
    return getActions();
  }

  @NotNull
  @Override
  public List<AnAction> getActions() {
    ArrayList<AnAction> result = new ArrayList<>();

    ArrayList<AnAction> secondary = new ArrayList<>();
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
      setMinimumButtonSize(JBUI.emptySize());
      setLayoutPolicy(NOWRAP_LAYOUT_POLICY);
      setBorder(JBUI.Borders.empty());
      setOpaque(false);
    } else {
      if (UIUtil.isUnderWin10LookAndFeel()) {
        setBorder(JBUI.Borders.empty(0));
        setMinimumButtonSize(myDecorateButtons ? JBUI.size(30, 20) : JBUI.size(25, 22));
      } else {
        setBorder(JBUI.Borders.empty(2));
        setMinimumButtonSize(myDecorateButtons ? JBUI.size(30, 20) : DEFAULT_MINIMUM_BUTTON_SIZE);
      }
      setOpaque(true);
      setLayoutPolicy(AUTO_LAYOUT_POLICY);
    }

    myUpdater.updateActions(false, true);
  }

  public void setAddSeparatorFirst(boolean addSeparatorFirst) {
    myAddSeparatorFirst = addSeparatorFirst;
    myUpdater.updateActions(false, true);
  }

  @TestOnly
  public Presentation getPresentation(AnAction action) {
    return myPresentationFactory.getPresentation(action);
  }

  public void clearPresentationCache() {
    myPresentationFactory.reset();
  }

  public interface PopupStateModifier {
    @ActionButtonComponent.ButtonState
    int getModifiedPopupState();
    boolean willModify();
  }
}
