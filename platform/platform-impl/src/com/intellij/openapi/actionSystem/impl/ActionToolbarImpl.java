// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.accessibility.AccessibilityUtils;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.internal.inspector.UiInspectorActionUtil;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.internal.statistic.collectors.fus.ui.persistence.ToolbarClicksCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.*;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutUtilKt;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ObjectUtils;
import com.intellij.util.animation.AlphaAnimated;
import com.intellij.util.animation.AlphaAnimationContext;
import com.intellij.util.concurrency.EdtScheduler;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.CancellablePromise;
import sun.swing.SwingUtilities2;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

@ApiStatus.Internal
public class ActionToolbarImpl extends JPanel implements ActionToolbar, QuickActionProvider, AlphaAnimated {
  private static final Logger LOG = Logger.getInstance(ActionToolbarImpl.class);

  private static final Set<ActionToolbarImpl> ourToolbars = new LinkedHashSet<>();


  private static final Key<String> SECONDARY_SHORTCUT = Key.create("SecondaryActions.shortcut");

  private static final String LOADING_LABEL = "LOADING_LABEL";
  private static final String SUPPRESS_ACTION_COMPONENT_WARNING = "ActionToolbarImpl.suppressCustomComponentWarning";
  private static final String SUPPRESS_TARGET_COMPONENT_WARNING = "ActionToolbarImpl.suppressTargetComponentWarning";
  public static final String DO_NOT_ADD_CUSTOMIZATION_HANDLER = "ActionToolbarImpl.suppressTargetComponentWarning";
  public static final String SUPPRESS_FAST_TRACK = "ActionToolbarImpl.suppressFastTrack";

  /**
   * Put {@code TRUE} into {@link #putClientProperty(Object, Object)} to mark that toolbar
   * should not be hidden by {@link com.intellij.ide.actions.ToggleToolbarAction}.
   */
  public static final String IMPORTANT_TOOLBAR_KEY = "ActionToolbarImpl.importantToolbar";
  public static final String USE_BASELINE_KEY = "ActionToolbarImpl.baseline";

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
    ThreadingAssertions.assertEventDispatchThread();
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
    ThreadingAssertions.assertEventDispatchThread();
    Utils.clearAllCachesAndUpdates();
    for (ActionToolbarImpl toolbar : new ArrayList<>(ourToolbars)) {
      toolbar.reset();
    }
  }

  private final Throwable myCreationTrace = new Throwable("toolbar creation trace");

  private final List<Rectangle> myComponentBounds = new ArrayList<>();
  private Supplier<? extends Dimension> myMinimumButtonSizeSupplier = Dimension::new;

  private ToolbarLayoutStrategy myLayoutStrategy;
  private int myOrientation;
  private final ActionGroup myActionGroup;
  private final @NotNull String myPlace;
  private List<? extends AnAction> myVisibleActions;
  private final PresentationFactory myPresentationFactory = createPresentationFactory();

  private final boolean myDecorateButtons;

  private final ToolbarUpdater myUpdater;
  private CancellablePromise<List<AnAction>> myLastUpdate;
  private boolean myForcedUpdateRequested = true;

  private @Nullable ActionButtonLook myCustomButtonLook;
  private @Nullable Border myActionButtonBorder;

  private final ActionButtonLook myMinimalButtonLook = ActionButtonLook.INPLACE_LOOK;

  private Rectangle myAutoPopupRec;

  private final DefaultActionGroup mySecondaryActions;
  private SecondaryGroupUpdater mySecondaryGroupUpdater;
  private boolean myForceMinimumSize;
  private boolean mySkipWindowAdjustments;
  private boolean myMinimalMode;

  private boolean myLayoutSecondaryActions;
  private ActionButton mySecondaryActionsButton;

  private int myFirstOutsideIndex = -1;
  private JBPopup myPopup;

  private JComponent myTargetComponent;
  private boolean myReservePlaceAutoPopupIcon = true;
  private boolean myShowSeparatorTitles;
  private Image myCachedImage;

  private final AlphaAnimationContext myAlphaContext = new AlphaAnimationContext(this);

  private final EventDispatcher<ActionToolbarListener> myListeners = EventDispatcher.create(ActionToolbarListener.class);

  private @NotNull Function<? super String, ? extends Component> mySeparatorCreator = (name) -> new MySeparator(name);

  private boolean myNeedCheckHoverOnLayout = false;

  public ActionToolbarImpl(@NotNull String place, @NotNull ActionGroup actionGroup, boolean horizontal) {
    this(place, actionGroup, horizontal, false, true);
  }

  public ActionToolbarImpl(@NotNull String place,
                           @NotNull ActionGroup actionGroup,
                           boolean horizontal,
                           boolean decorateButtons,
                           boolean customizable) {
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
    myUpdater = new ToolbarUpdater(this, place) {
      @Override
      protected void updateActionsImpl(boolean forced) {
        if (!ApplicationManager.getApplication().isDisposed()) {
          ActionToolbarImpl.this.updateActionsImpl(forced);
        }
      }
    };

    setOrientation(horizontal ? SwingConstants.HORIZONTAL : SwingConstants.VERTICAL);
    myLayoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY;

    mySecondaryActions = new DefaultActionGroup() {
      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        if (mySecondaryGroupUpdater != null) {
          e.getPresentation().setIcon(getTemplatePresentation().getIcon());
          mySecondaryGroupUpdater.update(e);
        }
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public boolean isDumbAware() {
        return true;
      }
    };
    mySecondaryActions.getTemplatePresentation().setIconSupplier(() -> AllIcons.General.GearPlain);
    mySecondaryActions.setPopup(true);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      addLoadingIcon();
    }

    // If the panel doesn't handle mouse event then it will be passed to its parent.
    // It means that if the panel is in sliding mode then the focus goes to the editor
    // and panel will be automatically hidden.
    enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK |
                 AWTEvent.COMPONENT_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK);
    setMiniModeInner(false);

    installPopupHandler(customizable, null, null);
    UiInspectorUtil.registerProvider(this, () -> UiInspectorActionUtil.collectActionGroupInfo(
      "Toolbar", myActionGroup, myPlace, myPresentationFactory));
  }

  protected @NotNull PresentationFactory createPresentationFactory() {
    return new ActionToolbarPresentationFactory(this);
  }

  protected void installPopupHandler(boolean customizable,
                                     @Nullable ActionGroup popupActionGroup,
                                     @Nullable String popupActionId) {
    PopupHandler popupHandler;
    if (customizable) {
      popupHandler = popupActionGroup == null
                     ? CustomizationUtil.installToolbarCustomizationHandler(this)
                     : CustomizationUtil.installToolbarCustomizationHandler(popupActionGroup, popupActionId, getComponent(), myPlace);
    }
    else {
      popupHandler = popupActionGroup != null
                     ? PopupHandler.installPopupMenu(getComponent(), popupActionGroup, myPlace)
                     : null;
    }
    if (popupHandler == null) return;
    new ComponentTreeWatcher(ArrayUtil.EMPTY_CLASS_ARRAY) {
      @Override
      protected void processComponent(Component comp) {
        if (ClientProperty.isTrue(comp, DO_NOT_ADD_CUSTOMIZATION_HANDLER)) return;
        if (ContainerUtil.exists(comp.getMouseListeners(), listener -> listener instanceof PopupHandler)) return;
        comp.addMouseListener(popupHandler);
      }

      @Override
      protected void unprocessComponent(Component component) {
      }
    }.register(this);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    for (Component component : getComponents()) {
      tweakActionComponentUI(component);
    }
    updateMinimumButtonSize();
  }

  @Override
  public int getBaseline(int width, int height) {
    if (getClientProperty(USE_BASELINE_KEY) != Boolean.TRUE || myOrientation != SwingConstants.HORIZONTAL) {
      return super.getBaseline(width, height);
    }

    int componentCount = getComponentCount();
    List<Rectangle> bounds = myLayoutStrategy.calculateBounds(this);

    int baseline = -1;
    for (int i = 0; i < componentCount; i++) {
      Component component = getComponent(i);
      Rectangle rect = bounds.get(i);
      boolean isShown = component.isVisible()
                        && rect.width != 0 && rect.height != 0
                        && rect.x < width && rect.y < height;
      if (isShown) {
        int b = component.getBaseline(rect.width, rect.height);
        if (b >= 0) {
          int baselineInParent = rect.y + b;
          if (baseline < 0) {
            baseline = baselineInParent;
          }
          else {
            if (baseline != baselineInParent) {
              return -1;
            }
          }
        }
      }
    }

    return baseline;
  }

  @Override
  public void setLayoutSecondaryActions(boolean val) {
    myLayoutSecondaryActions = val;
  }

  @Override
  public @NotNull String getPlace() {
    return myPlace;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (ComponentUtil.getParentOfType(CellRendererPane.class, this) != null) {
      return;
    }
    ourToolbars.add(this);

    updateActionsOnAdd();
  }

  protected void updateActionsOnAdd() {
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

    cancelCurrentUpdate();
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }

  @Override
  @NotNull
  public ToolbarLayoutStrategy getLayoutStrategy() {
    return myLayoutStrategy;
  }

  @Override
  public void setLayoutStrategy(@NotNull ToolbarLayoutStrategy strategy) {
    myLayoutStrategy = strategy;
    revalidate();
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  @Override
  public @NotNull ActionGroup getActionGroup() {
    return myActionGroup;
  }

  @Override
  @ApiStatus.Internal
  public @NotNull AlphaAnimationContext getAlphaContext() {
    return myAlphaContext;
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

  public void setSecondaryButtonPopupStateModifier(@NotNull ActionToolbarImpl.SecondaryGroupUpdater secondaryGroupUpdater) {
    mySecondaryGroupUpdater = secondaryGroupUpdater;
  }

  protected void fillToolBar(@NotNull List<? extends AnAction> actions, boolean layoutSecondaries) {
    boolean isLastElementSeparator = false;
    List<AnAction> rightAligned = new ArrayList<>();
    for (int i = 0; i < actions.size(); i++) {
      AnAction action = actions.get(i);
      if (isAlignmentEnabled() && action instanceof RightAlignedToolbarAction || forceRightAlignment()) {
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
          add(SEPARATOR_CONSTRAINT, mySeparatorCreator.apply(myShowSeparatorTitles ? ((Separator)action).getText() : null));
          isLastElementSeparator = true;
          continue;
        }
      }
      else {
        addActionButtonImpl(action, -1);
      }
      isLastElementSeparator = false;
    }

    if (mySecondaryActions.getChildrenCount() > 0) {
      Dimension minimumSize = isInsideNavBar() ? NAVBAR_MINIMUM_BUTTON_SIZE : DEFAULT_MINIMUM_BUTTON_SIZE;
      mySecondaryActionsButton =
        new ActionButton(mySecondaryActions, myPresentationFactory.getPresentation(mySecondaryActions), myPlace, minimumSize) {
          @Override
          protected String getShortcutText() {
            String shortcut = myPresentation.getClientProperty(SECONDARY_SHORTCUT);
            return shortcut != null ? shortcut : super.getShortcutText();
          }
        };
      mySecondaryActionsButton.setNoIconsInPopup(true);
      mySecondaryActionsButton.putClientProperty(ActionToolbar.SECONDARY_ACTION_PROPERTY, Boolean.TRUE);
      add(SECONDARY_ACTION_CONSTRAINT, mySecondaryActionsButton);
    }

    for (AnAction action : rightAligned) {
      JComponent button = action instanceof CustomComponentAction ? getCustomComponent(action) : createToolbarButton(action);
      if (!isInsideNavBar()) {
        button.putClientProperty(ToolbarLayoutUtilKt.RIGHT_ALIGN_KEY, Boolean.TRUE);
      }
      add(button);
    }
  }

  protected boolean isAlignmentEnabled() {
    return true;
  }

  protected boolean forceRightAlignment() {
    return false;
  }

  private void addActionButtonImpl(@NotNull AnAction action, int index) {
    if (action instanceof CustomComponentAction) {
      add(getCustomComponent(action), CUSTOM_COMPONENT_CONSTRAINT, index);
    }
    else {
      if (action instanceof ActionWithDelegate<?> wrapper &&
          wrapper.getDelegate() instanceof CustomComponentAction) {
        LOG.error("`CustomComponentAction` component is ignored due to wrapping: " +
                  Utils.operationName(action, null, myPlace));
      }
      add(createToolbarButton(action), ACTION_BUTTON_CONSTRAINT, index);
    }
  }

  protected final @NotNull JComponent getCustomComponent(@NotNull AnAction action) {
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
      customComponent.putClientProperty(CustomComponentAction.ACTION_KEY, action);
      ((CustomComponentAction)action).updateCustomComponent(customComponent, presentation);
    }

    AbstractButton clickable = UIUtil.findComponentOfType(customComponent, AbstractButton.class);
    if (clickable != null) {
      final class ToolbarClicksCollectorListener extends MouseAdapter {
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
    applyToolbarLook(getActionButtonLook(), presentation, result);
    return result;
  }

  private void tweakActionComponentUI(@NotNull Component actionComponent) {
    if (ActionPlaces.EDITOR_TOOLBAR.equals(myPlace)) {
      // tweak font & color for editor toolbar to match editor tabs style
      actionComponent.setFont(RelativeFont.NORMAL.fromResource("Toolbar.Component.fontSizeOffset", -2)
                                .derive(StartupUiUtil.getLabelFont()));
      actionComponent.setForeground(ColorUtil.dimmer(JBColor.BLACK));
    }
  }

  private @NotNull Border getActionButtonBorder() {
    return myActionButtonBorder != null ? myActionButtonBorder : new ActionButtonBorder(() -> 2, () -> 1);
  }

  protected @NotNull ActionButton createToolbarButton(@NotNull AnAction action,
                                                      @Nullable ActionButtonLook look,
                                                      @NotNull String place,
                                                      @NotNull Presentation presentation,
                                                      @NotNull Dimension minimumSize) {
    return createToolbarButton(action, look, place, presentation, () -> minimumSize);
  }

  protected @NotNull ActionButton createToolbarButton(@NotNull AnAction action,
                                                      @Nullable ActionButtonLook look,
                                                      @NotNull String place,
                                                      @NotNull Presentation presentation,
                                                      Supplier<? extends @NotNull Dimension> minimumSize) {
    ActionButton actionButton;
    if (Boolean.TRUE.equals(presentation.getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR))) {
      actionButton = createTextButton(action, place, presentation, minimumSize);
    }
    else {
      actionButton = createIconButton(action, place, presentation, minimumSize);
    }
    applyToolbarLook(look, presentation, actionButton);
    return actionButton;
  }

  protected @NotNull ActionButton createIconButton(@NotNull AnAction action,
                                                   @NotNull String place,
                                                   @NotNull Presentation presentation,
                                                   Supplier<? extends @NotNull Dimension> minimumSize) {
    return new ActionButton(action, presentation, place, minimumSize);
  }

  protected @NotNull ActionButtonWithText createTextButton(@NotNull AnAction action,
                                                           @NotNull String place,
                                                           @NotNull Presentation presentation,
                                                           Supplier<? extends @NotNull Dimension> minimumSize) {
    int mnemonic = action.getTemplatePresentation().getMnemonic();
    ActionButtonWithText buttonWithText = new ActionButtonWithText(action, presentation, place, minimumSize);

    if (mnemonic != KeyEvent.VK_UNDEFINED) {
      buttonWithText.registerKeyboardAction(__ -> buttonWithText.click(),
                                            KeyStroke.getKeyStroke(mnemonic, InputEvent.ALT_DOWN_MASK), WHEN_IN_FOCUSED_WINDOW);
    }
    return buttonWithText;
  }

  protected void applyToolbarLook(@Nullable ActionButtonLook look, @NotNull Presentation presentation, @NotNull JComponent component) {
    if (component instanceof ActionButton) {
      ((ActionButton)component).setLook(look);
      component.setBorder(getActionButtonBorder());
    }
    tweakActionComponentUI(component);
    ToolbarActionTracker.followToolbarComponent(presentation, component, getComponent());
  }

  protected final @NotNull ActionButton createToolbarButton(@NotNull AnAction action) {
    return createToolbarButton(
      action,
      getActionButtonLook(),
      myPlace, myPresentationFactory.getPresentation(action),
      myMinimumButtonSizeSupplier);
  }

  protected @Nullable ActionButtonLook getActionButtonLook() {
    if (myCustomButtonLook != null) {
      return myCustomButtonLook;
    }

    return myMinimalMode ? myMinimalButtonLook : myDecorateButtons ? new ActionButtonLook() {
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
    } : null;
  }

  @Override
  public void doLayout() {
    if (!isValid()) {
      calculateBounds();
      calculateAutoPopupRect();
    }
    final int componentCount = getComponentCount();
    LOG.assertTrue(componentCount <= myComponentBounds.size());
    for (int i = componentCount - 1; i >= 0; i--) {
      final Component component = getComponent(i);
      component.setBounds(myComponentBounds.get(i));
    }

    if (myNeedCheckHoverOnLayout) {
      Point location = MouseInfo.getPointerInfo().getLocation();
      SwingUtilities.convertPointFromScreen(location, this);
      for (int i = componentCount - 1; i >= 0; i--) {
        final Component component = getComponent(i);
        if (component instanceof ActionButton actionButton && component.getBounds().contains(location)) {
          actionButton.myRollover = true;
        }
      }
    }
  }

  @Override
  public void validate() {
    if (!isValid()) {
      calculateBounds();
      calculateAutoPopupRect();
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

  /**
   * The visibility of this method has been changed to private.
   * It is no longer necessary to override this method to implement a custom toolbar layout.
   * Instead, consider implementing your own {@link ToolbarLayoutStrategy}.
   */
  private void calculateBounds() {
    myComponentBounds.clear();
    myComponentBounds.addAll(myLayoutStrategy.calculateBounds(this));
  }

  private void calculateAutoPopupRect() {
    int firstHidden = -1;
    int edge = 0;
    for (int i = 0; i < myComponentBounds.size(); i++) {
      Rectangle r = myComponentBounds.get(i);
      if (r.x == Integer.MAX_VALUE || r.y == Integer.MAX_VALUE) {
        firstHidden = i;
        break;
      }
      edge = (int)(myOrientation == SwingConstants.HORIZONTAL ? r.getMaxX() : r.getMaxY());
    }

    if (firstHidden >= 0) {
      Dimension size = getSize();
      Insets insets = getInsets();
      myFirstOutsideIndex = firstHidden;
      myAutoPopupRec = myOrientation == SwingConstants.HORIZONTAL
                       ? new Rectangle(edge, insets.top, size.width - edge - insets.right, size.height - insets.top - insets.bottom)
                       : new Rectangle(insets.left, edge, size.width - insets.left - insets.right, size.height - edge - insets.bottom);
    }
    else {
      myAutoPopupRec = null;
      myFirstOutsideIndex = -1;
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
    return myLayoutStrategy.calcPreferredSize(this);
  }

  /**
   * Forces the minimum size of the toolbar to show all buttons, When set to {@code true}. By default ({@code false}) the
   * toolbar will shrink further and show the auto popup chevron button.
   */
  public void setForceMinimumSize(boolean force) {
    myForceMinimumSize = force;
  }

  public final void setCustomButtonLook(@Nullable ActionButtonLook customButtonLook) {
    myCustomButtonLook = customButtonLook;
  }

  public final void setActionButtonBorder(@Nullable Border actionButtonBorder) {
    myActionButtonBorder = actionButtonBorder;
  }

  @ApiStatus.Internal
  public final void setActionButtonBorder(
    @NotNull Supplier<Integer> directionalGapUnscaledSupplier,
    @NotNull Supplier<Integer> orthogonalGapUnscaledSupplier
  ) {
    setActionButtonBorder(new ActionButtonBorder(directionalGapUnscaledSupplier, orthogonalGapUnscaledSupplier));
  }

  public final void setActionButtonBorder(int directionalGap, int orthogonalGap) {
    setActionButtonBorder(new ActionButtonBorder(() -> directionalGap, () ->orthogonalGap));
  }

  public final void setSeparatorCreator(@NotNull Function<? super String, ? extends Component> separatorCreator) {
    mySeparatorCreator = separatorCreator;
  }

  /**
   * By default minimum size is to show chevron only.
   * If this option is {@code true} toolbar shows at least one (the first) component plus chevron (if need)
   *
   * @deprecated method is deprecated and going to be removed in future releases. Please use {@link ActionToolbar#setLayoutStrategy(ToolbarLayoutStrategy)} )}
   * method to set necessary layout for toolbar
   */
  @Deprecated(since = "2024.1", forRemoval = true)
  public void setForceShowFirstComponent(boolean showFirstComponent) {
    setLayoutStrategy(ToolbarLayoutUtilKt.autoLayoutStrategy(showFirstComponent));
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

    return myLayoutStrategy.calcMinimumSize(this);
  }

  protected @NotNull Color getSeparatorColor() {
    return JBUI.CurrentTheme.Toolbar.SEPARATOR_COLOR;
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

  public static boolean isSeparator(Component component) {
    return component instanceof MySeparator;
  }

  private final class MySeparator extends JComponent {
    private final String myText;

    private MySeparator(String text) {
      myText = text;
      setFont(JBUI.Fonts.toolbarSmallComboBoxFont());
      UISettings.setupComponentAntialiasing(this);
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

          int textWidth = SwingUtilities2.stringWidth(this, fontMetrics, myText);
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
          g.setColor(JBColor.foreground());
          SwingUtilities2.drawString(this, g, myText, gap * 2 + center + gap, top + fontMetrics.getAscent());
        }
      }
      else {
        LinePainter2D.paint((Graphics2D)g, gap, center, ActionToolbarImpl.this.getWidth() - gap * 2 - offset, center);
      }
    }
  }

  @Override
  public void setMinimumButtonSize(@NotNull Dimension size) {
    setMinimumButtonSize(() -> size);
  }

  public void setMinimumButtonSize(@NotNull Supplier<? extends @NotNull Dimension> size) {
    myMinimumButtonSizeSupplier = size;
    updateMinimumButtonSize();
    revalidate();
  }

  @Override
  public @NotNull Dimension getMinimumButtonSize() {
    return myMinimumButtonSizeSupplier.get();
  }

  public @NotNull Supplier<? extends @NotNull Dimension> getMinimumButtonSizeSupplier() {
    return myMinimumButtonSizeSupplier;
  }

  private void updateMinimumButtonSize() {
    if (myMinimumButtonSizeSupplier == null) {
      return; // called from the superclass constructor through updateUI()
    }
    JBDimension minimumButtonSize = JBDimension.create(myMinimumButtonSizeSupplier.get(), true);
    for (int i = getComponentCount() - 1; i >= 0; i--) {
      final Component component = getComponent(i);
      if (component instanceof ActionButton button) {
        button.setMinimumButtonSize(myMinimumButtonSizeSupplier);
      }
      else if (component instanceof JLabel && LOADING_LABEL.equals(component.getName())) {
        Dimension dimension = new Dimension();
        dimension.width = Math.max(minimumButtonSize.width, ((JLabel)component).getIcon().getIconWidth());
        dimension.height = Math.max(minimumButtonSize.height, ((JLabel)component).getIcon().getIconHeight());
        JBInsets.addTo(dimension, ((JLabel)component).getInsets());
        component.setPreferredSize(dimension);
      }
    }
  }

  @Override
  public void setOrientation(@MagicConstant(intValues = {SwingConstants.HORIZONTAL, SwingConstants.VERTICAL}) int orientation) {
    if (SwingConstants.HORIZONTAL != orientation && SwingConstants.VERTICAL != orientation) {
      throw new IllegalArgumentException("wrong orientation: " + orientation);
    }
    myOrientation = orientation;
  }

  @Override
  @MagicConstant(intValues = {SwingConstants.HORIZONTAL, SwingConstants.VERTICAL})
  public int getOrientation() {
    return myOrientation;
  }

  @Deprecated
  @Override
  public void updateActionsImmediately() {
    updateActionsImmediately(false);
  }

  @Override
  @RequiresEdt
  public @NotNull Future<?> updateActionsAsync() {
    updateActionsImmediately(false);
    CancellablePromise<List<AnAction>> update = myLastUpdate;
    return update == null ? CompletableFuture.completedFuture(null) : update;
  }


  @ApiStatus.Internal
  @RequiresEdt
  protected void updateActionsImmediately(boolean includeInvisible) {
    boolean isTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (getParent() == null && myTargetComponent == null && !isTestMode && !includeInvisible) {
      LOG.warn(new Throwable("'" + myPlace + "' toolbar manual update is ignored. " +
                             "Newly created toolbars are updated automatically on `addNotify`.", myCreationTrace));
      return;
    }
    updateActionsWithoutLoadingIcon(includeInvisible);
  }

  @ApiStatus.Internal
  @RequiresEdt
  protected void updateActionsWithoutLoadingIcon(boolean includeInvisible) {
    myUpdater.updateActions(true, false, includeInvisible);
  }

  private void updateActionsImpl(boolean forced) {
    if (forced) myForcedUpdateRequested = true;
    boolean isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();

    DataContext dataContext = Utils.createAsyncDataContext(getDataContext());

    cancelCurrentUpdate();

    boolean firstTimeFastTrack = !hasVisibleActions() &&
                                 getComponentCount() == 1 &&
                                 getClientProperty(SUPPRESS_FAST_TRACK) == null;
    if (firstTimeFastTrack) {
      putClientProperty(SUPPRESS_FAST_TRACK, true);
    }
    CancellablePromise<List<AnAction>> promise = myLastUpdate = Utils.expandActionGroupAsync(
      myActionGroup, myPresentationFactory, dataContext, myPlace, true, firstTimeFastTrack || isUnitTestMode);
    if (promise.isSucceeded()) {
      myLastUpdate = null;
      List<AnAction> fastActions;
      try {
        fastActions = promise.get(0, TimeUnit.MILLISECONDS);
      }
      catch (Throwable th) {
        throw new AssertionError(th);
      }
      actionsUpdated(true, fastActions);
    }
    else {
      boolean forcedActual = forced || myForcedUpdateRequested;
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
    if (mySecondaryActionsButton != null) {
      mySecondaryActionsButton.update();
      mySecondaryActionsButton.repaint();
    }
  }

  private void addLoadingIcon() {
    JLabel label = new JLabel();
    label.setName(LOADING_LABEL);
    label.setBorder(getActionButtonBorder());
    if (this instanceof PopupToolbar) {
      label.setIcon(AnimatedIcon.Default.INSTANCE);
    }
    else {
      boolean suppressLoading = ActionPlaces.MAIN_TOOLBAR.equals(myPlace) ||
                                ActionPlaces.NAVIGATION_BAR_TOOLBAR.equals(myPlace) ||
                                ActionPlaces.TOOLWINDOW_TITLE.equals(myPlace) ||
                                ActionPlaces.WELCOME_SCREEN.equals(myPlace);
      if (suppressLoading) {
        label.setIcon(EmptyIcon.create(16, 16));
      }
      else {
        AnimatedIcon icon = AnimatedIcon.Default.INSTANCE;
        label.setIcon(EmptyIcon.create(icon.getIconWidth(), icon.getIconHeight()));
        EdtScheduler.getInstance().schedule(Registry.intValue("actionSystem.toolbar.progress.icon.delay", 500), () -> {
          label.setIcon(icon);
        });
      }
    }
    myForcedUpdateRequested = true;
    add(label);
    updateMinimumButtonSize();
  }

  protected void actionsUpdated(boolean forced, @NotNull List<? extends AnAction> newVisibleActions) {
    myListeners.getMulticaster().actionsUpdated();
    if (!forced && !myPresentationFactory.isNeedRebuild()) {
      if (newVisibleActions.equals(myVisibleActions)) return;
      if (replaceButtonsForNewActionInstances(newVisibleActions)) return;
    }
    myForcedUpdateRequested = false;
    myCachedImage = null;
    boolean fullReset = newVisibleActions.isEmpty() || myVisibleActions.isEmpty();
    myVisibleActions = newVisibleActions;

    boolean skipSizeAdjustments = mySkipWindowAdjustments || skipSizeAdjustments();
    Component compForSize = guessBestParentForSizeAdjustment();
    Dimension oldSize = skipSizeAdjustments ? null : compForSize.getPreferredSize();

    removeAll();
    mySecondaryActions.removeAll();
    mySecondaryActionsButton = null;
    fillToolBar(myVisibleActions, myLayoutSecondaryActions && myOrientation == SwingConstants.HORIZONTAL);
    myPresentationFactory.resetNeedRebuild();

    if (!skipSizeAdjustments) {
      Dimension availSize = compForSize.getSize();
      Dimension newSize = compForSize.getPreferredSize();
      adjustContainerWindowSize(fullReset, availSize, oldSize, newSize);
    }

    compForSize.revalidate();
    compForSize.repaint();
  }

  private boolean replaceButtonsForNewActionInstances(@NotNull List<? extends AnAction> newVisibleActions) {
    if (newVisibleActions.size() != myVisibleActions.size()) return false;
    Component[] components = getComponents();
    ArrayList<Pair<Integer, AnAction>> pairs = new ArrayList<>();
    for (int count = 0, size = myVisibleActions.size(), buttonIndex = 0; count < size; count++) {
      AnAction prev = myVisibleActions.get(count);
      AnAction next = newVisibleActions.get(count);
      if (next == prev) continue;
      if (next.getClass() != prev.getClass()) return false;
      Pair<Integer, AnAction> pair = null;
      for (; buttonIndex < components.length && pair == null; buttonIndex++) {
        Component component = components[buttonIndex];
        AnAction action =
          component instanceof ActionButton o ? o.getAction() :
          prev instanceof CustomComponentAction ? ClientProperty.get(component, CustomComponentAction.ACTION_KEY) : null;
        if (action == prev) {
          pair = Pair.create(buttonIndex, next);
        }
      }
      if (pair == null) return false;
      pairs.add(pair);
    }
    if (pairs.size() == newVisibleActions.size()) return false;
    myVisibleActions = newVisibleActions;
    for (Pair<Integer, AnAction> pair : pairs) {
      int index = pair.first;
      remove(index);
      addActionButtonImpl(pair.second, index);
      Component button = getComponent(index);
      button.setBounds(components[index].getBounds());
      button.validate();
    }
    return true;
  }

  // don't call getPreferredSize for "best parent" if it isn't popup or lightweight hint
  private boolean skipSizeAdjustments() {
    return PopupUtil.getPopupContainerFor(this) == null && getParentLightweightHintComponent(this) == null;
  }

  private void adjustContainerWindowSize(boolean fullReset,
                                         @NotNull Dimension availSize,
                                         @NotNull Dimension oldSize,
                                         @NotNull Dimension newSize) {
    Dimension delta = new Dimension(newSize.width - oldSize.width, newSize.height - oldSize.height);
    if (!fullReset) {
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
    for (Component cur = result.getParent(); cur != null; cur = cur.getParent()) {
      if (cur instanceof JRootPane) break;
      if (cur instanceof JLayeredPane && cur.getParent() instanceof JRootPane) break;
      Dimension size = cur.getSize();
      if (myOrientation == SwingConstants.HORIZONTAL && size.height - availSize.height > 8 ||
          myOrientation == SwingConstants.VERTICAL && size.width - availSize.width > 8) {
        if (availSize.width == 0 && availSize.height == 0) result = cur;
        break;
      }
      result = cur;
      availSize = cur.getSize();
    }
    return result;
  }

  private static @Nullable JComponent getParentLightweightHintComponent(@Nullable JComponent component) {
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
    Component target = myTargetComponent != null ? myTargetComponent : IJSwingUtilities.getFocusedComponentInWindowOrSelf(this);
    return DataManager.getInstance().getDataContext(target);
  }

  @Override
  protected void processMouseMotionEvent(final MouseEvent e) {
    super.processMouseMotionEvent(e);

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
    popupToolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);

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
      .setFocusable(false) // do not steal focus on showing, and don't close on IDE frame gaining focus (see AbstractPopup.isCancelNeeded)
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
        Window window = ComponentUtil.getWindow(popupToolbar);
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
      super(place, actionGroup, horizontal, false, true);
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
  public boolean isReservePlaceAutoPopupIcon() {
    return myReservePlaceAutoPopupIcon && !isInsideNavBar();
  }

  @Override
  public void setSecondaryActionsTooltip(@NotNull @NlsContexts.Tooltip String secondaryActionsTooltip) {
    mySecondaryActions.getTemplatePresentation().setText(secondaryActionsTooltip);
  }

  @Override
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

  @Override
  public @NotNull List<AnAction> getActions(boolean originalProvider) {
    return getActions();
  }

  @Override
  public @NotNull List<AnAction> getActions() {
    List<AnAction> result = new ArrayList<>();
    List<AnAction> secondary = new ArrayList<>();
    for (AnAction each : myVisibleActions) {
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
      setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);
      setBorder(JBUI.Borders.empty());
      setOpaque(false);
    }
    else {
      Insets i = myOrientation == SwingConstants.VERTICAL ? JBUI.CurrentTheme.Toolbar.verticalToolbarInsets()
                                                          : JBUI.CurrentTheme.Toolbar.horizontalToolbarInsets();
      if (i != null) {
        setBorder(JBUI.Borders.empty(i));
      }
      else {
        setBorder(JBUI.Borders.empty(2));
      }

      setMinimumButtonSize(myDecorateButtons ? JBUI.size(30, 20) : DEFAULT_MINIMUM_BUTTON_SIZE);
      setOpaque(true);
      setLayoutStrategy(ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY);
      setLayoutSecondaryActions(true);
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

  @ApiStatus.Internal
  @NotNull PresentationFactory getPresentationFactory() {
    return myPresentationFactory;
  }

  @TestOnly
  public Presentation getPresentation(AnAction action) {
    return myPresentationFactory.getPresentation(action);
  }

  /**
   * Clear internal caches.
   * <p>
   * This method can be called after updating {@link ActionToolbarImpl#myActionGroup}
   * to make sure toolbar does not reference old {@link AnAction} instances.
   */
  public void reset() {
    cancelCurrentUpdate();

    boolean isTestMode = ApplicationManager.getApplication().isUnitTestMode();
    Image image = !isTestMode && isShowing() ? paintToImage(this) : null;
    if (image != null) {
      myCachedImage = image;
    }

    myPresentationFactory.reset();
    myVisibleActions.clear();
    removeAll();

    if (!isTestMode && image == null) {
      addLoadingIcon();
    }
  }

  private void cancelCurrentUpdate() {
    CancellablePromise<List<AnAction>> lastUpdate = myLastUpdate;
    myLastUpdate = null;
    if (lastUpdate != null) lastUpdate.cancel();
  }

  public interface SecondaryGroupUpdater {
    void update(@NotNull AnActionEvent e);
  }

  private final class ActionButtonBorder extends JBEmptyBorder {
    ActionButtonBorder(
      @NotNull Supplier<Integer> directionalGapUnscaledSupplier,
      @NotNull Supplier<Integer> orthogonalGapUnscaledSupplier
    ) {
      this(insetsSupplier(directionalGapUnscaledSupplier, orthogonalGapUnscaledSupplier));
    }

    private ActionButtonBorder(@NotNull Supplier<@NotNull Insets> supplier) {
      super(JBInsets.create(supplier, supplier.get()));
    }
  }

  private @NotNull Supplier<@NotNull Insets> insetsSupplier(
    @NotNull Supplier<Integer> directionalGapUnscaledSupplier,
    @NotNull Supplier<Integer> orthogonalGapUnscaledSupplier
  ) {
    return () -> {
      var directionalGap = directionalGapUnscaledSupplier.get();
      var orthogonalGap = orthogonalGapUnscaledSupplier.get();
      //noinspection UseDPIAwareInsets
      return myOrientation == SwingConstants.VERTICAL
             ? new Insets(directionalGap, orthogonalGap, directionalGap, orthogonalGap)
             :  new Insets(orthogonalGap, directionalGap, orthogonalGap, directionalGap);
    };
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) accessibleContext = new AccessibleActionToolbar();

    // We don't need additional grouping for ActionToolbar in the new frame header or if it's empty
    if (!myVisibleActions.isEmpty() &&
        !(ExperimentalUI.isNewUI() && getPlace().equals(ActionPlaces.MAIN_TOOLBAR))
        && !getPlace().equals(ActionPlaces.NEW_UI_RUN_TOOLBAR)) {
      accessibleContext.setAccessibleName(UIBundle.message("action.toolbar.accessible.group.name"));
    }
    else {
      accessibleContext.setAccessibleName("");
    }

    return accessibleContext;
  }

  @ApiStatus.Internal
  public void setNeedCheckHoverOnLayout(boolean needCheckHoverOnLayout) {
    myNeedCheckHoverOnLayout = needCheckHoverOnLayout;
  }

  private final class AccessibleActionToolbar extends AccessibleJPanel {
    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibilityUtils.GROUPED_ELEMENTS;
    }
  }
}
