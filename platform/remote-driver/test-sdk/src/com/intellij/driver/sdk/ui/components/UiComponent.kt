package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Driver
import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.sdk.ui.DEFAULT_FIND_TIMEOUT
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.SearchContext
import com.intellij.driver.sdk.ui.UiText
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.keyboard.RemoteKeyboard
import com.intellij.driver.sdk.ui.keyboard.WithKeyboard
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.Robot
import com.intellij.driver.sdk.ui.remote.RobotProvider
import com.intellij.driver.sdk.ui.remote.SearchService
import com.intellij.driver.sdk.waitAny
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForOne
import com.intellij.openapi.diagnostic.logger
import org.intellij.lang.annotations.Language
import java.awt.Color
import java.awt.IllegalComponentStateException
import java.awt.Point
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class ComponentData(
  val xpath: String,
  val driver: Driver,
  val searchService: SearchService,
  val robotProvider: RobotProvider,
  val parentSearchContext: SearchContext,
  val foundComponent: Component?,
)

private val LOG = logger<UiComponent>()

open class UiComponent(private val data: ComponentData) : Finder, WithKeyboard {
  private var cachedComponent: Component? = null

  val component: Component
    get() = data.foundComponent
            ?: kotlin.runCatching { cachedComponent?.takeIf { it.isShowing() } }.getOrNull()
            ?: findThisComponent().apply { cachedComponent = this }

  override val driver: Driver = data.driver
  override val searchService: SearchService = data.searchService
  override val robotProvider: RobotProvider = data.robotProvider

  val robot: Robot by lazy {
    data.robotProvider.getRobotFor(component)
  }

  override val searchContext: SearchContext = object : SearchContext {
    override val context: String = data.parentSearchContext.context + data.xpath

    override fun findAll(xpath: String): List<Component> {
      return withComponent { searchService.findAll(xpath, it) }
    }
  }

  companion object {
    /**
     * Waits until the element specified is found within the parent search context.
     *
     * @param timeout The maximum time to wait for the element to not be found. If not specified, the default timeout is used.
     */
    fun <T : UiComponent> T.waitFound(timeout: Duration? = DEFAULT_FIND_TIMEOUT): T {
      findThisComponent(timeout)
      return this
    }
  }

  override fun keyboard(keyboardActions: RemoteKeyboard.() -> Unit) {
    component // making sure the component is found
    super.keyboard(keyboardActions)
  }

  /**
   * Waits until the element specified is not found within the parent search context.
   *
   * @param timeout The maximum time to wait for the element to not be found. If not specified, the default timeout is used.
   */
  fun waitNotFound(timeout: Duration? = DEFAULT_FIND_TIMEOUT) {
    waitFor(message = "There should be no ${this::class.simpleName}[xpath=${data.xpath}] in ${data.parentSearchContext.contextAsString}",
            timeout = timeout ?: DEFAULT_FIND_TIMEOUT,
            interval = 1.seconds) {
      !present()
    }
  }

  override fun toString(): String {
    return this::class.simpleName + "[xpath=${data.xpath}]"
  }

  fun setFocus() {
    withComponent { robot.focus(it) }
  }

  private fun findThisComponent(timeout: Duration? = DEFAULT_FIND_TIMEOUT): Component =
    waitForOne(
      message = "Find ${this::class.simpleName}[xpath=${data.xpath}] in ${data.parentSearchContext.contextAsString}",
      timeout = timeout ?: DEFAULT_FIND_TIMEOUT,
      interval = 1.seconds,
      getter = { data.parentSearchContext.findAll(data.xpath) }
    )

  fun <T> withComponent(action: (Component) -> T): T {
    var lastException: Exception? = null
    var count = 0
    while (count < 3) {
      try {
        return action(component)
      }
      catch (e: IllegalComponentStateException) {
        if (data.foundComponent != null) {
          // cannot do anything about it
          throw e
        }

        // reset cached value, search again next time
        cachedComponent = null

        lastException = e
        count++

        LOG.debug("Exception while executing action with component. Retry ${count}", e)
      }
    }

    throw lastException ?: RuntimeException("Unable to execute action with component")
  }

  /**
   * Returns all UiText elements matching the optional filter without waiting.
   *
   * @param filter Optional predicate to filter the text elements
   * @return List of UiText elements that match the filter, or all text elements if no filter is provided
   * @see waitAnyTexts for waiting until text elements are found
   * @see hasText for checking if specific text exists
   * @see hasSubtext for checking if text containing substring exists
   */
  fun getAllTexts(filter: ((UiText) -> Boolean)? = null): List<UiText> {
    return withComponent { searchService.findAllText(it) }
      .filter { text -> filter?.invoke(UiText(this, text)) ?: true }
      .map { UiText(this, it) }
  }

  /**
   * Waits for a non-empty list of UiText elements matching the specified predicate.
   *
   * @param message Optional custom message for the waiting operation
   * @param timeout The maximum time to wait for matching elements (default: DEFAULT_FIND_TIMEOUT)
   * @param predicate Function to test each UiText element (default: accepts all)
   * @return List of UiText elements that match the predicate
   * @see waitAnyTexts for waiting for specific text content
   * @see getAllTexts for non-blocking retrieval of text elements
   */
  fun waitAnyTexts(message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT, predicate: (UiText) -> Boolean = { true }): List<UiText> {
    return waitAny(message = message ?: "Finding at least some texts and filter matching predicate in $this",
                   timeout = timeout,
                   getter = { getAllTexts() },
                   checker = { predicate(it) }
    )
  }

  /**
   * Waits for a non-empty list of UiText elements with the exact specified text.
   *
   * @param text The exact text to search for
   * @param message Optional custom message for the waiting operation
   * @param timeout The maximum time to wait for matching elements (default: DEFAULT_FIND_TIMEOUT)
   * @return List of UiText elements with the specified text
   * @see waitAnyTexts for waiting with custom predicate
   * @see waitOneText for waiting for exactly one text element
   * @see waitAnyTextsContains for waiting for text containing substring
   */
  fun waitAnyTexts(text: String, message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT): List<UiText> {
    return waitAny(message = message ?: "Finding at least some texts and filter '$text' in $this",
                   timeout = timeout,
                   getter = { getAllTexts() },
                   checker = { it.text == text }
    )
  }

  /**
   * Waits for exactly one UiText element with the specified exact text.
   *
   * @param text The exact text to search for
   * @param message Optional custom message for the waiting operation
   * @param timeout The maximum time to wait for the element (default: DEFAULT_FIND_TIMEOUT)
   * @return The UiText element with the specified text
   * @see waitAnyTexts for waiting for multiple text elements
   * @see waitOneText for waiting with custom predicate
   * @see waitOneContainsText for waiting for text containing substring
   */
  fun waitOneText(text: String, message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT): UiText {
    return waitForOne(message = message ?: "Finding text '$text' in $this",
                      timeout = timeout,
                      getter = { getAllTexts() },
                      checker = { it.text == text }
    )
  }

  /**
   * Waits for exactly one UiText element matching the specified predicate.
   *
   * @param message Optional custom message for the waiting operation
   * @param timeout The maximum time to wait for the element (default: DEFAULT_FIND_TIMEOUT)
   * @param predicate Function to test each UiText element
   * @return The UiText element that matches the predicate
   * @see waitOneText for waiting for specific text content
   * @see waitAnyTexts for waiting for multiple matching elements
   */
  fun waitOneText(message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT, predicate: (UiText) -> Boolean): UiText {
    return waitForOne(message = message ?: "Finding one text matching predicate in $this",
                      timeout = timeout,
                      getter = { getAllTexts() },
                      checker = { predicate(it) }
    )
  }

  /**
   * Waits until there are no UiText elements with the specified exact text.
   *
   * @param text The exact text that should not be present
   * @param message Optional custom message for the waiting operation
   * @param timeout The maximum time to wait for the condition (default: DEFAULT_FIND_TIMEOUT)
   * @see waitAnyTexts for waiting until text is present
   * @see hasText for non-blocking check if text exists
   */
  fun waitNoTexts(text: String, message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT) {
    waitFor(message = message ?: "Finding no texts '$text' in $this",
            timeout = timeout,
            getter = { getAllTexts() },
            checker = { it.none { it.text == text } }
    )
  }

  /**
   * Waits for a non-empty list of UiText elements containing the specified substring.
   *
   * @param text The substring to search for within text elements
   * @param message Optional custom message for the waiting operation
   * @param timeout The maximum time to wait for matching elements (default: DEFAULT_FIND_TIMEOUT)
   * @return List of UiText elements containing the specified substring
   * @see waitAnyTexts for waiting for exact text matches
   * @see waitOneContainsText for waiting for exactly one text containing substring
   * @see hasSubtext for non-blocking check if text containing substring exists
   */
  fun waitAnyTextsContains(text: String, message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT): List<UiText> {
    return waitAny(message = message ?: "Finding at least some texts and contains '$text' in $this",
                   timeout = timeout,
                   getter = { getAllTexts() },
                   checker = { it.text.contains(text) }
    )
  }

  /**
   * Checks if this component has any text that contains the specified substring.
   *
   * @param subtext The substring to search for within the component's text
   * @return true if the component has text containing the specified substring, false otherwise
   * @see hasText for checking exact text matches
   */
  fun hasSubtext(subtext: String): Boolean {
    return getAllTexts { it.text.contains(subtext) }.isNotEmpty()
  }

  /**
   * Waits until there is one UiText element that contains the specified substring.
   *
   * @param text The substring to search for within the component's text elements
   * @param message Optional custom message for waiting operation
   * @param ignoreCase Whether to ignore the case when searching for the substring (default: true)
   * @param timeout The maximum time to wait for the element (default: DEFAULT_FIND_TIMEOUT)
   * @return The UiText element that contains the specified substring
   * @see waitContainsText for waiting until all text contains the substring
   * @see hasSubtext for non-blocking check if the component has text containing a substring
   */
  fun waitOneContainsText(text: String, message: String? = null, ignoreCase: Boolean = true, timeout: Duration = DEFAULT_FIND_TIMEOUT): UiText {
    return waitForOne(message = message ?: "Finding the text containing '$text' in $this",
                      timeout = timeout,
                      getter = { getAllTexts() },
                      checker = { it.text.contains(other = text, ignoreCase = ignoreCase) }
    )
  }

  /**
   * Waits until all text in the component contains the specified substring.
   *
   * @param text The substring that all text must contain
   * @param message Optional custom message for waiting operation
   * @param ignoreCase Whether to ignore case when searching for the substring (default: true)
   * @param timeout The maximum time to wait for the condition (default: DEFAULT_FIND_TIMEOUT)
   * @see waitOneContainsText for waiting until one text element contains the substring
   * @see hasSubtext for non-blocking check if component has text containing a substring
   */
  fun waitContainsText(text: String, message: String? = null, ignoreCase: Boolean = true, timeout: Duration = DEFAULT_FIND_TIMEOUT) {
    waitFor(message = message ?: "Finding the text containing '$text' in $this",
            timeout = timeout,
            getter = { getAllTexts().asString() },
            checker = { it.contains(text, ignoreCase = ignoreCase) }
    )
  }

  /**
   * Checks if this component has the specified text.
   *
   * @param text The exact text to search for within the component
   * @return true if the component has the specified text, false otherwise
   * @see hasSubtext for checking if text contains a substring
   */
  fun hasText(text: String): Boolean {
    return getAllTexts().any { it.text == text }
  }


  /**
   * Retrieves all UI text elements in a vertically ordered manner.
   *
   * This method returns a list of lists of `UiText` objects. Each inner list represents a collection of UI text
   * elements that are located at the same vertical position on the screen. The outer list represents the entire
   * collection of vertically ordered UI text elements.
   *
   * The UI text elements are ordered horizontally within each vertical position, based on their `x` coordinates.
   *
   * @return A list of lists of `UiText` objects, representing all UI text elements ordered vertically.
   */
  fun getAllVerticallyOrderedUiText(): List<List<UiText>> =
    getAllTexts().groupBy { it.point.y }.toSortedMap()
      .values.map { it.sortedBy { it.point.x } }.toList()

  /**
   * Waits for exactly one match in the list of vertically ordered UI text elements.
   * That list is originally returned by `getAllVerticallyOrderedUiText`
   *
   * This method waits until there is exactly one UI text element in the list that matches the specified conditions.
   * The conditions can be either a full match of the text or a partial match if the `fullMatch` parameter is set to `false`.
   *
   * @param message An optional message to display when waiting for a match.
   * @param text The text to match against the UI text elements.
   * @param fullMatch Flag to indicate whether the match should be a full match or partial match.
   * @param timeout The maximum time to wait for the match.
   * @return A list of `UiText` objects that match the specified conditions.
   */
  fun waitOneMatchInVerticallyOrderedText(
    text: String,
    message: String? = null,
    fullMatch: Boolean = true,
    timeout: Duration = DEFAULT_FIND_TIMEOUT,
  ): List<UiText> =
    waitForOne(message ?: "Find '${text}'(fullMatch = $fullMatch) in vertically ordered text", timeout,
               getter = { getAllVerticallyOrderedUiText() },
               checker = {
                 if (fullMatch) {
                   text == it.asString()
                 }
                 else {
                   it.asString().contains(text)
                 }
               })

  fun present(): Boolean {
    val found = data.parentSearchContext.findAll(data.xpath)

    val present = if (found.size == 1) {
      true
    }
    else if (found.isEmpty()) {
      false
    }
    else {
      error("There are more than one $this in the hierarchy: $found.\n\t" +
            "Please use `xx()` if you need to check for more than one $this or clarify your search request.")
    }

    LOG.info("$this is present in hierarchy: $present")
    return present
  }

  fun notPresent(): Boolean {
    return !present()
  }

  fun isFocusOwner(): Boolean {
    return withComponent { it.isFocusOwner() }
  }

  fun isEnabled(): Boolean {
    return withComponent { it.isEnabled() }
  }

  fun hasVisibleComponent(component: UiComponent): Boolean {
    return hasComponent(component, Component::isShowing)
  }

  fun hasComponent(component: UiComponent, check: (Component) -> Boolean): Boolean {
    val components = searchContext.findAll(component.data.xpath)
    if (components.isEmpty()) return false
    return components.any { check(it) }
  }

  fun getParent(): UiComponent {
    val parent = withComponent { it.getParent() }

    return UiComponent(ComponentData(data.xpath + "/..", driver, searchService, robotProvider,
                                     data.parentSearchContext, parent))
  }

  fun findInParentContext(@Language("XPath") xpath: String): UiComponent? {
    val parentSearchContext = data.parentSearchContext
    val foundComponent = parentSearchContext.findAll(xpath).firstOrNull() ?: return null
    val componentPath = "${parentSearchContext.contextAsString}/$xpath"

    return UiComponent(ComponentData(componentPath, driver, searchService, robotProvider,
                                     parentSearchContext, foundComponent))
  }

  fun getColor(point: Point?, moveMouse: Boolean = true): Color {
    if (moveMouse) moveMouse(point)
    return withComponent {
      Color(robot.getColor(it, point).getRGB())
    }
  }

  // Mouse
  fun click(point: Point? = null, mouseButton: RemoteMouseButton = RemoteMouseButton.LEFT) {
    LOG.info("Click at $this${point?.let { ": $it" } ?: ""}")
    if (point != null) {
      withComponent { robot.click(it, point,  mouseButton) }
    }
    else {
      withComponent { robot.click(it, mouseButton) }
    }
  }

  fun strictClick(point: Point? = null, mouseButton: RemoteMouseButton = RemoteMouseButton.LEFT) {
    LOG.info("strictClick at $this${point?.let { ": $it" } ?: ""}")
    if (point != null) {
      withComponent { robot.strictClick(it, point) }
    }
    else {
      withComponent { robot.strictClick(it, null) }
    }
  }

  fun doubleClick(point: Point? = null) {
    LOG.info("Double click at $this${point?.let { ": $it" } ?: ""}")
    if (point != null) {
      withComponent { robot.click(it, point, RemoteMouseButton.LEFT, 2) }
    }
    else {
      withComponent { robot.doubleClick(it) }
    }
  }

  fun tripleClick(point: Point? = null) {
    LOG.info("Triple click at $this${point?.let { ": $it" } ?: ""}")
    if (point != null) {
      withComponent { robot.click(it, point, RemoteMouseButton.LEFT, 3) }
    }
    else {
      withComponent { robot.tripleClick(it) }
    }
  }
  fun rightClick(point: Point? = null) {
    LOG.info("Right click at $this${point?.let { ": $it" } ?: ""}")
    if (point != null) {
      withComponent { robot.click(it, point, RemoteMouseButton.RIGHT) }
    }
    else {
      withComponent { robot.click(it, RemoteMouseButton.RIGHT) }
    }
  }

  fun moveMouse(point: Point? = null) {
    LOG.info("Move mouse to $this${point?.let { ": $it" } ?: ""}")
    if (point != null) {
      withComponent { robot.moveMouse(it, point) }
    }
    else {
      withComponent { robot.moveMouse(it) }
    }
  }

  fun mousePressMoveRelease(from: Point, to: Point) {
    with(robot) {
      withComponent { moveMouse(it, from) }
      pressMouse(RemoteMouseButton.LEFT)

      withComponent { moveMouse(it, to) }
      releaseMouse(RemoteMouseButton.LEFT)
    }
  }

  fun mousePressAndRelease(from: Point, to: Point) {
    with(robot) {
      withComponent { moveMouse(it, from) }
      pressMouse(RemoteMouseButton.LEFT)
      releaseMouse(RemoteMouseButton.LEFT)
    }
  }

  fun press(point: Point? = null) {
    LOG.info("Press at $this${point?.let { ": $it" } ?: ""}")
    withComponent { robot.moveMouseAndPress(it, point) }
  }

  fun mouseRelease() {
    LOG.info("Release at $this")
    withComponent { robot.releaseMouse(RemoteMouseButton.LEFT) }
  }

  fun getBackgroundColor(): Color {
    return withComponent { Color(it.getBackground().getRGB()) }
  }

  fun getForegroundColor(): Color {
    return withComponent { Color(it.getForeground().getRGB()) }
  }

  fun getLocationOnScreen(): Point = component.getLocationOnScreen()
}