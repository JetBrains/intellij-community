// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.codeWithMe.ClientId
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings.Companion.setupComponentAntialiasing
import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.internal.inspector.UiInspectorActionUtil.collectActionGroupInfo
import com.intellij.internal.inspector.UiInspectorUtil
import com.intellij.internal.statistic.collectors.fus.ui.persistence.ToolbarClicksCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.ActionManagerEx.Companion.getInstanceEx
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.Utils.clearAllCachesAndUpdates
import com.intellij.openapi.actionSystem.impl.Utils.operationName
import com.intellij.openapi.actionSystem.toolbarLayout.RIGHT_ALIGN_KEY
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.actionSystem.toolbarLayout.autoLayoutStrategy
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.ui.*
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.ToolbarActionTracker.Companion.followToolbarComponent
import com.intellij.ui.awt.DevicePoint
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.scale.JBUIScale.addUserScaleChangeListener
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.util.ArrayUtil
import com.intellij.util.EventDispatcher
import com.intellij.util.animation.AlphaAnimated
import com.intellij.util.animation.AlphaAnimationContext
import com.intellij.util.concurrency.EdtScheduler
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import sun.swing.SwingUtilities2
import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.function.Function
import java.util.function.Supplier
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.border.Border
import kotlin.math.max

private val LOG = logger<ActionToolbarImpl>()
private val ourToolbars = LinkedHashSet<ActionToolbarImpl>()

private val SECONDARY_SHORTCUT = Key.create<String>("SecondaryActions.shortcut")

private const val LOADING_LABEL = "LOADING_LABEL"
private const val SUPPRESS_ACTION_COMPONENT_WARNING = "ActionToolbarImpl.suppressCustomComponentWarning"
private const val SUPPRESS_TARGET_COMPONENT_WARNING = "ActionToolbarImpl.suppressTargetComponentWarning"

@ApiStatus.Internal
open class ActionToolbarImpl @JvmOverloads constructor(
  place: String,
  actionGroup: ActionGroup,
  horizontal: Boolean,
  decorateButtons: Boolean = false,
  customizable: Boolean = true,
) : JPanel(null), ActionToolbar, QuickActionProvider, AlphaAnimated {
  private val myCreationTrace = Throwable("toolbar creation trace")

  private val myComponentBounds = ArrayList<Rectangle>()
  private var myMinimumButtonSizeSupplier: Supplier<out Dimension> = Supplier { Dimension() }

  private var myLayoutStrategy: ToolbarLayoutStrategy
  private var myOrientation = 0
  private val myActionGroup: ActionGroup
  private val myPlace: String
  private var myVisibleActions: List<AnAction>

  val presentationFactory: PresentationFactory = createPresentationFactory()

  private val myDecorateButtons: Boolean

  private val myUpdater: ToolbarUpdater
  private var myLastUpdate: Job? = null
  private var myForcedUpdateRequested = true
  private var myUpdateOnFirstShowJob: Job? = null

  private var myUpdatesWithNewButtons = 0
  private var myLastNewButtonActionClass: String? = null

  private var myCustomButtonLook: ActionButtonLook? = null
  private var myActionButtonBorder: Border? = null

  private val myMinimalButtonLook: ActionButtonLook = ActionButtonLook.INPLACE_LOOK

  private var myAutoPopupRec: Rectangle? = null

  private val mySecondaryActions: DefaultActionGroup
  private var mySecondaryGroupUpdater: SecondaryGroupUpdater? = null
  private var myForceMinimumSize = false
  private var mySkipWindowAdjustments = false
  private var myMinimalMode = false

  private var myLayoutSecondaryActions = false
  private var mySecondaryActionsButton: ActionButton? = null

  private var myFirstOutsideIndex = -1
  private var myPopup: JBPopup? = null

  private var myTargetComponent: JComponent? = null
  private var myReservePlaceAutoPopupIcon = true
  private var myShowSeparatorTitles = false
  private var myCachedImage: Image? = null

  override val alphaContext: AlphaAnimationContext = AlphaAnimationContext(this)

  private val myListeners = EventDispatcher.create<ActionToolbarListener>(ActionToolbarListener::class.java)

  private var mySeparatorCreator: Function<in String?, out Component> = Function { MySeparator(it) }

  private var myNeedCheckHoverOnLayout = false

  init {
    if (ActionPlaces.UNKNOWN == place || place.isEmpty()) {
      LOG.warn(Throwable("Please do not use ActionPlaces.UNKNOWN or the empty place. " +
                         "Any string unique enough to deduce the toolbar location will do.", myCreationTrace))
    }

    alphaContext.animator.setVisibleImmediately(true)
    myPlace = place
    myActionGroup = actionGroup
    myVisibleActions = ArrayList<AnAction>()
    myDecorateButtons = decorateButtons
    myUpdater = object : ToolbarUpdater(this, place) {
      override fun updateActionsImpl(forced: Boolean) {
        if (!ApplicationManager.getApplication().isDisposed()) {
          this@ActionToolbarImpl.updateActionsImpl(forced)
        }
      }
    }

    setOrientation(if (horizontal) SwingConstants.HORIZONTAL else SwingConstants.VERTICAL)
    myLayoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY

    mySecondaryActions = object : DefaultActionGroup() {
      override fun update(e: AnActionEvent) {
        super.update(e)
        mySecondaryGroupUpdater?.run {
          e.presentation.setIcon(templatePresentation.getIcon())
          update(e)
        }
      }
      override fun getActionUpdateThread() = ActionUpdateThread.EDT
      override fun isDumbAware() = true
    }
    mySecondaryActions.templatePresentation.setIconSupplier { AllIcons.General.GearPlain }
    mySecondaryActions.isPopup = true

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      addLoadingIcon()
    }

    // If the panel doesn't handle mouse event, then it will be passed to its parent.
    // It means that if the panel is in sliding mode then the focus goes to the editor
    // and panel will be automatically hidden.
    enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.MOUSE_EVENT_MASK or
                   AWTEvent.COMPONENT_EVENT_MASK or AWTEvent.CONTAINER_EVENT_MASK)
    setMiniModeInner(false)

    installPopupHandler(customizable, null, null)
    UiInspectorUtil.registerProvider(this) {
      collectActionGroupInfo("Toolbar", myActionGroup, myPlace, presentationFactory)
    }
  }

  protected open fun createPresentationFactory(): PresentationFactory {
    return ActionToolbarPresentationFactory(this)
  }

  protected fun installPopupHandler(
    customizable: Boolean,
    popupActionGroup: ActionGroup?,
    popupActionId: String?,
  ) {
    val popupHandler = when {
      customizable && popupActionGroup == null ->
        CustomizationUtil.installToolbarCustomizationHandler(this)
      customizable && popupActionGroup != null ->
        CustomizationUtil.installToolbarCustomizationHandler(popupActionGroup, popupActionId, component, myPlace)
      popupActionGroup != null -> PopupHandler.installPopupMenu(component, popupActionGroup, myPlace)
      else -> return
    }
    object : ComponentTreeWatcher(ArrayUtil.EMPTY_CLASS_ARRAY) {
      override fun processComponent(comp: Component) {
        if (ClientProperty.isTrue(comp, DO_NOT_ADD_CUSTOMIZATION_HANDLER)) return
        if (comp.mouseListeners.find { it is PopupHandler } != null) return
        comp.addMouseListener(popupHandler)
      }
      override fun unprocessComponent(component: Component?) = Unit
    }.register(this)
  }

  override fun updateUI() {
    super.updateUI()
    for (component in components) {
      tweakActionComponentUI(component)
    }
    updateMinimumButtonSize()
    if (parent != null) { // check to avoid the warning inside
      updateActionsAsync() // update presentations, as something might have changed (e.g. Compact Mode on/off making icons smaller/larger)
    }
  }

  override fun getBaseline(width: Int, height: Int): Int {
    if (getClientProperty(USE_BASELINE_KEY) != true || myOrientation != SwingConstants.HORIZONTAL) {
      return super.getBaseline(width, height)
    }

    val componentCount = getComponentCount()
    val bounds = myLayoutStrategy.calculateBounds(this)

    var baseline = -1
    for (i in 0..<componentCount) {
      val component = getComponent(i)
      val rect = bounds[i]
      val isShown = component.isVisible
                    && rect.width != 0 && rect.height != 0 && rect.x < width && rect.y < height
      if (isShown) {
        val b = component.getBaseline(rect.width, rect.height)
        if (b >= 0) {
          val baselineInParent = rect.y + b
          if (baseline < 0) {
            baseline = baselineInParent
          }
          else {
            if (baseline != baselineInParent) {
              return -1
            }
          }
        }
      }
    }

    return baseline
  }

  override fun setLayoutSecondaryActions(value: Boolean) {
    myLayoutSecondaryActions = value
  }

  override fun getPlace(): String = myPlace

  override fun addNotify() {
    super.addNotify()
    if (ComponentUtil.getParentOfType(CellRendererPane::class.java, this) != null) {
      return
    }
    ourToolbars.add(this)
    updateActionsOnAdd()
  }

  protected fun updateActionsOnAdd() {
    if (isShowing()) {
      @Suppress("DEPRECATION")
      updateActionsImmediately()
    }
    else {
      if (myUpdateOnFirstShowJob != null) {
        return
      }

      launchOnceOnShow("ActionToolbarImpl.updateActionsOnAdd") {
        withContext(Dispatchers.UiWithModelAccess) {
          // a first update really
          if (myForcedUpdateRequested && myLastUpdate == null) {
            @Suppress("DEPRECATION")
            (updateActionsImmediately())
          }
        }
      }.apply {
        myUpdateOnFirstShowJob = this
        invokeOnCompletion {
          myUpdateOnFirstShowJob = null
        }
      }
    }
  }

  private fun isInsideNavBar(): Boolean = ActionPlaces.NAVIGATION_BAR_TOOLBAR == myPlace

  override fun removeNotify() {
    super.removeNotify()
    if (ComponentUtil.getParentOfType(CellRendererPane::class.java, this) != null) return
    ourToolbars.remove(this)

    myPopup?.run {
      cancel()
      myPopup = null
    }

    cancelCurrentUpdate()
  }

  override fun getComponent(): JComponent = this

  override fun getLayoutStrategy(): ToolbarLayoutStrategy = myLayoutStrategy

  override fun setLayoutStrategy(strategy: ToolbarLayoutStrategy) {
    myLayoutStrategy = strategy
    revalidate()
  }

  override fun getComponentGraphics(graphics: Graphics?): Graphics {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))
  }

  override fun getActionGroup(): ActionGroup = myActionGroup

  override fun paint(g: Graphics) {
    alphaContext.paint(g) { super.paint(g) }
  }

  override fun paintComponent(g: Graphics) {
    myCachedImage?.run {
      UIUtil.drawImage(g, this, 0, 0, null)
      return
    }
    super.paintComponent(g)

    myAutoPopupRec?.run {
      if (myOrientation == SwingConstants.HORIZONTAL) {
        val dy = height / 2 - AllIcons.Ide.Link.iconHeight / 2
        AllIcons.Ide.Link.paintIcon(this@ActionToolbarImpl, g, maxX.toInt() - AllIcons.Ide.Link.iconWidth - 1, y + dy)
      }
      else {
        val dx = width / 2 - AllIcons.Ide.Link.iconWidth / 2
        AllIcons.Ide.Link.paintIcon(this@ActionToolbarImpl, g, x + dx, maxY.toInt() - AllIcons.Ide.Link.iconWidth - 1)
      }
    }
  }

  fun setSecondaryButtonPopupStateModifier(secondaryGroupUpdater: SecondaryGroupUpdater) {
    mySecondaryGroupUpdater = secondaryGroupUpdater
  }

  protected open fun fillToolBar(actions: List<AnAction>, layoutSecondaries: Boolean) {
    var isLastElementSeparator = false
    val rightAligned: MutableList<AnAction> = ArrayList()
    for (i in actions.indices) {
      val action: AnAction = actions[i]
      if (isAlignmentEnabled() && action is RightAlignedToolbarAction || forceRightAlignment()) {
        rightAligned.add(action)
        continue
      }

      if (layoutSecondaries) {
        if (isSecondaryAction(action, i)) {
          mySecondaryActions.add(action)
          continue
        }
      }

      if (action is Separator) {
        if (isLastElementSeparator) continue
        if (i > 0 && i < actions.size - 1) {
          add(ActionToolbar.SEPARATOR_CONSTRAINT, mySeparatorCreator.apply(
            if (myShowSeparatorTitles) action.text else null))
          isLastElementSeparator = true
          continue
        }
      }
      else {
        addActionButtonImpl(action, -1)
      }
      isLastElementSeparator = false
    }

    if (mySecondaryActions.childrenCount > 0) {
      val minimumSize = if (isInsideNavBar()) ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE else ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
      mySecondaryActionsButton = object : ActionButton(
        mySecondaryActions, presentationFactory.getPresentation(mySecondaryActions), myPlace, minimumSize) {
        override fun getShortcutText(): String? {
          val shortcut = myPresentation.getClientProperty(SECONDARY_SHORTCUT)
          return shortcut ?: super.getShortcutText()
        }
      }
        .apply {
          setNoIconsInPopup(true)
          putClientProperty(ActionToolbar.SECONDARY_ACTION_PROPERTY, true)
        }
      add(ActionToolbar.SECONDARY_ACTION_CONSTRAINT, mySecondaryActionsButton)
    }

    for (action in rightAligned) {
      val button = getOrCreateActionComponent(action)
      if (!isInsideNavBar()) {
        button.putClientProperty(RIGHT_ALIGN_KEY, true)
      }
      add(button)
    }
  }

  protected open fun isSecondaryAction(action: AnAction, actionIndex: Int): Boolean {
    return !myActionGroup.isPrimary(action)
  }

  protected open fun isAlignmentEnabled(): Boolean = true

  protected open fun forceRightAlignment(): Boolean = false

  private fun addActionButtonImpl(action: AnAction, index: Int) {
    val component = getOrCreateActionComponent(action)
    val constraints: Any =
      if (component is ActionButton) ActionToolbar.ACTION_BUTTON_CONSTRAINT else ActionToolbar.CUSTOM_COMPONENT_CONSTRAINT
    add(component, constraints, index)
  }

  protected fun getOrCreateActionComponent(action: AnAction): JComponent {
    val presentation = presentationFactory.getPresentation(action)
    val componentProvider =
      if (action is CustomComponentAction) action
      else presentation.getClientProperty(ActionUtil.COMPONENT_PROVIDER)
    if (componentProvider != null) {
      return getCustomComponent(action, presentation, componentProvider)
    }
    else {
      if (action is ActionWithDelegate<*> && action.getDelegate() is CustomComponentAction) {
        LOG.error("`CustomComponentAction` component is ignored due to wrapping: " +
                  operationName(action, null, myPlace))
      }
      myLastNewButtonActionClass = action.javaClass.getName()
      return createToolbarButton(action, getActionButtonLook(), myPlace, presentation, myMinimumButtonSizeSupplier)
    }
  }

  private fun getCustomComponent(
    anAction: AnAction,
    presentation: Presentation,
    action: CustomComponentAction,
  ): JComponent {
    var customComponent = presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY)
    if (customComponent == null) {
      myLastNewButtonActionClass = anAction.javaClass.getName()
      customComponent = createCustomComponent(action, presentation)
      if (customComponent.parent != null && customComponent.getClientProperty(SUPPRESS_ACTION_COMPONENT_WARNING) == null) {
        customComponent.putClientProperty(SUPPRESS_ACTION_COMPONENT_WARNING, true)
        LOG.warn(action.javaClass.getSimpleName() + ".component.getParent() != null in '" + myPlace + "' toolbar. " +
                 "Custom components shall not be reused.")
      }
      presentation.putClientProperty(CustomComponentAction.COMPONENT_KEY, customComponent)
      ClientProperty.put(customComponent, CustomComponentAction.ACTION_KEY, anAction)
      action.updateCustomComponent(customComponent, presentation)
    }

    val clickable = UIUtil.findComponentOfType(customComponent, AbstractButton::class.java)
    if (clickable != null) {
      class ToolbarClicksCollectorListener : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          ToolbarClicksCollector.record(anAction, myPlace, e, getDataContext())
        }
      }
      if (clickable.mouseListeners.find { it is ToolbarClicksCollectorListener } == null) {
        clickable.addMouseListener(ToolbarClicksCollectorListener())
      }
    }
    return customComponent
  }

  protected open fun createCustomComponent(action: CustomComponentAction, presentation: Presentation): JComponent {
    val result = action.createCustomComponent(presentation, myPlace)
    applyToolbarLook(getActionButtonLook(), presentation, result)
    return result
  }

  private fun tweakActionComponentUI(actionComponent: Component) {
    if (ActionPlaces.EDITOR_TOOLBAR == myPlace) {
      // tweak font & color for editor toolbar to match editor tabs style
      actionComponent.setFont(RelativeFont.NORMAL.fromResource("Toolbar.Component.fontSizeOffset", -2)
                                .derive(StartupUiUtil.labelFont))
      actionComponent.setForeground(ColorUtil.dimmer(JBColor.BLACK))
    }
  }

  protected open fun createToolbarButton(
    action: AnAction,
    look: ActionButtonLook?,
    place: String,
    presentation: Presentation,
    minimumSize: Dimension,
  ): ActionButton {
    return createToolbarButton(action, look, place, presentation, Supplier { minimumSize })
  }

  /**
   * Override together with [isDefaultActionButtonImplementation]
   */
  protected open fun createToolbarButton(
    action: AnAction,
    look: ActionButtonLook?,
    place: String,
    presentation: Presentation,
    minimumSize: Supplier<out Dimension>,
  ): ActionButton {
    val actionButton = when {
      presentation.getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR) == true ->
        createTextButton(action, place, presentation, minimumSize)
      else -> createIconButton(action, place, presentation, minimumSize)
    }
    applyToolbarLook(look, presentation, actionButton)
    return actionButton
  }

  /**
   * Override together with [isDefaultActionButtonImplementation]
   */
  protected open fun createIconButton(
    action: AnAction,
    place: String,
    presentation: Presentation,
    minimumSize: Supplier<out Dimension>,
  ): ActionButton {
    return ActionButton(action, presentation, place, minimumSize)
  }

  /**
   * Override together with [isDefaultActionButtonImplementation]
   */
  protected open fun createTextButton(
    action: AnAction,
    place: String,
    presentation: Presentation,
    minimumSize: Supplier<out Dimension>,
  ): ActionButtonWithText {
    val mnemonic = action.templatePresentation.getMnemonic()
    val buttonWithText = ActionButtonWithText(action, presentation, place, minimumSize)

    if (mnemonic != KeyEvent.VK_UNDEFINED) {
      buttonWithText.registerKeyboardAction(
        { buttonWithText.click() },
        KeyStroke.getKeyStroke(mnemonic, InputEvent.ALT_DOWN_MASK), WHEN_IN_FOCUSED_WINDOW)
    }
    return buttonWithText
  }

  /**
   * Return `true` if the [oldActionButton] instance can be reused
   * Return `false` if the difference with new [newPresentation] from the prior one requres re-creation of the ActionButton
   */
  protected open fun isDefaultActionButtonImplementation(oldActionButton: ActionButton, newPresentation: Presentation): Boolean {
    val shouldHaveText = newPresentation.getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR) == true
    if (shouldHaveText) {
      return oldActionButton.javaClass == ActionButtonWithText::class.java
    }
    else {
      return oldActionButton.javaClass == ActionButton::class.java
    }
  }

  protected fun applyToolbarLook(look: ActionButtonLook?, presentation: Presentation, component: JComponent) {
    if (component is ActionButton) {
      component.setLook(look)
      component.setBorder(getActionButtonBorder())
    }
    tweakActionComponentUI(component)
    followToolbarComponent(presentation, component, getComponent())
  }

  protected open fun getActionButtonLook(): ActionButtonLook? {
    return when {
      myCustomButtonLook != null -> myCustomButtonLook
      myMinimalMode -> myMinimalButtonLook
      myDecorateButtons -> object : ActionButtonLook() {
        override fun paintBorder(g: Graphics, c: JComponent, state: Int) {
          g.color = JBColor.border()
          g.drawLine(c.getWidth() - 1, 0, c.getWidth() - 1, c.getHeight())
        }

        override fun paintBackground(g: Graphics, component: JComponent, state: Int) {
          if (state == ActionButtonComponent.PUSHED) {
            g.color = component.getBackground().darker()
            (g as Graphics2D).fill(g.clip)
          }
        }
      }
      else -> null
    }
  }

  override fun doLayout() {
    if (!isValid) {
      calculateBounds()
      calculateAutoPopupRect()
    }
    val componentCount = getComponentCount()
    LOG.assertTrue(componentCount <= myComponentBounds.size)
    for (i in componentCount - 1 downTo 0) {
      val component = getComponent(i)
      component.bounds = myComponentBounds[i]
    }

    if (myNeedCheckHoverOnLayout) {
      val location = MouseInfo.getPointerInfo().location
      SwingUtilities.convertPointFromScreen(location, this)
      for (i in componentCount - 1 downTo 0) {
        val component = getComponent(i)
        if (component is ActionButton && component.bounds.contains(location)) {
          component.myRollover = true
        }
      }
    }
  }

  override fun validate() {
    if (!isValid) {
      calculateBounds()
      calculateAutoPopupRect()
      super.validate()
    }
  }

  protected open fun getChildPreferredSize(index: Int): Dimension {
    val component = getComponent(index)
    return if (component.isVisible) component.preferredSize else Dimension()
  }

  private val maxButtonWidth: Int
    /**
     * @return maximum button width
     */
    get() {
      var width = 0
      for (i in 0..<componentCount) {
        val dimension = getChildPreferredSize(i)
        width = max(width, dimension.width)
      }
      return width
    }

  /**
   * @return maximum button height
   */
  override fun getMaxButtonHeight(): Int {
    var height = 0
    for (i in 0..<componentCount) {
      val dimension = getChildPreferredSize(i)
      height = max(height, dimension.height)
    }
    return height
  }

  /**
   * The visibility of this method has been changed to private.
   * It is no longer necessary to override this method to implement a custom toolbar layout.
   * Instead, consider implementing your own [ToolbarLayoutStrategy].
   */
  private fun calculateBounds() {
    myComponentBounds.clear()
    myComponentBounds.addAll(myLayoutStrategy.calculateBounds(this))
  }

  private fun calculateAutoPopupRect() {
    var firstHidden = -1
    var edge = 0
    for (i in myComponentBounds.indices) {
      val r = myComponentBounds[i]
      if (r.x == Int.MAX_VALUE || r.y == Int.MAX_VALUE) {
        firstHidden = i
        break
      }
      edge = (if (myOrientation == SwingConstants.HORIZONTAL) r.maxX else r.maxY).toInt()
    }

    if (firstHidden >= 0) {
      val size = getSize()
      val insets = getInsets()
      myFirstOutsideIndex = firstHidden
      myAutoPopupRec = if (myOrientation == SwingConstants.HORIZONTAL)
        Rectangle(edge, insets.top, size.width - edge - insets.right, size.height - insets.top - insets.bottom)
      else
        Rectangle(insets.left, edge, size.width - insets.left - insets.right, size.height - edge - insets.bottom)
    }
    else {
      myAutoPopupRec = null
      myFirstOutsideIndex = -1
    }
  }

  override fun getPreferredSize(): Dimension {
    return myCachedImage?.let {
      Dimension(ImageUtil.getUserWidth(it), ImageUtil.getUserHeight(it))
    } ?: updatePreferredSize(super.getPreferredSize())
  }

  protected open fun updatePreferredSize(preferredSize: Dimension): Dimension {
    return myLayoutStrategy.calcPreferredSize(this)
  }

  /**
   * Forces the minimum size of the toolbar to show all buttons, When set to `true`. By default (`false`) the
   * toolbar will shrink further and show the auto popup chevron button.
   */
  fun setForceMinimumSize(force: Boolean) {
    myForceMinimumSize = force
  }

  fun setCustomButtonLook(customButtonLook: ActionButtonLook?) {
    myCustomButtonLook = customButtonLook
  }

  private fun getActionButtonBorder(): Border = myActionButtonBorder ?: ActionButtonBorder({ 2 }, { 1 })

  fun setActionButtonBorder(border: Border?) {
    myActionButtonBorder = border
  }

  fun setActionButtonBorder(
    directionalGapUnscaledSupplier: Supplier<Int>,
    orthogonalGapUnscaledSupplier: Supplier<Int>,
  ) {
    myActionButtonBorder = ActionButtonBorder(directionalGapUnscaledSupplier, orthogonalGapUnscaledSupplier)
  }

  fun setActionButtonBorder(directionalGap: Int, orthogonalGap: Int) {
    myActionButtonBorder = ActionButtonBorder({ directionalGap }, { orthogonalGap })
  }

  fun setSeparatorCreator(separatorCreator: Function<in String?, out Component>) {
    mySeparatorCreator = separatorCreator
  }

  /**
   * By default minimum size is to show chevron only.
   * If this option is `true` toolbar shows at least one (the first) component plus chevron (if need)
   *
   */
  @Deprecated(
    """method is deprecated and going to be removed in future releases. Please use {@link ActionToolbar#setLayoutStrategy(ToolbarLayoutStrategy)} )}
    method to set necessary layout for toolbar""")
  fun setForceShowFirstComponent(showFirstComponent: Boolean) {
    setLayoutStrategy(autoLayoutStrategy(showFirstComponent))
  }

  /**
   * This option makes sense when you use a toolbar inside JBPopup
   * When some 'actions' are hidden under the chevron the popup with extra components would be shown/hidden
   * with size adjustments for the main popup (this is default behavior).
   * If this option is `true` size adjustments would be omitted
   */
  fun setSkipWindowAdjustments(skipWindowAdjustments: Boolean) {
    mySkipWindowAdjustments = skipWindowAdjustments
  }

  override fun getMinimumSize(): Dimension {
    return updateMinimumSize(super.getMinimumSize())
  }

  protected fun updateMinimumSize(minimumSize: Dimension): Dimension {
    if (myForceMinimumSize) {
      return updatePreferredSize(minimumSize)
    }

    return myLayoutStrategy.calcMinimumSize(this)
  }

  protected open fun getSeparatorColor(): Color = JBUI.CurrentTheme.Toolbar.SEPARATOR_COLOR

  protected open fun getSeparatorHeight(): Int = scale(24)

  private fun paintToImage(comp: JComponent): Image? {
    val size = comp.size
    if (size.width < 1 || size.height < 1) return null
    val image = UIUtil.createImage(comp, size.width, size.height, BufferedImage.TYPE_INT_ARGB)
    UIUtil.useSafely(image.graphics) { comp.paint(it) }
    return image
  }

  private inner class MySeparator(private val myText: String?) : JComponent() {
    init {
      setFont(JBUI.Fonts.toolbarSmallComboBoxFont())
      setupComponentAntialiasing(this)
    }

    override fun getPreferredSize(): Dimension {
      val gap = scale(2)
      val center = scale(3)
      val width = gap * 2 + center
      val height: Int = getSeparatorHeight()

      return when {
        myOrientation == SwingConstants.HORIZONTAL && myText != null -> {
          val fontMetrics = getFontMetrics(getFont())
          val textWidth = UIUtil.computeStringWidth(this, fontMetrics, myText)
          JBDimension(width + gap * 2 + textWidth, max(fontMetrics.height, height), true)
        }
        myOrientation == SwingConstants.HORIZONTAL -> JBDimension(width, height, true)
        else -> JBDimension(height, width, true)
      }
    }

    override fun paintComponent(g: Graphics) {
      if (parent == null) return

      val gap = scale(2)
      val center = scale(3)
      val offset: Int
      if (myOrientation == SwingConstants.HORIZONTAL) {
        offset = this@ActionToolbarImpl.getHeight() - getMaxButtonHeight() - 1
      }
      else {
        offset = this@ActionToolbarImpl.getWidth() - maxButtonWidth - 1
      }

      val service = InternalUICustomization.getInstance()
      val graphics = g.create()
      val g2 = (service?.preserveGraphics(graphics) ?: graphics) as Graphics2D
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)

      try {
        g2.color = getSeparatorColor()
        if (myOrientation == SwingConstants.HORIZONTAL) {
          val y2 = this@ActionToolbarImpl.getHeight() - gap * 2 - offset
          LinePainter2D.paint(g2, center.toDouble(), gap.toDouble(), center.toDouble(), y2.toDouble())

          if (myText != null) {
            val fontMetrics = getFontMetrics(getFont())
            val top = (getHeight() - fontMetrics.height) / 2
            g.color = JBColor.foreground()
            @Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
            SwingUtilities2.drawString(this, g, myText, gap * 2 + center + gap, top + fontMetrics.ascent)
          }
        }
        else {
          LinePainter2D.paint(g2, gap.toDouble(), center.toDouble(), (this@ActionToolbarImpl.getWidth() - gap * 2 - offset).toDouble(),
                              center.toDouble())
        }
      }
      finally {
        g2.dispose()
      }
    }
  }

  override fun setMinimumButtonSize(size: Dimension) {
    setMinimumButtonSize { size }
  }

  fun setMinimumButtonSize(size: Supplier<out Dimension>) {
    myMinimumButtonSizeSupplier = size
    updateMinimumButtonSize()
    revalidate()
  }

  override fun getMinimumButtonSize(): Dimension {
    return myMinimumButtonSizeSupplier.get()
  }

  val minimumButtonSizeSupplier: Supplier<out Dimension>
    get() = myMinimumButtonSizeSupplier

  private fun updateMinimumButtonSize() {
    @Suppress("SENSELESS_COMPARISON")
    if (myMinimumButtonSizeSupplier == null) {
      return  // called from the superclass constructor through updateUI()
    }
    val minimumButtonSize = JBDimension.create(myMinimumButtonSizeSupplier.get(), true)
    for (i in componentCount - 1 downTo 0) {
      val component = getComponent(i)
      if (component is ActionButton) {
        component.setMinimumButtonSize(myMinimumButtonSizeSupplier)
      }
      else if (component is JLabel && LOADING_LABEL == component.getName()) {
        val dimension = Dimension()
        dimension.width = max(minimumButtonSize.width, component.icon.iconWidth)
        dimension.height = max(minimumButtonSize.height, component.icon.iconHeight)
        JBInsets.addTo(dimension, component.getInsets())
        component.preferredSize = dimension
      }
    }
  }

  override fun setOrientation(
    @MagicConstant(intValues = [SwingConstants.HORIZONTAL.toLong(), SwingConstants.VERTICAL.toLong()])
    orientation: Int,
  ) {
    require(SwingConstants.HORIZONTAL == orientation || SwingConstants.VERTICAL == orientation) { "wrong orientation: $orientation" }
    myOrientation = orientation
  }

  @MagicConstant(intValues = [SwingConstants.HORIZONTAL.toLong(), SwingConstants.VERTICAL.toLong()])
  override fun getOrientation(): Int {
    return myOrientation
  }

  @Deprecated("")
  override fun updateActionsImmediately() {
    updateActionsImmediately(false)
  }

  @RequiresEdt
  override fun updateActionsAsync(): Future<*> {
    updateActionsImmediately(false)
    val update = myLastUpdate
    return update?.asCompletableFuture() ?: CompletableFuture.completedFuture(null)
  }


  @RequiresEdt
  protected fun updateActionsImmediately(includeInvisible: Boolean) {
    val isTestMode = ApplicationManager.getApplication().isUnitTestMode()
    if (parent == null && !isTestMode && !includeInvisible) {
      LOG.warn(Throwable(
        "'$myPlace' toolbar manual update is ignored. " +
        "Newly created toolbars are updated automatically on `addNotify`.",
        myCreationTrace))
      return
    }
    updateActionsWithoutLoadingIcon(includeInvisible)
  }

  @RequiresEdt
  protected fun updateActionsWithoutLoadingIcon(includeInvisible: Boolean) {
    // null when called through updateUI from a superclass constructor
    myUpdater.updateActions(now = true, forced = false, includeInvisible = includeInvisible)
  }

  @OptIn(InternalCoroutinesApi::class)
  private fun updateActionsImpl(forced: Boolean) {
    if (forced) myForcedUpdateRequested = true
    val forcedActual = forced || myForcedUpdateRequested
    val isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode()

    val dataContext = Utils.createAsyncDataContext(getDataContext())

    cancelCurrentUpdate()

    val firstTimeFastTrack = !hasVisibleActions() && componentCount == 1 && !ClientProperty.isTrue(this, SUPPRESS_FAST_TRACK)

    val cs = service<CoreUiCoroutineScopeHolder>().coroutineScope
    val job = cs.launch(
      Dispatchers.UiWithModelAccess + ModalityState.any().asContextElement() +
      ClientId.coroutineContext(), CoroutineStart.UNDISPATCHED) {
      try {
        val actions = Utils.expandActionGroupSuspend(
          myActionGroup, presentationFactory, dataContext,
          myPlace, ActualActionUiKind.Toolbar(this@ActionToolbarImpl),
          firstTimeFastTrack || isUnitTestMode)
        myLastNewButtonActionClass = null
        actionsUpdated(forcedActual, actions)
        if (firstTimeFastTrack) ClientProperty.put(this@ActionToolbarImpl, SUPPRESS_FAST_TRACK, true)
        reportActionButtonChangedEveryTimeIfNeeded()
      }
      catch (ex: CancellationException) {
        throw ex
      }
      catch (ex: Throwable) {
        LOG.error(ex)
      }
    }
    myLastUpdate = job
    job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) {
      if (myLastUpdate === job) {
        myLastUpdate = null
      }
    }
    mySecondaryActionsButton?.run {
      update()
      repaint()
    }
  }

  private fun reportActionButtonChangedEveryTimeIfNeeded() {
    if (myUpdatesWithNewButtons < 0) return  // already reported

    if (myLastNewButtonActionClass == null) {
      myUpdatesWithNewButtons = 0
      return
    }
    if (++myUpdatesWithNewButtons < 20) return
    LOG.error(Throwable("'" + myPlace + "' toolbar creates new components for " + myUpdatesWithNewButtons +
                        " updates in a row. The latest button is created for '" + myLastNewButtonActionClass + "'." +
                        " Toolbar action instances must not change on every update", myCreationTrace))
    myUpdatesWithNewButtons = -1
  }

  private fun addLoadingIcon() {
    val label = JLabel()
    label.setName(LOADING_LABEL)
    label.setBorder(getActionButtonBorder())
    if (this is PopupToolbar) {
      label.setIcon(AnimatedIcon.Default.INSTANCE)
    }
    else {
      val suppressLoading = ActionPlaces.MAIN_TOOLBAR == myPlace ||
                            ActionPlaces.NAVIGATION_BAR_TOOLBAR == myPlace ||
                            ActionPlaces.TOOLWINDOW_TITLE == myPlace ||
                            ActionPlaces.WELCOME_SCREEN == myPlace
      if (suppressLoading) {
        label.setIcon(EmptyIcon.create(16, 16))
      }
      else {
        val icon = AnimatedIcon.Default.INSTANCE
        label.setIcon(EmptyIcon.create(icon.iconWidth, icon.iconHeight))
        EdtScheduler.getInstance().schedule(Registry.intValue("actionSystem.toolbar.progress.icon.delay", 500), CoroutineSupport.UiDispatcherKind.STRICT) {
          label.setIcon(icon)
        }
      }
    }
    myForcedUpdateRequested = true
    add(label)
    updateMinimumButtonSize()
  }

  protected open fun actionsUpdated(forced: Boolean, newVisibleActions: List<AnAction>) {
    myListeners.getMulticaster().actionsUpdated()
    if (!forced && !presentationFactory.isNeedRebuild) {
      if (replaceButtonsForNewActionInstances(newVisibleActions)) return
    }
    myForcedUpdateRequested = false
    myCachedImage = null
    val fullReset = newVisibleActions.isEmpty() || myVisibleActions.isEmpty()
    myVisibleActions = newVisibleActions

    val skipSizeAdjustments = mySkipWindowAdjustments || skipSizeAdjustments()
    val compForSize = guessBestParentForSizeAdjustment()
    val oldSize = if (skipSizeAdjustments) null else compForSize.preferredSize

    removeAll()
    mySecondaryActions.removeAll()
    mySecondaryActionsButton = null
    fillToolBar(myVisibleActions, myLayoutSecondaryActions && myOrientation == SwingConstants.HORIZONTAL)
    presentationFactory.resetNeedRebuild()

    if (!skipSizeAdjustments) {
      val availSize = compForSize.size
      val newSize = compForSize.preferredSize
      adjustContainerWindowSize(fullReset, availSize, oldSize!!, newSize)
    }

    compForSize.revalidate()
    compForSize.repaint()
  }

  /**
   * Try to update toolbar without calling [JComponent.remove] on all old components.
   *
   * We assume that Toolbar might have non-action [JComponent.getComponents],
   * so we need to find the old action component to replace it.
   *
   * For non-trivial cases, fallback to the full rebuild.
   */
  private fun replaceButtonsForNewActionInstances(newVisibleActions: List<AnAction>): Boolean {
    data class Replacement(val buttonIndex: Int, val nextAction: AnAction)

    if (newVisibleActions.size != myVisibleActions.size) return false
    val components = getComponents()
    val pairs = ArrayList<Replacement>()

    var buttonIndex = 0 // avoid N^2 button search
    for (index in myVisibleActions.indices) {
      val prev: AnAction = myVisibleActions[index]
      val next: AnAction = newVisibleActions[index]
      if (next.javaClass != prev.javaClass) return false // in theory, that should be OK, but better to be safe

      if (prev is Separator) {
        if (next !is Separator) return false
        if (myShowSeparatorTitles && prev.text != next.text) return false
        continue
      }

      val nextP = presentationFactory.getPresentation(next)

      val nextIsCustom = next is CustomComponentAction ||
                         nextP.getClientProperty(ActionUtil.COMPONENT_PROVIDER) != null
      val prevIsCustom = nextP.getClientProperty(CustomComponentAction.COMPONENT_KEY) != null
      if (nextIsCustom != prevIsCustom) return false // ActionUtil.COMPONENT_PROVIDER can change dynamically
      if (nextIsCustom) {
        if (next === prev) continue // keep old custom component untouched
        return false // can't find what component to replace
      }

      var actionButton: ActionButton? = null
      while (buttonIndex < components.size) {
        val component = components[buttonIndex]
        buttonIndex++

        if (component is ActionButton && component.action === prev) {
          actionButton = component
          break
        }
      }
      if (actionButton == null) return false

      if (next === prev && isDefaultActionButtonImplementation(actionButton, nextP)) {
        continue // keep old component untouched
      }

      // create a new button and replace it in-place
      pairs.add(Replacement(buttonIndex - 1, next))
    }

    if (pairs.size == newVisibleActions.size) return false // no gain from in-place updates

    myVisibleActions = newVisibleActions
    for (pair in pairs) {
      val index: Int = pair.buttonIndex
      remove(index)
      addActionButtonImpl(pair.nextAction, index)
      val button = getComponent(index)
      button.bounds = components[index].bounds
      button.validate()
    }
    return true
  }

  /**
   * Automatic container window size adjustment is performed only if:
   * 
   * - the window is a popup or a lightweight hint;
   * - and fast track actions update is not suppressed.
   * 
   * Fast track action update is normally enabled for regular toolbars inside popups
   * but it's usually suppressed for toolbars of a "floating" nature,
   * and for such toolbars size adjustment can create UI bugs like IJPL-187340,
   * when the popup is resized over and over again every time the toolbar is shown.
   */
  private fun skipSizeAdjustments(): Boolean {
    return (PopupUtil.getPopupContainerFor(this) == null &&
            getParentLightweightHintComponent(this) == null) ||
           ClientProperty.isTrue(this, SUPPRESS_FAST_TRACK)
  }

  private fun adjustContainerWindowSize(
    fullReset: Boolean,
    availSize: Dimension,
    oldSize: Dimension,
    newSize: Dimension,
  ) {
    val delta = Dimension(newSize.width - oldSize.width, newSize.height - oldSize.height)
    if (!fullReset) {
      if (myOrientation == SwingConstants.HORIZONTAL) delta.width = 0
      if (myOrientation == SwingConstants.VERTICAL) delta.height = 0
    }
    delta.width = max(0, delta.width - max(0, availSize.width - oldSize.width))
    delta.height = max(0, delta.height - max(0, availSize.height - oldSize.height))
    if (delta.width == 0 && delta.height == 0) {
      return
    }
    val popup = PopupUtil.getPopupContainerFor(this)
    if (popup != null) {
      val size = popup.getSize()
      size.width += delta.width
      size.height += delta.height
      popup.setSize(size)
      popup.moveToFitScreen()
    }
    else {
      val parent = getParentLightweightHintComponent(this)
      if (parent != null) { // a LightweightHint that fits in
        val size = parent.size
        size.width += delta.width
        size.height += delta.height
        parent.size = size
      }
    }
  }

  private fun guessBestParentForSizeAdjustment(): Component {
    if (this is PopupToolbar) return this
    var result: Component? = parent ?: this
    var availSize = result!!.size
    var cur: Component? = result.parent
    while (cur != null) {
      if (cur is JRootPane) break
      if (cur is JLayeredPane && cur.parent is JRootPane) break
      val size = cur.size
      if (myOrientation == SwingConstants.HORIZONTAL && size.height - availSize.height > 8 ||
          myOrientation == SwingConstants.VERTICAL && size.width - availSize.width > 8) {
        if (availSize.width == 0 && availSize.height == 0) result = cur
        break
      }
      result = cur
      availSize = cur.size
      cur = cur.parent
    }
    return result!!
  }

  private fun getParentLightweightHintComponent(component: JComponent): JComponent? {
    var result: JComponent? = null
    UIUtil.uiParents(component, false).reduce { a, b ->
      if (b is JLayeredPane && b.getLayer(a) == JLayeredPane.POPUP_LAYER) {
        result = a as JComponent?
      }
      b
    }
    return result
  }

  override fun hasVisibleActions(): Boolean {
    return !myVisibleActions.isEmpty()
  }

  fun hasVisibleAction(action: AnAction): Boolean {
    return myVisibleActions.contains(action)
  }

  override fun getTargetComponent(): JComponent? {
    return myTargetComponent
  }

  override fun setTargetComponent(component: JComponent?) {
    if (myTargetComponent == null) {
      putClientProperty(SUPPRESS_TARGET_COMPONENT_WARNING, true)
    }
    if (myTargetComponent !== component) {
      myTargetComponent = component
      if (isShowing()) {
        @Suppress("DEPRECATION")
        updateActionsImmediately()
      }
    }
  }

  override fun getToolbarDataContext(): DataContext {
    return getDataContext()
  }

  override fun setShowSeparatorTitles(showSeparatorTitles: Boolean) {
    myShowSeparatorTitles = showSeparatorTitles
  }

  override fun addListener(listener: ActionToolbarListener, parentDisposable: Disposable) {
    myListeners.addListener(listener, parentDisposable)
  }

  protected open fun getDataContext(): DataContext {
    if (myTargetComponent == null && getClientProperty(
        SUPPRESS_TARGET_COMPONENT_WARNING) == null && !ApplicationManager.getApplication().isUnitTestMode()) {
      putClientProperty(SUPPRESS_TARGET_COMPONENT_WARNING, true)
      LOG.warn(Throwable(
        "'" + myPlace + "' toolbar by default uses any focused component to update its actions. " +
        "Toolbar actions that need local UI context would be incorrectly disabled. " +
        "Please call toolbar.setTargetComponent() explicitly.",
        myCreationTrace))
    }
    val target = (if (myTargetComponent != null) myTargetComponent
    else com.intellij.util.IJSwingUtilities.getFocusedComponentInWindowOrSelf(this))!!
    return DataManager.getInstance().getDataContext(target)
  }

  override fun processMouseMotionEvent(e: MouseEvent) {
    super.processMouseMotionEvent(e)

    myAutoPopupRec?.run {
      if (contains(e.getPoint())) {
        showAutoPopup()
      }
    }
  }

  private fun showAutoPopup() {
    if (isPopupShowing) return

    val group: ActionGroup
    if (myOrientation == SwingConstants.HORIZONTAL) {
      group = myActionGroup
    }
    else {
      val outside = DefaultActionGroup()
      for (i in myFirstOutsideIndex..<myVisibleActions.size) {
        outside.add(myVisibleActions[i])
      }
      group = outside
    }

    val popupToolbar: PopupToolbar = object : PopupToolbar(myPlace, group, true, this@ActionToolbarImpl) {
      override fun onOtherActionPerformed() {
        hidePopup()
      }

      override fun getDataContext() = this@ActionToolbarImpl.getDataContext()
    }
    popupToolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY)

    val location: Point
    if (myOrientation == SwingConstants.HORIZONTAL) {
      location = locationOnScreen

      val toolWindow = PlatformDataKeys.TOOL_WINDOW.getData(DataManager.getInstance().getDataContext(this))
      if (toolWindow != null && toolWindow.getAnchor() == ToolWindowAnchor.RIGHT) {
        val rightXOnScreen = location.x + getWidth()
        val toolbarPreferredWidth = popupToolbar.getPreferredSize().width
        location.x = rightXOnScreen - toolbarPreferredWidth
      }
    }
    else {
      location = locationOnScreen
      location.y = location.y + getHeight() - popupToolbar.getPreferredSize().height
    }

    val actionManager = getInstanceEx()
    val builder = JBPopupFactory.getInstance().createComponentPopupBuilder(popupToolbar, null)
    builder.setResizable(false)
      .setMovable(true) // fit the screen automatically
      .setFocusable(false) // do not steal focus on showing, and don't close on IDE frame gaining focus (see AbstractPopup.isCancelNeeded)
      .setMayBeParent(true)
      .setTitle(null)
      .setCancelOnClickOutside(true)
      .setCancelOnOtherWindowOpen(true)
      .setCancelCallback {
        val toClose = actionManager.isActionPopupStackEmpty
        if (toClose) {
          myUpdater.updateActions(false, true, false)
        }
        toClose
      }
      .setCancelOnMouseOutCallback { event ->
        val window = ComponentUtil.getWindow(popupToolbar)
        if (window != null && Window.Type.POPUP == window.type) {
          val parent = UIUtil.uiParents(event.component, false).find { it == window }
          if (parent != null) return@setCancelOnMouseOutCallback false // mouse over a child popup
        }
        myAutoPopupRec != null &&
        actionManager.isActionPopupStackEmpty &&
        !RelativeRectangle(this, myAutoPopupRec).contains(DevicePoint(event))
      }

    builder.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        processClosed()
      }
    })
    val popup = builder.createPopup()
    myPopup = popup
    Disposer.register(popup, popupToolbar)
    popup.showInScreenCoordinates(this, location)

    val window = SwingUtilities.getWindowAncestor(this) ?: return

    val componentAdapter: ComponentListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        hidePopup()
      }

      override fun componentMoved(e: ComponentEvent?) {
        hidePopup()
      }

      override fun componentShown(e: ComponentEvent?) {
        hidePopup()
      }

      override fun componentHidden(e: ComponentEvent?) {
        hidePopup()
      }
    }
    window.addComponentListener(componentAdapter)
    Disposer.register(popupToolbar) { window.removeComponentListener(componentAdapter) }
  }

  private val isPopupShowing: Boolean
    get() = myPopup?.isDisposed() == false

  private fun hidePopup() {
    myPopup?.cancel()
    processClosed()
  }

  private fun processClosed() {
    val popup = myPopup ?: return
    if (popup.isVisible()) {
      // setCancelCallback(..) can override cancel()
      return
    }
    // cancel() already called Disposer.dispose()
    myPopup = null
    myUpdater.updateActions(false, false, false)
  }

  private abstract class PopupToolbar(
    place: String,
    actionGroup: ActionGroup,
    horizontal: Boolean,
    parent: ActionToolbarImpl,
  ) : ActionToolbarImpl(place, actionGroup, horizontal, false, true), AnActionListener, Disposable {
    val myParent: ActionToolbarImpl

    init {
      ApplicationManager.getApplication().getMessageBus().connect(this).subscribe<AnActionListener>(AnActionListener.TOPIC, this)
      myParent = parent
      setBorder(myParent.border)
    }

    override fun getParent(): Container? {
      val parent = super.parent
      return parent ?: myParent
    }

    override fun getPreferredSize(): Dimension {
      val size = super.getPreferredSize()
      size.width = max(size.width, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width)
      size.height = max(size.height, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height)
      if (isPaintParentWhileLoading()) {
        val parentSize = myParent.size
        size.width += parentSize.width
        size.height = parentSize.height
      }
      return size
    }

    override fun doLayout() {
      super.doLayout()
      if (isPaintParentWhileLoading()) {
        val component = getComponent(0) as JLabel
        component.setLocation(component.getX() + myParent.getWidth(), component.getY())
      }
    }

    override fun paint(g: Graphics) {
      if (isPaintParentWhileLoading()) {
        myParent.paint(g)
        paintChildren(g)
      }
      else {
        super.paint(g)
      }
    }

    fun isPaintParentWhileLoading(): Boolean = 
      orientation == SwingConstants.HORIZONTAL && 
      myParent.orientation == SwingConstants.HORIZONTAL && 
      !hasVisibleActions() && 
      myParent.hasVisibleActions() && 
      componentCount == 1 && 
      getComponent(0) is JLabel && 
      LOADING_LABEL == getComponent(0).getName()

    override fun dispose() {
    }

    override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
      if (!hasVisibleAction(action)) {
        onOtherActionPerformed()
      }
    }

    protected abstract fun onOtherActionPerformed()
  }

  override fun setReservePlaceAutoPopupIcon(reserve: Boolean) {
    myReservePlaceAutoPopupIcon = reserve
  }

  override fun isReservePlaceAutoPopupIcon(): Boolean {
    return myReservePlaceAutoPopupIcon && !isInsideNavBar()
  }

  override fun setSecondaryActionsTooltip(secondaryActionsTooltip: @NlsContexts.Tooltip String) {
    mySecondaryActions.templatePresentation.setText(secondaryActionsTooltip)
  }

  override fun setSecondaryActionsShortcut(secondaryActionsShortcut: String) {
    mySecondaryActions.templatePresentation.putClientProperty<String?>(SECONDARY_SHORTCUT, secondaryActionsShortcut)
  }

  override fun setSecondaryActionsIcon(icon: Icon?) {
    setSecondaryActionsIcon(icon, false)
  }

  override fun setSecondaryActionsIcon(icon: Icon?, hideDropdownIcon: Boolean) {
    val presentation = mySecondaryActions.templatePresentation
    presentation.setIcon(icon)
    presentation.putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, if (hideDropdownIcon) true else null)
  }

  override fun getActions(originalProvider: Boolean): List<AnAction> {
    return listOf(myActionGroup)
  }

  override fun getActions(): List<AnAction> {
    if (myVisibleActions.isEmpty()) return listOf<AnAction>()
    val result = ArrayList<AnAction>(myVisibleActions.size)
    var secondary: ArrayList<AnAction>? = null
    for (each in myVisibleActions) {
      if (myActionGroup.isPrimary(each)) {
        result.add(each)
      }
      else {
        if (secondary == null) secondary = ArrayList<AnAction>()
        secondary.add(each)
      }
    }
    if (secondary != null) {
      result.add(Separator())
      result.addAll(secondary)
    }
    return result
  }

  override fun setMiniMode(minimalMode: Boolean) {
    if (myMinimalMode == minimalMode) return
    setMiniModeInner(minimalMode)
    myUpdater.updateActions(false, true, false)
  }

  private fun setMiniModeInner(minimalMode: Boolean) {
    myMinimalMode = minimalMode
    if (myMinimalMode) {
      minimumButtonSize = JBUI.emptySize()
      setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY)
      setBorder(JBUI.Borders.empty())
      setOpaque(false)
    }
    else {
      val i = when (myOrientation) {
        SwingConstants.VERTICAL -> JBUI.CurrentTheme.Toolbar.verticalToolbarInsets()
        else -> JBUI.CurrentTheme.Toolbar.horizontalToolbarInsets()
      }
      setBorder(if (i != null) JBUI.Borders.empty(i) else JBUI.Borders.empty(2))
      minimumButtonSize =
        if (myDecorateButtons) JBUI.size(30, 20)
        else ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
      setOpaque(true)
      setLayoutStrategy(ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY)
      setLayoutSecondaryActions(true)
    }
  }

  @TestOnly
  fun getPresentation(action: AnAction): Presentation {
    return presentationFactory.getPresentation(action)
  }

  /**
   * Clear internal caches.
   *
   * This method can be called after updating [ActionToolbarImpl.myActionGroup]
   * to make sure toolbar does not reference old [AnAction] instances.
   */
  fun reset() {
    cancelCurrentUpdate()

    val isTestMode = ApplicationManager.getApplication().isUnitTestMode()
    val image: Image? = if (!isTestMode && isShowing()) paintToImage(this) else null
    if (image != null) {
      myCachedImage = image
    }

    presentationFactory.reset()
    myVisibleActions = listOf()
    removeAll()

    if (!isTestMode && image == null) {
      addLoadingIcon()
    }
  }

  private fun cancelCurrentUpdate() {
    myLastUpdate?.cancel()
    myLastUpdate = null
  }

  interface SecondaryGroupUpdater {
    fun update(e: AnActionEvent)
  }

  @Suppress("UNCHECKED_CAST")
  private inner class ActionButtonBorder(supplier: Supplier<Insets>)
    : JBEmptyBorder(JBInsets.create(supplier as Supplier<Insets?>, supplier.get())) {
    constructor(
      directionalGapUnscaledSupplier: Supplier<Int>,
      orthogonalGapUnscaledSupplier: Supplier<Int>,
    ) : this(insetsSupplier(directionalGapUnscaledSupplier, orthogonalGapUnscaledSupplier))
  }

  private fun insetsSupplier(
    directionalGapUnscaledSupplier: Supplier<Int>,
    orthogonalGapUnscaledSupplier: Supplier<Int>,
  ): Supplier<Insets> {
    return Supplier {
      val directionalGap = directionalGapUnscaledSupplier.get()
      val orthogonalGap = orthogonalGapUnscaledSupplier.get()
      @Suppress("UseDPIAwareInsets")
      if (myOrientation == SwingConstants.VERTICAL) {
        Insets(directionalGap, orthogonalGap, directionalGap, orthogonalGap)
      }
      else {
        Insets(orthogonalGap, directionalGap, orthogonalGap, directionalGap)
      }
    }
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessibleActionToolbar()
      // We don't need additional grouping for ActionToolbar in the new frame header or if it's empty
      if (!myVisibleActions.isEmpty() && !(isNewUI() && place == ActionPlaces.MAIN_TOOLBAR) && (place != ActionPlaces.NEW_UI_RUN_TOOLBAR)) {
        accessibleContext.setAccessibleName(UIBundle.message("action.toolbar.accessible.group.name"))
      }
      else {
        accessibleContext.setAccessibleName("")
      }
    }

    return accessibleContext
  }

  fun setNeedCheckHoverOnLayout(needCheckHoverOnLayout: Boolean) {
    myNeedCheckHoverOnLayout = needCheckHoverOnLayout
  }

  @Suppress("RedundantInnerClassModifier")
  private inner class AccessibleActionToolbar : AccessibleJPanel() {
    override fun getAccessibleRole() = AccessibilityUtils.GROUPED_ELEMENTS
  }

  companion object {
    const val DO_NOT_ADD_CUSTOMIZATION_HANDLER: String = "ActionToolbarImpl.suppressTargetComponentWarning"
    val SUPPRESS_FAST_TRACK: Key<Boolean> = Key.create("ActionToolbarImpl.suppressFastTrack")

    /**
     * Put `TRUE` into [.putClientProperty] to mark that toolbar
     * should not be hidden by [com.intellij.ide.actions.ToggleToolbarAction].
     */
    const val IMPORTANT_TOOLBAR_KEY: String = "ActionToolbarImpl.importantToolbar"
    const val USE_BASELINE_KEY: String = "ActionToolbarImpl.baseline"

    init {
      addUserScaleChangeListener {
        (ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE as JBDimension).update()
        (ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE as JBDimension).update()
      }
    }

    /** Async toolbars are not updated immediately despite the name of the method.  */
    @JvmStatic
    fun updateAllToolbarsImmediately() {
      updateAllToolbarsImmediately(false)
    }

    @JvmStatic
    fun updateAllToolbarsImmediately(includeInvisible: Boolean) {
      ThreadingAssertions.assertEventDispatchThread()
      for (toolbar in ArrayList<ActionToolbarImpl>(ourToolbars)) {
        toolbar.updateActionsImmediately(includeInvisible)
        for (c in toolbar.components) {
          if (c is ActionButton) {
            c.updateToolTipText()
            c.updateIcon()
          }
          toolbar.updateUI()
        }
      }
    }

    @JvmStatic
    fun resetAllToolbars() {
      ThreadingAssertions.assertEventDispatchThread()
      clearAllCachesAndUpdates()
      for (toolbar in ArrayList(ourToolbars)) {
        toolbar.reset()
      }
    }

    @JvmStatic
    fun isInPopupToolbar(component: Component?): Boolean {
      return ComponentUtil.getParentOfType(PopupToolbar::class.java, component) != null
    }

    @TestOnly
    @JvmStatic
    fun findToolbar(group: ActionGroup): ActionToolbarImpl? {
      return ourToolbars.find { it.myActionGroup == group }
    }
  }
}
