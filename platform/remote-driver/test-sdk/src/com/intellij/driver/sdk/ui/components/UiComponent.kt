package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Driver
import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.sdk.ui.DEFAULT_FIND_TIMEOUT
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.SearchContext
import com.intellij.driver.sdk.ui.UiText
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.isWayland
import com.intellij.driver.sdk.ui.keyboard.RemoteKeyboard
import com.intellij.driver.sdk.ui.keyboard.WithKeyboard
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.Robot
import com.intellij.driver.sdk.ui.remote.RobotProvider
import com.intellij.driver.sdk.ui.remote.SearchService
import com.intellij.driver.sdk.ui.withoutStepOverlays
import com.intellij.driver.sdk.waitAny
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForOne
import com.intellij.driver.sdk.withoutPauseOnIndicators
import com.intellij.openapi.diagnostic.logger
import org.intellij.lang.annotations.Language
import java.awt.Color
import java.awt.IllegalComponentStateException
import java.awt.Point
import java.awt.Rectangle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class ComponentData(
  val xpath: String,
  val driver: Driver,
  val searchService: SearchService,
  val robotProvider: RobotProvider,
  val parentSearchContext: SearchContext,
  val foundComponent: Component?,
  /** If the readable name is provided, it will be used in the error messages */
  val readableName: String? = null,
)

private val LOG = logger<UiComponent>()

open class UiComponent(private val data: ComponentData) : Finder, WithKeyboard {
  private var cachedComponent: Component? = null
  private var readinessCheckInProgress: Boolean = false

  val component: Component
    get() = data.foundComponent
            ?: runCatching { cachedComponent?.takeIf { it.isShowing() } }.getOrNull()
            ?: findComponent()

  override val driver: Driver = data.driver
  override val searchService: SearchService = data.searchService
  override val robotProvider: RobotProvider = data.robotProvider

  val robot: Robot by lazy {
    robotProvider.getRobotFor(component)
  }

  override val searchContext: SearchContext = object : SearchContext {
    override val context: String = data.parentSearchContext.context + data.xpath

    override fun findAll(xpath: String): List<Component> {
      return withoutPauseOnIndicators { withComponent { searchService.findAll(xpath, it) } }
    }
  }

  companion object {
    /** Waits until the parent search context has this component, and returns this component. */
    fun <T : UiComponent> T.waitFound(timeout: Duration? = DEFAULT_FIND_TIMEOUT): T {
      findComponent(timeout)
      return this
    }
  }

  override fun toString(): String {
    val name = data.readableName?.let { if (" " in it) "'$it'" else it } ?: this::class.simpleName
    return "$name[xpath=${data.xpath}]"
  }

  override fun keyboard(keyboardActions: RemoteKeyboard.() -> Unit) {
    component // making sure the component is found
    super.keyboard(keyboardActions)
  }

  open fun waitReady(timeout: Duration = DEFAULT_FIND_TIMEOUT) {}

  private fun findComponent(timeout: Duration? = DEFAULT_FIND_TIMEOUT): Component = withoutPauseOnIndicators {
    val findTimeout = timeout ?: DEFAULT_FIND_TIMEOUT
    val found = waitForOne(
      message = "Find $this in ${data.parentSearchContext.contextAsString}",
      timeout = findTimeout,
      interval = 1.seconds,
      getter = { data.parentSearchContext.findAll(data.xpath) }
    )
    // The readiness check searches inside this component. The cache lets these searches reuse the component.
    // The flag stops a second check when a search during the check finds the component again.
    cachedComponent = found
    if (!readinessCheckInProgress) {
      readinessCheckInProgress = true
      try {
        waitReady(findTimeout)
      }
      finally {
        readinessCheckInProgress = false
      }
    }
    // A search during the readiness check can find the component again and cache the new one.
    cachedComponent ?: found
  }

  /**
   * Runs [action] on the component.
   * After an [IllegalComponentStateException], finds the component again and retries [action].
   */
  fun <T> withComponent(action: (Component) -> T): T {
    var attempt = 1
    while (true) {
      try {
        return action(component)
      }
      catch (e: IllegalComponentStateException) {
        // A component from the constructor cannot be found again.
        if (data.foundComponent != null) throw e

        cachedComponent = null
        if (attempt == 3) throw e
        LOG.debug("Exception while executing action with component. Retry $attempt", e)
        attempt++
      }
    }
  }

  fun present(): Boolean {
    val found = data.parentSearchContext.findAll(data.xpath)
    val present = when (found.size) {
      0 -> false
      1 -> true
      else -> error("There are more than one $this in the hierarchy: $found.\n\t" +
                    "Please use `xx()` if you need to check for more than one $this or clarify your search request.")
    }
    LOG.info("$this is present in hierarchy: $present")
    return present
  }

  fun notPresent(): Boolean = !present()

  /** Waits until the parent search context has no such component. */
  fun waitNotFound(timeout: Duration? = DEFAULT_FIND_TIMEOUT) {
    waitFor(message = "There should be no $this in ${data.parentSearchContext.contextAsString}",
            timeout = timeout ?: DEFAULT_FIND_TIMEOUT,
            interval = 1.seconds) {
      notPresent()
    }
  }

  fun hasVisibleComponent(child: UiComponent): Boolean {
    return hasComponent(child, Component::isShowing)
  }

  fun hasComponent(child: UiComponent, check: (Component) -> Boolean): Boolean {
    return searchContext.findAll(child.data.xpath).any(check)
  }

  fun getParent(): UiComponent {
    val parent = withComponent { it.getParent() }
    return UiComponent(ComponentData(data.xpath + "/..", driver, searchService, robotProvider,
                                     data.parentSearchContext, parent))
  }

  /** Returns the first component that [xpath] finds in the parent search context, or `null`. */
  fun findInParentContext(@Language("XPath") xpath: String): UiComponent? {
    val parentSearchContext = data.parentSearchContext
    val foundComponent = parentSearchContext.findAll(xpath).firstOrNull() ?: return null
    return UiComponent(ComponentData(xpath, driver, searchService, robotProvider,
                                     parentSearchContext, foundComponent))
  }

  fun setFocus() {
    withComponent { robot.focus(it) }
  }

  fun isFocusOwner(): Boolean = withComponent { it.isFocusOwner() }

  fun isEnabled(): Boolean = withComponent { it.isEnabled() }

  fun getLocationOnScreen(): Point {
    check(!driver.isWayland) { "not supported on Wayland" }
    return component.getLocationOnScreen()
  }

  fun getBounds(): Rectangle = component.getBounds()

  fun getWidth(): Int = component.width

  fun getHeight(): Int = component.height

  fun getBackgroundColor(): Color = withComponent { Color(it.getBackground().getRGB()) }

  fun getForegroundColor(): Color = withComponent { Color(it.getForeground().getRGB()) }

  /** The colour on the screen at [point] of this component. A step overlay goes off the screen for the read. */
  fun getColor(point: Point?, moveMouse: Boolean = true): Color {
    if (moveMouse) moveMouse(point)
    return withoutStepOverlays {
      withComponent {
        Color(robot.getColor(it, point).getRGB())
      }
    }
  }

  // Texts

  /** Returns the texts of this component that match [filter], without a wait. */
  fun getAllTexts(filter: ((UiText) -> Boolean)? = null): List<UiText> {
    val texts = withComponent { searchService.findAllText(it) }.map { UiText(this, it) }
    return if (filter == null) texts else texts.filter(filter)
  }

  /** Returns the texts of this component in rows. The rows go from top to bottom, and each row goes from left to right. */
  fun getAllVerticallyOrderedUiText(): List<List<UiText>> {
    return getAllTexts().groupBy { it.point.y }.toSortedMap().values.map { row -> row.sortedBy { it.point.x } }
  }

  fun hasText(text: String): Boolean = getAllTexts().any { it.text == text }

  fun hasSubtext(subtext: String): Boolean = getAllTexts().any { it.text.contains(subtext) }

  /** Waits until at least one text matches [predicate], and returns all the matching texts. */
  fun waitAnyTexts(message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT, predicate: (UiText) -> Boolean = { true }): List<UiText> {
    return waitAny(message = message ?: "Finding at least some texts and filter matching predicate in $this",
                   timeout = timeout,
                   getter = { getAllTexts() },
                   checker = predicate)
  }

  /** Waits until at least one text is equal to [text], and returns all such texts. */
  fun waitAnyTexts(text: String, message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT): List<UiText> {
    return waitAnyTexts(message ?: "Finding at least some texts and filter '$text' in $this", timeout) { it.text == text }
  }

  /** Waits until at least one text contains [text], and returns all such texts. */
  fun waitAnyTextsContains(text: String, message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT): List<UiText> {
    return waitAnyTexts(message ?: "Finding at least some texts and contains '$text' in $this", timeout) { it.text.contains(text) }
  }

  /** Waits until exactly one text matches [predicate], and returns it. */
  fun waitOneText(message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT, predicate: (UiText) -> Boolean): UiText {
    return waitForOne(message = message ?: "Finding one text matching predicate in $this",
                      timeout = timeout,
                      getter = { getAllTexts() },
                      checker = predicate)
  }

  /** Waits until exactly one text is equal to [text], and returns it. */
  fun waitOneText(text: String, message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT): UiText {
    return waitOneText(message ?: "Finding text '$text' in $this", timeout) { it.text == text }
  }

  /** Waits until exactly one text contains [text], and returns it. */
  fun waitOneContainsText(text: String, message: String? = null, ignoreCase: Boolean = true, timeout: Duration = DEFAULT_FIND_TIMEOUT): UiText {
    return waitOneText(message ?: "Finding the text containing '$text' in $this", timeout) {
      it.text.contains(text, ignoreCase = ignoreCase)
    }
  }

  /** Waits until the joined texts of this component contain [text]. */
  fun waitContainsText(text: String, message: String? = null, ignoreCase: Boolean = true, timeout: Duration = DEFAULT_FIND_TIMEOUT) {
    waitFor(message = message ?: "Finding the text containing '$text' in $this",
            timeout = timeout,
            getter = { getAllTexts().asString() },
            checker = { it.contains(text, ignoreCase = ignoreCase) })
  }

  /** Waits until there is no text equal to [text]. */
  fun waitNoTexts(text: String, message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT) {
    waitFor(message = message ?: "Finding no texts '$text' in $this",
            timeout = timeout,
            getter = { getAllTexts() },
            checker = { texts -> texts.none { it.text == text } })
  }

  /**
   * Waits until exactly one row of [getAllVerticallyOrderedUiText] matches [text], and returns that row.
   * With [fullMatch], the joined row texts must be equal to [text]. Otherwise, they must contain [text].
   */
  fun waitOneMatchInVerticallyOrderedText(
    text: String,
    message: String? = null,
    fullMatch: Boolean = true,
    timeout: Duration = DEFAULT_FIND_TIMEOUT,
  ): List<UiText> {
    return waitForOne(message ?: "Find '${text}'(fullMatch = $fullMatch) in vertically ordered text", timeout,
                      getter = { getAllVerticallyOrderedUiText() },
                      checker = { row -> if (fullMatch) row.asString() == text else row.asString().contains(text) })
  }

  // Mouse

  fun click(point: Point? = null, mouseButton: RemoteMouseButton = RemoteMouseButton.LEFT) {
    logGesture("Click", point)
    withComponent { if (point != null) robot.click(it, point, mouseButton) else robot.click(it, mouseButton) }
  }

  /**
   * Posts a click addressed to this component and returns without waiting for it to be dispatched.
   *
   * Use it for a click whose effect cannot be awaited — a click that opens a modal dialog, most of all: the dialog
   * blocks the dispatching thread while it is up, so [click] would wait for its own event forever. In exchange
   * nothing is verified past the component being shown and enabled — not delivery, not the effect. Assert on what
   * the click was supposed to produce, and use `IdeEventQueue.flushQueue` if a later step needs the gesture
   * dispatched first. `Robot.postGesture` documents the full contract.
   */
  fun postClick(point: Point? = null) {
    logGesture("Post click", point)
    withComponent { robot.postGesture(it, point, RemoteMouseButton.LEFT, 1) }
  }

  /** Clicks with the left button and repeats the click until the component gets it. See [Robot.strictClick]. */
  fun strictClick(point: Point? = null) {
    logGesture("Strict click", point)
    withComponent { robot.strictClick(it, point) }
  }

  @Deprecated("The click always uses the left button and ignores mouseButton", ReplaceWith("strictClick(point)"))
  fun strictClick(point: Point? = null, mouseButton: RemoteMouseButton = RemoteMouseButton.LEFT) {
    strictClick(point)
  }

  fun doubleClick(point: Point? = null) {
    logGesture("Double click", point)
    withComponent { if (point != null) robot.click(it, point, RemoteMouseButton.LEFT, 2) else robot.doubleClick(it) }
  }

  fun tripleClick(point: Point? = null) {
    logGesture("Triple click", point)
    withComponent { if (point != null) robot.click(it, point, RemoteMouseButton.LEFT, 3) else robot.tripleClick(it) }
  }

  fun rightClick(point: Point? = null) {
    logGesture("Right click", point)
    withComponent { if (point != null) robot.click(it, point, RemoteMouseButton.RIGHT) else robot.click(it, RemoteMouseButton.RIGHT) }
  }

  fun mouseWheelClick(point: Point? = null) {
    logGesture("Mouse wheel click", point)
    withComponent { if (point != null) robot.click(it, point, RemoteMouseButton.MIDDLE) else robot.click(it, RemoteMouseButton.MIDDLE) }
  }

  fun moveMouse(point: Point? = null) {
    logGesture("Move mouse", point)
    withComponent { if (point != null) robot.moveMouse(it, point) else robot.moveMouse(it) }
  }

  fun press(point: Point? = null) {
    logGesture("Press", point)
    withComponent { robot.moveMouseAndPress(it, point) }
  }

  fun mouseRelease() {
    logGesture("Release", point = null)
    withComponent { robot.releaseMouse(RemoteMouseButton.LEFT) }
  }

  fun mousePressMoveRelease(from: Point, to: Point) {
    withComponent { robot.moveMouse(it, from) }
    robot.pressMouse(RemoteMouseButton.LEFT)
    withComponent { robot.moveMouse(it, to) }
    robot.releaseMouse(RemoteMouseButton.LEFT)
  }

  @Deprecated("The method ignores the to point. Use press(from) and mouseRelease(), or mousePressMoveRelease(from, to).")
  fun mousePressAndRelease(from: Point, to: Point) {
    withComponent { robot.moveMouse(it, from) }
    robot.pressMouse(RemoteMouseButton.LEFT)
    robot.releaseMouse(RemoteMouseButton.LEFT)
  }

  private fun logGesture(gesture: String, point: Point?) {
    LOG.info("$gesture at $this${point?.let { ": $it" } ?: ""}")
  }
}
