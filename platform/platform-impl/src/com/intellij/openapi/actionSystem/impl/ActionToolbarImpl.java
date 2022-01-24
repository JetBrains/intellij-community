// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.internal.statistic.collectors.fus.ui.persistence.ToolbarClicksCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import com.intellij.util.animation.AlphaAnimated;
import com.intellij.util.animation.AlphaAnimationContext;
import com.intellij.util.animation.ShowHideAnimator;
import com.intellij.util.concurrency.EdtScheduledExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.CancellablePromise;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static com.intellij.util.IJSwingUtilities.getFocusedComponentInWindowOrSelf;

public class ActionToolbarImpl extends JPanel implements ActionToolbar, QuickActionProvider, AlphaAnimated {
  private static final Logger LOG = Logger.getInstance(ActionToolbarImpl.class);

  private static final Set<ActionToolbarImpl> ourToolbars = new LinkedHashSet<>();
  private static final String RIGHT_ALIGN_KEY = "RIGHT_ALIGN";

  private static final Key<String> SECONDARY_SHORTCUT = Key.create("SecondaryActions.shortcut");

  private static final String LOADING_LABEL = "LOADING_LABEL";
  private static final String SUPPRESS_ACTION_COMPONENT_WARNING = "ActionToolbarImpl.suppressCustomComponentWarning";
  private static final String SUPPRESS_TARGET_COMPONENT_WARNING = "ActionToolbarImpl.suppressTargetComponentWarning";

  static {
    JBUIScale.addUserScaleChangeListener(__ -> {
      ((JBDimension)ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE).update();
      ((JBDimension)ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE).update();
    });
  }

  /** Async toolbars are not updated immediately despite the name of the method. */
  public static void updateAllToolbarsImmediately() {
    updateAllToolbarsImmediately(false);
  }

  @ApiStatus.Internal
  public static void updateAllToolbarsImmediately(boolean includeInvisible) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    for (ActionToolbarImpl toolbar : new ArrayList<>(ourToolbars)) {
      toolbar.updateActionsImmediately(includeInvisible);
      for (Component c : toolbar.getComponents()) {
        if (c instanceof ActionButton) {
          ((ActionButton)c).updateToolTipText();
          ((ActionButton)c).updateIcon();
        }
        toolbar.updateUI();
      }
    }
  }

  @ApiStatus.Internal
  public static void resetAllToolbars() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Utils.clearAllCachesAndUpdates();
    boolean isTestMode = ApplicationManager.getApplication().isUnitTestMode();
    for (ActionToolbarImpl toolbar : new ArrayList<>(ourToolbars)) {
      CancellablePromise<List<AnAction>> promise = toolbar.myLastUpdate;
      toolbar.myLastUpdate = null;
      if (promise != null) promise.cancel();
      toolbar.myVisibleActions.clear();
      Image image = !isTestMode && toolbar.isShowing() ? paintToImage(toolbar) : null;
      toolbar.removeAll();
      if (image != null) toolbar.myCachedImage = image;
      else if (!isTestMode) toolbar.addLoadingIcon();
    }
  }

  private final Throwable myCreationTrace = new Throwable("toolbar creation trace");

  /** @see #calculateBounds(Dimension, List) */
  private final List<Rectangle> myComponentBounds = new ArrayList<>();
  private JBDimension myMinimumButtonSize = JBUI.emptySize();

  /** @see ActionToolbar#getLayoutPolicy() */
  @LayoutPolicy
  private int myLayoutPolicy;
  private int myOrientation;
  private final ActionGroup myActionGroup;
  private final @NotNull String myPlace;
  private List<? extends AnAction> myVisibleActions;
  private final PresentationFactory myPresentationFactory = new PresentationFactory();
  private final boolean myDecorateButtons;

  private final ToolbarUpdater myUpdater;
  private CancellablePromise<List<AnAction>> myLastUpdate;
  private boolean myForcedUpdateRequested = true;

  /** @see ActionToolbar#adjustTheSameSize(boolean) */
  private boolean myAdjustTheSameSize;

  private final ActionButtonLook myMinimalButtonLook = ActionButtonLook.INPLACE_LOOK;

  private Rectangle myAutoPopupRec;

  private final DefaultActionGroup mySecondaryActions;
  private SecondaryGroupUpdater mySecondaryGroupUpdater;
  private boolean myForceMinimumSize;
  private boolean myForceShowFirstComponent;
  private boolean mySkipWindowAdjustments;
  private boolean myMinimalMode;
  private boolean myNoGapMode;//if true secondary actions button would be layout side-by-side with other buttons

  private ActionButton mySecondaryActionsButton;

  private int myFirstOutsideIndex = -1;
  private JBPopup myPopup;

  private JComponent myTargetComponent;
  private boolean myReservePlaceAutoPopupIcon = true;
  private boolean myShowSeparatorTitles;
  private PopupHandler myPopupHandler;
  private Image myCachedImage;

  private final AlphaAnimationContext myAlphaContext = new AlphaAnimationContext(this);

  private final EventDispatcher<ActionToolbarListener> myListeners = EventDispatcher.create(ActionToolbarListener.class);

  public ActionToolbarImpl(@NotNull String place, @NotNull ActionGroup actionGroup, boolean horizontal) {
    this(place, actionGroup, horizontal, false);
  }

  public ActionToolbarImpl(@NotNull String place,
                           @NotNull ActionGroup actionGroup,
                           boolean horizontal,
                           boolean decorateButtons) {
    super(null);
    if (ActionPlaces.UNKNOWN.equals(place) || place.isEmpty()) {
      LOG.warn("Please do not use ActionPlaces.UNKNOWN or the empty place. " +
               "Any string unique enough to deduce the toolbar location will do.", myCreationTrace);
    }

    myAlphaContext.getAnimator().setVisibleImmediately(true);
    myPlace = place;
    myActionGroup = actionGroup;
    myVisibleActions = new ArrayList<>();
    myDecorateButtons = decorateButtons;
    myUpdater = new ToolbarUpdater(this) {
      @Override
      protected void updateActionsImpl(boolean forced) {
        if (!ApplicationManager.getApplication().isDisposed()) {
          ActionToolbarImpl.this.updateActionsImpl(forced);
        }
      }
    };

    setOrientation(horizontal ? SwingConstants.HORIZONTAL : SwingConstants.VERTICAL);

    mySecondaryActions = new DefaultActionGroup() {
      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        if (mySecondaryGroupUpdater != null) {
          e.getPresentation().setIcon(getTemplatePresentation().getIcon());
          mySecondaryGroupUpdater.update(e);
        }
      }
    };
    mySecondaryActions.getTemplatePresentation().setIcon(AllIcons.General.GearPlain);
    mySecondaryActions.setPopup(true);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      addLoadingIcon();
    }

    // If the panel doesn't handle mouse event then it will be passed to its parent.
    // It means that if the panel is in sliding mode then the focus goes to the editor
    // and panel will be automatically hidden.
    enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.COMPONENT_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK);
    setMiniModeInner(false);
    myPopupHandler = CustomizationUtil.installToolbarCustomizationHandler(this);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    for (Component component : getComponents()) {
      tweakActionComponentUI(component);
    }
  }

  public @NotNull String getPlace() {
    return myPlace;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (ComponentUtil.getParentOfType(CellRendererPane.class, this) != null) return;
    ourToolbars.add(this);

    if (isShowing()) {
      updateActionsImmediately();
    }
    else {
      UiNotifyConnector.doWhenFirstShown(this, () -> {
        if (myForcedUpdateRequested && myLastUpdate == null) { // a first update really
          updateActionsImmediately();
        }
      });
    }
  }

  protected boolean isInsideNavBar() {
    return ActionPlaces.NAVIGATION_BAR_TOOLBAR.equals(myPlace);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (ComponentUtil.getParentOfType(CellRendererPane.class, this) != null) return;
    ourToolbars.remove(this);

    if (myPopup != null) {
      myPopup.cancel();
      myPopup = null;
    }

    CancellablePromise<List<AnAction>> lastUpdate = myLastUpdate;
    myLastUpdate = null;
    if (lastUpdate != null) lastUpdate.cancel();
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }

  @Override
  public int getLayoutPolicy() {
    return myLayoutPolicy;
  }

  @Override
  public void setLayoutPolicy(@LayoutPolicy int layoutPolicy) {
    if (layoutPolicy != NOWRAP_LAYOUT_POLICY && layoutPolicy != WRAP_LAYOUT_POLICY && layoutPolicy != AUTO_LAYOUT_POLICY) {
      throw new IllegalArgumentException("wrong layoutPolicy: " + layoutPolicy);
    }
    myLayoutPolicy = layoutPolicy;
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  public @NotNull ActionGroup getActionGroup() {
    return myActionGroup;
  }

  @Override
  public @NotNull ShowHideAnimator getAlphaAnimator() {
    return myAlphaContext.getAnimator();
  }

  @Override
  public void paint(Graphics g) {
    myAlphaContext.paint(g, () -> super.paint(g));
  }

  @Override
  protected void paintComponent(final Graphics g) {
    if (myCachedImage != null) {
      UIUtil.drawImage(g, myCachedImage, 0, 0, null);
      return;
    }
    super.paintComponent(g);

    if (myLayoutPolicy == AUTO_LAYOUT_POLICY && myAutoPopupRec != null) {
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

  public void setSecondaryButtonPopupStateModifier(@NotNull ActionToolbarImpl.SecondaryGroupUpdater secondaryGroupUpdater) {
    mySecondaryGroupUpdater = secondaryGroupUpdater;
  }

  protected void fillToolBar(@NotNull List<? extends AnAction> actions, boolean layoutSecondaries) {
    boolean isLastElementSeparator = false;
    List<AnAction> rightAligned = new ArrayList<>();
    for (int i = 0; i < actions.size(); i++) {
      AnAction action = actions.get(i);
      if (action instanceof RightAlignedToolbarAction) {
        rightAligned.add(action);
        continue;
      }

      if (layoutSecondaries) {
        if (!myActionGroup.isPrimary(action)) {
          mySecondaryActions.add(action);
          continue;
        }
      }

      if (action instanceof Separator) {
        if (isLastElementSeparator) continue;
        if (i > 0 && i < actions.size() - 1) {
          add(SEPARATOR_CONSTRAINT, new MySeparator(myShowSeparatorTitles ? ((Separator)action).getText() : null));
          isLastElementSeparator = true;
          continue;
        }
      }
      else if (action instanceof CustomComponentAction) {
        add(CUSTOM_COMPONENT_CONSTRAINT, getCustomComponent(action));
      }
      else {
        add(ACTION_BUTTON_CONSTRAINT, createToolbarButton(action));
      }
      isLastElementSeparator = false;
    }

    if (mySecondaryActions.getChildrenCount() > 0) {
      mySecondaryActionsButton =
        new ActionButton(mySecondaryActions, myPresentationFactory.getPresentation(mySecondaryActions), myPlace, getMinimumButtonSize()) {
          @Override
          protected String getShortcutText() {
            String shortcut = myPresentation.getClientProperty(SECONDARY_SHORTCUT);
            return shortcut != null ? shortcut : super.getShortcutText();
          }
        };
      mySecondaryActionsButton.setNoIconsInPopup(true);
      add(SECONDARY_ACTION_CONSTRAINT, mySecondaryActionsButton);
    }

    for (AnAction action : rightAligned) {
      JComponent button = action instanceof CustomComponentAction ? getCustomComponent(action) : createToolbarButton(action);
      if (!isInsideNavBar()) {
        button.putClientProperty(RIGHT_ALIGN_KEY, Boolean.TRUE);
      }
      add(button);
    }
  }

  @Override
  protected void addImpl(Component comp, Object constraints, int index) {
    super.addImpl(comp, constraints, index);
    if (myPopupHandler != null && !ContainerUtil.exists(comp.getMouseListeners(), listener -> listener instanceof PopupHandler)) {
      comp.addMouseListener(myPopupHandler);
    }
  }

  final protected @NotNull JComponent getCustomComponent(@NotNull AnAction action) {
    Presentation presentation = myPresentationFactory.getPresentation(action);
    JComponent customComponent = presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY);
    if (customComponent == null) {
      customComponent = createCustomComponent((CustomComponentAction)action, presentation);
      if (customComponent.getParent() != null && customComponent.getClientProperty(SUPPRESS_ACTION_COMPONENT_WARNING) == null) {
        customComponent.putClientProperty(SUPPRESS_ACTION_COMPONENT_WARNING, true);
        LOG.warn(action.getClass().getSimpleName() + ".component.getParent() != null in '" + myPlace + "' toolbar. " +
                 "Custom components shall not be reused.");
      }
      presentation.putClientProperty(CustomComponentAction.COMPONENT_KEY, customComponent);
      ComponentUtil.putClientProperty(customComponent, CustomComponentAction.ACTION_KEY, action);
    }
    tweakActionComponentUI(customComponent);

    AbstractButton clickable = UIUtil.findComponentOfType(customComponent, AbstractButton.class);
    if (clickable != null) {
      class ToolbarClicksCollectorListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) { ToolbarClicksCollector.record(action, myPlace, e, getDataContext()); }
      }
      if (!ContainerUtil.exists(clickable.getMouseListeners(), o -> o instanceof ToolbarClicksCollectorListener)) {
        clickable.addMouseListener(new ToolbarClicksCollectorListener());
      }
    }
    return customComponent;
  }

  protected @NotNull JComponent createCustomComponent(@NotNull CustomComponentAction action, @NotNull Presentation presentation) {
    JComponent result = action.createCustomComponent(presentation, myPlace);
    ToolbarActionTracker.followToolbarComponent(presentation, result, getComponent());
    return result;
  }

  private void tweakActionComponentUI(@NotNull Component actionComponent) {
    if (ActionPlaces.EDITOR_TOOLBAR.equals(myPlace)) {
      // tweak font & color for editor toolbar to match editor tabs style
      actionComponent.setFont(RelativeFont.NORMAL.fromResource("Toolbar.Component.fontSizeOffset", -2).derive(StartupUiUtil.getLabelFont()));
      actionComponent.setForeground(ColorUtil.dimmer(JBColor.BLACK));
    }
  }

  private @NotNull Dimension getMinimumButtonSize() {
    return isInsideNavBar() ? NAVBAR_MINIMUM_BUTTON_SIZE : DEFAULT_MINIMUM_BUTTON_SIZE;
  }

  private @NotNull JBEmptyBorder getActionButtonBorder() {
    return myOrientation == SwingConstants.VERTICAL ? JBUI.Borders.empty(2, 1) : JBUI.Borders.empty(1, 2);
  }

  protected @NotNull ActionButton createToolbarButton(@NotNull AnAction action,
                                                      final ActionButtonLook look,
                                                      @NotNull String place,
                                                      @NotNull Presentation presentation,
                                                      @NotNull Dimension minimumSize) {
    if (action.displayTextInToolbar()) {
      int mnemonic = KeyEvent.getExtendedKeyCodeForChar(action.getTemplatePresentation().getMnemonic());

      ActionButtonWithText buttonWithText = new ActionButtonWithText(action, presentation, place, minimumSize);

      if (mnemonic != KeyEvent.VK_UNDEFINED) {
        buttonWithText.registerKeyboardAction(__ -> buttonWithText.click(), KeyStroke.getKeyStroke(mnemonic,
                                InputEvent.ALT_DOWN_MASK), WHEN_IN_FOCUSED_WINDOW);
      }
      tweakActionComponentUI(buttonWithText);
      return buttonWithText;
    }

    ActionButton actionButton = new ActionButton(action, presentation, place, minimumSize) {
      @Override
      protected DataContext getDataContext() {
        return getToolbarDataContext();
      }

      @Override
      protected @NotNull Icon getFallbackIcon(boolean enabled) {
        return enabled ? AllIcons.Toolbar.Unknown : IconLoader.getDisabledIcon(AllIcons.Toolbar.Unknown);
      }
    };

    actionButton.setLook(look);
    actionButton.setBorder(getActionButtonBorder());

    ToolbarActionTracker.followToolbarComponent(presentation, actionButton, getComponent());
    return actionButton;
  }

  final protected @NotNull ActionButton createToolbarButton(@NotNull AnAction action) {
    return createToolbarButton(
      action,
      myMinimalMode ? myMinimalButtonLook : myDecorateButtons ? new ActionButtonLook() {
        @Override
        public void paintBorder(Graphics g, JComponent c, int state) {
          g.setColor(JBColor.border());
          g.drawLine(c.getWidth() - 1, 0, c.getWidth() - 1, c.getHeight());
        }

        @Override
        public void paintBackground(Graphics g, JComponent component, int state) {
          if (state == ActionButtonComponent.PUSHED) {
            g.setColor(component.getBackground().darker());
            ((Graphics2D)g).fill(g.getClip());
          }
        }
      } : null,
      myPlace, myPresentationFactory.getPresentation(action),
      myMinimumButtonSize.size());
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

  protected Dimension getChildPreferredSize(int index) {
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

  private void calculateBoundsNowrapImpl(@NotNull List<? extends Rectangle> bounds) {
    final int componentCount = getComponentCount();
    LOG.assertTrue(componentCount <= bounds.size());

    final int width = getWidth();
    final int height = getHeight();

    final Insets insets = getInsets();

    if (myAdjustTheSameSize) {
      final int maxWidth = getMaxButtonWidth();
      final int maxHeight = getMaxButtonHeight();

      int offset = 0;
      if (myOrientation == SwingConstants.HORIZONTAL) {
        for (int i = 0; i < componentCount; i++) {
          final Rectangle r = bounds.get(i);
          r.setBounds(insets.left + offset, insets.top + (height - maxHeight) / 2, maxWidth, maxHeight);
          offset += maxWidth;
        }
      }
      else {
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

  private void calculateBoundsAutoImp(@NotNull Dimension sizeToFit, @NotNull List<? extends Rectangle> bounds) {
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
          boolean inside = isLast ? eachX + eachBound.width <= widthToFit : eachX + eachBound.width + autoButtonSize <= widthToFit;

          if (inside) {
            if (eachComp == mySecondaryActionsButton) {
              assert isLast;
              if (sizeToFit.width != Integer.MAX_VALUE && !myNoGapMode) {
                eachBound.x = sizeToFit.width - insets.right - eachBound.width;
                eachX = (int)eachBound.getMaxX() - insets.left;
              }
              else {
                eachBound.x = insets.left + eachX;
              }
            }
            else {
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

  private void calculateBoundsWrapImpl(@NotNull Dimension sizeToFit, @NotNull List<? extends Rectangle> bounds) {
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
      final int maxWidth = getMaxButtonWidth();
      final int maxHeight = getMaxButtonHeight();
      int xOffset = 0;
      int yOffset = 0;
      if (myOrientation == SwingConstants.HORIZONTAL) {

        // Lay components out
        int maxRowWidth = getMaxRowWidth(widthToFit, maxWidth);
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

        // Lay components out
        // Calculate max size of a row. It's not possible to make more then 3 column toolbar
        final int maxRowHeight = Math.max(heightToFit, componentCount * myMinimumButtonSize.height() / 3);
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
        int maxRowWidth = getMaxRowWidth(widthToFit, myMinimumButtonSize.width());

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
        final int maxRowHeight = Math.max(heightToFit, componentCount * myMinimumButtonSize.height() / 3);
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

  private int getMaxRowWidth(int widthToFit, int maxWidth) {
    int componentCount = getComponentCount();
    // Calculate max size of a row. It's not possible to make more than 3 row toolbar
    int maxRowWidth = Math.max(widthToFit, componentCount * maxWidth / 3);
    for (int i = 0; i < componentCount; i++) {
      final Component component = getComponent(i);
      if (component instanceof JComponent && ((JComponent)component).getClientProperty(RIGHT_ALIGN_KEY) == Boolean.TRUE) {
        maxRowWidth -= getChildPreferredSize(i).width;
      }
    }
    return maxRowWidth;
  }

  /**
   * Calculates bounds of all the components in the toolbar
   */
  protected void calculateBounds(@NotNull Dimension size2Fit, @NotNull List<Rectangle> bounds) {
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
        }
      }
    }
  }

  @Override
  public @NotNull Dimension getPreferredSize() {
    if (myCachedImage != null) {
      return new Dimension(ImageUtil.getUserWidth(myCachedImage), ImageUtil.getUserHeight(myCachedImage));
    }
    return updatePreferredSize(super.getPreferredSize());
  }

  protected Dimension updatePreferredSize(Dimension preferredSize) {
    final ArrayList<Rectangle> bounds = new ArrayList<>();
    int forcedHeight;
    if (getWidth() > 0 && getLayoutPolicy() == ActionToolbar.WRAP_LAYOUT_POLICY && myOrientation == SwingConstants.HORIZONTAL) {
      calculateBounds(new Dimension(getWidth(), Integer.MAX_VALUE), bounds);
      Rectangle union = null;
      for (Rectangle bound : bounds) {
        union = union == null ? bound : union.union(bound);
      }
      forcedHeight = union != null ? union.height : 0;
    } else {
      calculateBounds(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE), bounds); // it doesn't take into account wrapping
      forcedHeight = 0;
    }
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
    final Dimension dimension = new Dimension(xRight - xLeft, Math.max(yBottom - yTop, forcedHeight));

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

  /**
   * Forces the minimum size of the toolbar to show all buttons, When set to {@code true}. By default ({@code false}) the
   * toolbar will shrink further and show the auto popup chevron button.
   */
  public void setForceMinimumSize(boolean force) {
    myForceMinimumSize = force;
  }

  /**
   * By default minimum size is to show chevron only.
   * If this option is {@code true} toolbar shows at least one (the first) component plus chevron (if need)
   */
  public void setForceShowFirstComponent(boolean showFirstComponent) {
    myForceShowFirstComponent = showFirstComponent;
  }

  /**
   * This option makes sense when you use a toolbar inside JBPopup
   * When some 'actions' are hidden under the chevron the popup with extra components would be shown/hidden
   * with size adjustments for the main popup (this is default behavior).
   * If this option is {@code true} size adjustments would be omitted
   */
  public void setSkipWindowAdjustments(boolean skipWindowAdjustments) {
    mySkipWindowAdjustments = skipWindowAdjustments;
  }

  @Override
  public Dimension getMinimumSize() {
    return updateMinimumSize(super.getMinimumSize());
  }

  protected Dimension updateMinimumSize(Dimension minimumSize) {
    if (myForceMinimumSize) {
      return updatePreferredSize(minimumSize);
    }
    if (myLayoutPolicy == AUTO_LAYOUT_POLICY) {
      final Insets i = getInsets();
      if (myForceShowFirstComponent && getComponentCount() > 0 && getComponent(0).isShowing()) {
        Component c = getComponent(0);
        Dimension firstSize = c.getPreferredSize();
        if (myOrientation == SwingConstants.HORIZONTAL) {
          return new Dimension(firstSize.width + AllIcons.Ide.Link.getIconWidth() + i.left + i.right,
                               Math.max(firstSize.height, myMinimumButtonSize.height()) + i.top + i.bottom);
        }
        else {
          return new Dimension(Math.max(firstSize.width, AllIcons.Ide.Link.getIconWidth()) + i.left + i.right,
                               firstSize.height + myMinimumButtonSize.height() + i.top + i.bottom);
        }
      }
      return new Dimension(AllIcons.Ide.Link.getIconWidth() + i.left + i.right, myMinimumButtonSize.height() + i.top + i.bottom);
    }
    else {
      return minimumSize;
    }
  }

  protected @NotNull Color getSeparatorColor() {
    return JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground();
  }

  protected int getSeparatorHeight() {
    return JBUIScale.scale(24);
  }

  private static @Nullable Image paintToImage(@NotNull JComponent comp) {
    Dimension size = comp.getSize();
    if (size.width < 1 || size.height < 1) return null;
    BufferedImage image = UIUtil.createImage(comp, size.width, size.height, BufferedImage.TYPE_INT_ARGB);
    UIUtil.useSafely(image.getGraphics(), comp::paint);
    return image;
  }

  private final class MySeparator extends JComponent {
    private final String myText;

    private MySeparator(String text) {
      myText = text;
      setFont(JBUI.Fonts.toolbarSmallComboBoxFont());
    }

    @Override
    public Dimension getPreferredSize() {
      int gap = JBUIScale.scale(2);
      int center = JBUIScale.scale(3);
      int width = gap * 2 + center;
      int height = getSeparatorHeight();

      if (myOrientation == SwingConstants.HORIZONTAL) {
        if (myText != null) {
          FontMetrics fontMetrics = getFontMetrics(getFont());

          int textWidth = getTextWidth(fontMetrics, myText, getGraphics());
          return new JBDimension(width + gap * 2 + textWidth,
                                 Math.max(fontMetrics.getHeight(), height), true);
        }
        else {
          return new JBDimension(width, height, true);
        }
      }
      else {
        //noinspection SuspiciousNameCombination
        return new JBDimension(height, width, true);
      }
    }

    @Override
    protected void paintComponent(final Graphics g) {
      if (getParent() == null) return;

      int gap = JBUIScale.scale(2);
      int center = JBUIScale.scale(3);
      int offset;
      if (myOrientation == SwingConstants.HORIZONTAL) {
        offset = ActionToolbarImpl.this.getHeight() - getMaxButtonHeight() - 1;
      }
      else {
        offset = ActionToolbarImpl.this.getWidth() - getMaxButtonWidth() - 1;
      }

      g.setColor(getSeparatorColor());
      if (myOrientation == SwingConstants.HORIZONTAL) {
        int y2 = ActionToolbarImpl.this.getHeight() - gap * 2 - offset;
        LinePainter2D.paint((Graphics2D)g, center, gap, center, y2);

        if (myText != null) {
          FontMetrics fontMetrics = getFontMetrics(getFont());
          int top = (getHeight() - fontMetrics.getHeight()) / 2;
          UISettings.setupAntialiasing(g);
          g.setColor(JBColor.foreground());
          g.drawString(myText, gap * 2 + center + gap, top + fontMetrics.getAscent());
        }
      }
      else {
        LinePainter2D.paint((Graphics2D)g, gap, center, ActionToolbarImpl.this.getWidth() - gap * 2 - offset, center);
      }
    }

    private int getTextWidth(@NotNull FontMetrics fontMetrics, @NotNull String text, @Nullable Graphics graphics) {
      if (graphics == null) {
        return fontMetrics.stringWidth(text);
      }
      else {
        Graphics g = graphics.create();
        try {
          UISettings.setupAntialiasing(g);
          return fontMetrics.getStringBounds(text, g).getBounds().width;
        }
        finally {
          g.dispose();
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
  public void setMinimumButtonSize(final @NotNull Dimension size) {
    myMinimumButtonSize = JBDimension.create(size, true);
    for (int i = getComponentCount() - 1; i >= 0; i--) {
      final Component component = getComponent(i);
      if (component instanceof ActionButton) {
        final ActionButton button = (ActionButton)component;
        button.setMinimumButtonSize(size);
      }
      else if (component instanceof JLabel && LOADING_LABEL.equals(component.getName())) {
        Dimension dimension = new Dimension();
        dimension.width = Math.max(myMinimumButtonSize.width, ((JLabel)component).getIcon().getIconWidth());
        dimension.height = Math.max(myMinimumButtonSize.height, ((JLabel)component).getIcon().getIconHeight());
        JBInsets.addTo(dimension, ((JLabel)component).getInsets());
        component.setPreferredSize(dimension);
      }
    }
    revalidate();
  }

  @Override
  public void setOrientation(@MagicConstant(intValues = {SwingConstants.HORIZONTAL, SwingConstants.VERTICAL}) int orientation) {
    if (SwingConstants.HORIZONTAL != orientation && SwingConstants.VERTICAL != orientation) {
      throw new IllegalArgumentException("wrong orientation: " + orientation);
    }
    myOrientation = orientation;
  }

  @MagicConstant(intValues = {SwingConstants.HORIZONTAL, SwingConstants.VERTICAL})
  public int getOrientation() {
    return myOrientation;
  }

  /** Async toolbars are not updated immediately despite the name of the method. */
  @Override
  public void updateActionsImmediately() {
    updateActionsImmediately(false);
  }

  @ApiStatus.Internal
  public void updateActionsImmediately(boolean includeInvisible) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    boolean isTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (getParent() == null && myTargetComponent == null && !isTestMode && !includeInvisible) {
      LOG.warn(new Throwable("'" + myPlace + "' toolbar manual update is ignored. " +
                             "Newly created toolbars are updated automatically on `addNotify`.", myCreationTrace));
      return;
    }
    if (!isTestMode && myCachedImage == null && getComponentCount() == 0 && isShowing()) {
      addLoadingIcon();
    }
    myUpdater.updateActions(true, false, includeInvisible);
  }

  private boolean myHideDisabled = false;

  public void setHideDisabled(boolean hideDisabled) {
    myHideDisabled = hideDisabled;
    updateActionsImmediately();
  }

  private void updateActionsImpl(boolean forced) {
    if (forced) myForcedUpdateRequested = true;

    DataContext dataContext = Utils.wrapDataContext(getDataContext());
    ActionUpdater updater = new ActionUpdater(LaterInvocator.isInModalContext(), myPresentationFactory,
                                              dataContext, myPlace, false, true);
    if (Utils.isAsyncDataContext(dataContext)) {
      CancellablePromise<List<AnAction>> lastUpdate = myLastUpdate;
      myLastUpdate = null;
      if (lastUpdate != null) lastUpdate.cancel();

      updateActionsImplFastTrack(updater);

      boolean forcedActual = forced || myForcedUpdateRequested;
      CancellablePromise<List<AnAction>> promise = myLastUpdate = updater.expandActionGroupAsync(myActionGroup, myHideDisabled);
      promise
        .onSuccess(actions -> {
          if (myLastUpdate == promise) myLastUpdate = null;
          actionsUpdated(forcedActual, actions);
        })
        .onError(ex -> {
          if (!(ex instanceof ControlFlowException || ex instanceof CancellationException)) {
            LOG.error(ex);
          }
        });
    }
    else {
      boolean forcedActual = forced || myForcedUpdateRequested;
      actionsUpdated(forcedActual, updater.expandActionGroupWithTimeout(myActionGroup, myHideDisabled));
    }
    if (mySecondaryActionsButton != null) {
      mySecondaryActionsButton.update();
      mySecondaryActionsButton.repaint();
    }
  }

  private void updateActionsImplFastTrack(@NotNull ActionUpdater updater) {
    String failedKey = "ActionToolbarImpl.fastTrackFailed";
    if (!(myVisibleActions.isEmpty() && getComponentCount() == 1 && getClientProperty(failedKey) == null)) {
      return;
    }
    List<AnAction> actions = Utils.expandActionGroupFastTrack(updater, myActionGroup, myHideDisabled, null);
    if (actions != null) {
      actionsUpdated(true, actions);
    }
    else {
      putClientProperty(failedKey, true);
    }
  }

  private void addLoadingIcon() {
    AnimatedIcon icon = AnimatedIcon.Default.INSTANCE;
    JLabel label = new JLabel();
    label.setName(LOADING_LABEL);
    label.setBorder(getActionButtonBorder());
    if (this instanceof PopupToolbar) {
      label.setIcon(icon);
    }
    else {
      label.setIcon(EmptyIcon.create(icon.getIconWidth(), icon.getIconHeight()));
      boolean suppressLoading = ActionPlaces.MAIN_TOOLBAR.equals(myPlace) ||
                                ActionPlaces.NAVIGATION_BAR_TOOLBAR.equals(myPlace) ||
                                ActionPlaces.TOOLWINDOW_TITLE.equals(myPlace) ||
                                ActionPlaces.WELCOME_SCREEN.equals(myPlace);
      if (!suppressLoading) {
        EdtScheduledExecutorService.getInstance().schedule(
          () -> label.setIcon(icon), Registry.intValue("actionSystem.toolbar.progress.icon.delay", 500), TimeUnit.MILLISECONDS);
      }
    }
    myForcedUpdateRequested = true;
    add(label);
  }

  protected boolean canUpdateActions(@NotNull List<? extends AnAction> newVisibleActions) {
    return !newVisibleActions.equals(myVisibleActions) || myPresentationFactory.isNeedRebuild();
  }

  protected void actionsUpdated(boolean forced, @NotNull List<? extends AnAction> newVisibleActions) {
    myListeners.getMulticaster().actionsUpdated();
    if (forced || canUpdateActions(newVisibleActions)) {
      myForcedUpdateRequested = false;
      myCachedImage = null;
      boolean shouldRebuildUI = newVisibleActions.isEmpty() || myVisibleActions.isEmpty();
      myVisibleActions = newVisibleActions;

      boolean skipSizeAdjustments = mySkipWindowAdjustments;
      Component compForSize = skipSizeAdjustments ? null : guessBestParentForSizeAdjustment();
      Dimension oldSize = skipSizeAdjustments ? null : compForSize.getPreferredSize();

      removeAll();
      mySecondaryActions.removeAll();
      mySecondaryActionsButton = null;
      fillToolBar(myVisibleActions, getLayoutPolicy() == AUTO_LAYOUT_POLICY && myOrientation == SwingConstants.HORIZONTAL);
      myPresentationFactory.resetNeedRebuild();

      if (!skipSizeAdjustments) {
        Dimension availSize = compForSize.getSize();
        Dimension newSize = compForSize.getPreferredSize();
        adjustContainerWindowSize(shouldRebuildUI, availSize, oldSize, newSize);
      }

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

  private void adjustContainerWindowSize(boolean shouldRebuildUI,
                                         @NotNull Dimension availSize,
                                         @NotNull Dimension oldSize,
                                         @NotNull Dimension newSize) {
    Dimension delta = new Dimension(newSize.width - oldSize.width, newSize.height - oldSize.height);
    if (!shouldRebuildUI) {
      if (myOrientation == SwingConstants.HORIZONTAL) delta.width = 0;
      if (myOrientation == SwingConstants.VERTICAL) delta.height = 0;
    }
    delta.width = Math.max(0, delta.width - Math.max(0, availSize.width - oldSize.width));
    delta.height = Math.max(0, delta.height - Math.max(0, availSize.height - oldSize.height));
    if (delta.width == 0 && delta.height == 0) {
      return;
    }
    JBPopup popup = PopupUtil.getPopupContainerFor(this);
    if (popup != null) {
      Dimension size = popup.getSize();
      size.width += delta.width;
      size.height += delta.height;
      popup.setSize(size);
      popup.moveToFitScreen();
    }
    else {
      JComponent parent = getParentLightweightHintComponent(this);
      if (parent != null) { // a LightweightHint that fits in
        Dimension size = parent.getSize();
        size.width += delta.width;
        size.height += delta.height;
        parent.setSize(size);
      }
    }
  }

  private @NotNull Component guessBestParentForSizeAdjustment() {
    if (this instanceof PopupToolbar) return this;
    Component result = ObjectUtils.chooseNotNull(getParent(), this);
    Dimension availSize = result.getSize();
    for (Component p = result.getParent(); p != null && !(p instanceof JRootPane); p = p.getParent()) {
      Dimension pSize = p.getSize();
      if (myOrientation == SwingConstants.HORIZONTAL && pSize.height - availSize.height > 8 ||
          myOrientation == SwingConstants.VERTICAL && pSize.width - availSize.width > 8) {
        break;
      }
      result = p;
      availSize = result.getSize();
    }
    return result;
  }

  @Nullable
  private static JComponent getParentLightweightHintComponent(@Nullable JComponent component) {
    Ref<JComponent> result = Ref.create();
    UIUtil.uiParents(component, false).reduce((a, b) -> {
      if (b instanceof JLayeredPane && ((JLayeredPane)b).getLayer(a) == JLayeredPane.POPUP_LAYER) {
        result.set((JComponent)a);
      }
      return b;
    });
    return result.get();
  }

  @Override
  public boolean hasVisibleActions() {
    return !myVisibleActions.isEmpty();
  }

  public boolean hasVisibleAction(@NotNull AnAction action) {
    return myVisibleActions.contains(action);
  }

  @ApiStatus.Internal
  @Override
  public @Nullable JComponent getTargetComponent() {
    return myTargetComponent;
  }

  @Override
  public void setTargetComponent(@Nullable JComponent component) {
    if (myTargetComponent == null) {
      putClientProperty(SUPPRESS_TARGET_COMPONENT_WARNING, true);
    }
    if (myTargetComponent != component) {
      myTargetComponent = component;
      if (isShowing()) {
        updateActionsImmediately();
      }
    }
  }

  @Override
  public @NotNull DataContext getToolbarDataContext() {
    return getDataContext();
  }

  @Override
  public void setShowSeparatorTitles(boolean showSeparatorTitles) {
    myShowSeparatorTitles = showSeparatorTitles;
  }

  @Override
  public void addListener(@NotNull ActionToolbarListener listener, @NotNull Disposable parentDisposable) {
    myListeners.addListener(listener, parentDisposable);
  }

  protected @NotNull DataContext getDataContext() {
    if (myTargetComponent == null && getClientProperty(SUPPRESS_TARGET_COMPONENT_WARNING) == null &&
        !ApplicationManager.getApplication().isUnitTestMode()) {
      putClientProperty(SUPPRESS_TARGET_COMPONENT_WARNING, true);
      LOG.warn("'" + myPlace + "' toolbar by default uses any focused component to update its actions. " +
               "Toolbar actions that need local UI context would be incorrectly disabled. " +
               "Please call toolbar.setTargetComponent() explicitly.", myCreationTrace);
    }
    Component target = myTargetComponent != null ? myTargetComponent : getFocusedComponentInWindowOrSelf(this);
    return DataManager.getInstance().getDataContext(target);
  }

  @Override
  protected void processMouseMotionEvent(final MouseEvent e) {
    super.processMouseMotionEvent(e);

    if (getLayoutPolicy() != AUTO_LAYOUT_POLICY) {
      return;
    }
    if (myAutoPopupRec != null && myAutoPopupRec.contains(e.getPoint())) {
      IdeFocusManager.getInstance(null).doWhenFocusSettlesDown(this::showAutoPopup);
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

    PopupToolbar popupToolbar = new PopupToolbar(myPlace, group, true, this) {
      @Override
      protected void onOtherActionPerformed() {
        hidePopup();
      }

      @Override
      protected @NotNull DataContext getDataContext() {
        return ActionToolbarImpl.this.getDataContext();
      }
    };
    popupToolbar.setLayoutPolicy(NOWRAP_LAYOUT_POLICY);

    Point location;
    if (myOrientation == SwingConstants.HORIZONTAL) {
      location = getLocationOnScreen();

      ToolWindow toolWindow = DataManager.getInstance().getDataContext(this).getData(PlatformDataKeys.TOOL_WINDOW);
      if (toolWindow != null && toolWindow.getAnchor() == ToolWindowAnchor.RIGHT) {
        int rightXOnScreen = location.x + getWidth();
        int toolbarPreferredWidth = popupToolbar.getPreferredSize().width;
        location.x = rightXOnScreen - toolbarPreferredWidth;
      }
    }
    else {
      location = getLocationOnScreen();
      location.y = location.y + getHeight() - popupToolbar.getPreferredSize().height;
    }

    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(popupToolbar, null);
    builder.setResizable(false)
      .setMovable(true) // fit the screen automatically
      .setRequestFocus(true)
      .setMayBeParent(true)
      .setTitle(null)
      .setCancelOnClickOutside(true)
      .setCancelOnOtherWindowOpen(true)
      .setCancelCallback(() -> {
        final boolean toClose = actionManager.isActionPopupStackEmpty();
        if (toClose) {
          myUpdater.updateActions(false, true, false);
        }
        return toClose;
      })
      .setCancelOnMouseOutCallback(event -> {
        Window window = UIUtil.getWindow(popupToolbar);
        if (window != null && Window.Type.POPUP == window.getType()) {
          Component parent = UIUtil.uiParents(event.getComponent(), false).find(window::equals);
          if (parent != null) return false; // mouse over a child popup
        }
        return myAutoPopupRec != null &&
               actionManager.isActionPopupStackEmpty() &&
               !new RelativeRectangle(this, myAutoPopupRec).contains(new RelativePoint(event));
      });

    builder.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        processClosed();
      }
    });
    myPopup = builder.createPopup();
    Disposer.register(myPopup, popupToolbar);

    myPopup.showInScreenCoordinates(this, location);

    Window window = SwingUtilities.getWindowAncestor(this);
    if (window == null) {
      return;
    }

    ComponentListener componentAdapter = new ComponentAdapter() {
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
    Disposer.register(popupToolbar, () -> window.removeComponentListener(componentAdapter));
  }

  private boolean isPopupShowing() {
    return myPopup != null && !myPopup.isDisposed();
  }

  private void hidePopup() {
    if (myPopup != null) {
      myPopup.cancel();
      processClosed();
    }
  }

  private void processClosed() {
    if (myPopup == null) return;
    if (myPopup.isVisible()) {
      // setCancelCallback(..) can override cancel()
      return;
    }
    // cancel() already called Disposer.dispose()
    myPopup = null;
    myUpdater.updateActions(false, false, false);
  }

  abstract static class PopupToolbar extends ActionToolbarImpl implements AnActionListener, Disposable {
    final ActionToolbarImpl myParent;

    PopupToolbar(@NotNull String place,
                 @NotNull ActionGroup actionGroup,
                 final boolean horizontal,
                 @NotNull ActionToolbarImpl parent) {
      super(place, actionGroup, horizontal, false);
      ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(AnActionListener.TOPIC, this);
      myParent = parent;
      setBorder(myParent.getBorder());
    }

    @Override
    public Container getParent() {
      Container parent = super.getParent();
      return parent != null ? parent : myParent;
    }

    @Override
    public @NotNull Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      size.width = Math.max(size.width, DEFAULT_MINIMUM_BUTTON_SIZE.width);
      size.height = Math.max(size.height, DEFAULT_MINIMUM_BUTTON_SIZE.height);
      if (isPaintParentWhileLoading()) {
        Dimension parentSize = myParent.getSize();
        size.width += parentSize.width;
        size.height = parentSize.height;
      }
      return size;
    }

    @Override
    public void doLayout() {
      super.doLayout();
      if (isPaintParentWhileLoading()) {
        JLabel component = (JLabel)getComponent(0);
        component.setLocation(component.getX() + myParent.getWidth(), component.getY());
      }
    }

    @Override
    public void paint(Graphics g) {
      if (isPaintParentWhileLoading()) {
        myParent.paint(g);
        paintChildren(g);
      }
      else {
        super.paint(g);
      }
    }

    boolean isPaintParentWhileLoading() {
      return getOrientation() == SwingConstants.HORIZONTAL &&
             myParent.getOrientation() == SwingConstants.HORIZONTAL &&
             !hasVisibleActions() && myParent.hasVisibleActions() &&
             getComponentCount() == 1 && getComponent(0) instanceof JLabel &&
             LOADING_LABEL.equals(getComponent(0).getName());
    }

    @Override
    public void dispose() {
    }

    @Override
    public void afterActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event, @NotNull AnActionResult result) {
      if (!hasVisibleAction(action)) {
        onOtherActionPerformed();
      }
    }

    protected abstract void onOtherActionPerformed();
  }

  @Override
  public void setReservePlaceAutoPopupIcon(final boolean reserve) {
    myReservePlaceAutoPopupIcon = reserve;
  }

  @Override
  public void setSecondaryActionsTooltip(@NotNull @NlsContexts.Tooltip String secondaryActionsTooltip) {
    mySecondaryActions.getTemplatePresentation().setText(secondaryActionsTooltip);
  }

  public void setSecondaryActionsShortcut(@NotNull String secondaryActionsShortcut) {
    mySecondaryActions.getTemplatePresentation().putClientProperty(SECONDARY_SHORTCUT, secondaryActionsShortcut);
  }

  @Override
  public void setSecondaryActionsIcon(Icon icon) {
    setSecondaryActionsIcon(icon, false);
  }

  @Override
  public void setSecondaryActionsIcon(Icon icon, boolean hideDropdownIcon) {
    Presentation presentation = mySecondaryActions.getTemplatePresentation();
    presentation.setIcon(icon);
    presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, hideDropdownIcon ? Boolean.TRUE : null);
  }

  public void setNoGapMode() {
    myNoGapMode = true;
  }

  @Override
  public @NotNull List<AnAction> getActions(boolean originalProvider) {
    return getActions();
  }

  @Override
  public @NotNull List<AnAction> getActions() {
    List<AnAction> result = new ArrayList<>();

    List<AnAction> secondary = new ArrayList<>();
    AnAction[] kids = myActionGroup.getChildren(null);
    for (AnAction each : kids) {
      if (myActionGroup.isPrimary(each)) {
        result.add(each);
      }
      else {
        secondary.add(each);
      }
    }
    result.add(new Separator());
    result.addAll(secondary);

    return result;
  }

  @Override
  public void setMiniMode(boolean minimalMode) {
    if (myMinimalMode == minimalMode) return;
    setMiniModeInner(minimalMode);
    myUpdater.updateActions(false, true, false);
  }

  private void setMiniModeInner(boolean minimalMode) {
    myMinimalMode = minimalMode;
    if (myMinimalMode) {
      setMinimumButtonSize(JBUI.emptySize());
      setLayoutPolicy(NOWRAP_LAYOUT_POLICY);
      setBorder(JBUI.Borders.empty());
      setOpaque(false);
    }
    else {
      Insets i = myOrientation == SwingConstants.VERTICAL ? UIManager.getInsets("ToolBar.verticalToolbarInsets")
                                                          : UIManager.getInsets("ToolBar.horizontalToolbarInsets");
      if (i != null) {
        setBorder(JBUI.Borders.empty(i.top, i.left, i.bottom, i.right));
      } else {
        setBorder(JBUI.Borders.empty(2));
      }

      setMinimumButtonSize(myDecorateButtons ? JBUI.size(30, 20) : DEFAULT_MINIMUM_BUTTON_SIZE);
      setOpaque(true);
      setLayoutPolicy(AUTO_LAYOUT_POLICY);
    }
  }

  public static boolean isInPopupToolbar(@Nullable Component component) {
    return ComponentUtil.getParentOfType(PopupToolbar.class, component) != null;
  }

  @TestOnly
  public static ActionToolbarImpl findToolbar(ActionGroup group) {
    for (ActionToolbarImpl toolbar : ourToolbars) {
      if (toolbar.myActionGroup.equals(group)) {
        return toolbar;
      }
    }
    return null;
  }

  @TestOnly
  public Presentation getPresentation(AnAction action) {
    return myPresentationFactory.getPresentation(action);
  }

  public void clearPresentationCache() {
    myPresentationFactory.reset();
  }

  public interface SecondaryGroupUpdater {
    void update(@NotNull AnActionEvent e);
  }
}
