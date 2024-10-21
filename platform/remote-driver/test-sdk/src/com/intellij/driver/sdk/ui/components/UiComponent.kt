package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Driver
import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.sdk.ui.DEFAULT_FIND_TIMEOUT
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.SearchContext
import com.intellij.driver.sdk.ui.UiText
import com.intellij.driver.sdk.ui.UiText.Companion.allText
import com.intellij.driver.sdk.ui.keyboard.WithKeyboard
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.Robot
import com.intellij.driver.sdk.ui.remote.RobotProvider
import com.intellij.driver.sdk.ui.remote.SearchService
import com.intellij.driver.sdk.waitAny
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForOne
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import java.awt.Color
import java.awt.IllegalComponentStateException
import java.awt.Point
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
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
  companion object {
    /**
     * Waits until the element specified is found within the parent search context. Doesn't guaranty visibility.
     *
     * @param timeout The maximum time to wait for the element to not be found. If not specified, the default timeout is used.
     */
    fun <T : UiComponent> T.waitFound(timeout: Duration? = DEFAULT_FIND_TIMEOUT): T {
      findThisComponent(timeout)
      return this
    }

    /**
     * Asserts that the current UI component is found. Doesn't check visibility.
     *
     * @return The current UI component.
     */
    fun <T : UiComponent> T.assertFound(): T {
      assert(present()) { "Component '$this' should be found" }
      return this
    }

    /**
     * Waits until the element specified is not found within the parent search context.
     *
     * @param timeout The maximum time to wait for the element to not be found. If not specified, the default timeout is used.
     */
    fun <T : UiComponent> T.waitNotFound(timeout: Duration? = DEFAULT_FIND_TIMEOUT) {
      waitFor(message = "No ${this::class.simpleName}[xpath=${data.xpath}] in ${data.parentSearchContext.contextAsString}",
              timeout = timeout ?: DEFAULT_FIND_TIMEOUT,
              interval = 1.seconds) {
        !present()
      }
    }

    /**
     * Asserts that the calling UiComponent is not found in the hierarchy.
     */
    fun <T : UiComponent> T.assertNotFound() {
      assert(!present()) { "Component '$this' should not be found" }
    }

    fun <T : UiComponent> T.waitIsFocusOwner(timeout: Duration = DEFAULT_FIND_TIMEOUT): T {
      waitFor("Component '$this' is focus owner", timeout = timeout) {
        component.isFocusOwner()
      }
      return this
    }

    fun <T : UiComponent> T.waitVisible(timeout: Duration = DEFAULT_FIND_TIMEOUT): T {
      waitFor("Component '$this' is visible", timeout = timeout) {
        component.isVisible()
      }
      return this
    }
  }

  override fun toString(): String {
    return this::class.simpleName + "[xpath=${data.xpath}]"
  }

  private var cachedComponent: Component? = null

  val component: Component
    get() = data.foundComponent
            ?: kotlin.runCatching { cachedComponent?.takeIf { it.isShowing() } }.getOrNull()
            ?: findThisComponent().apply { cachedComponent = this }

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

  /*
    Returns all UiText's matching predicate without waiting
   */
  fun getAllTexts(predicate: ((UiText) -> Boolean)? = null): List<UiText> {
    val allText = withComponent { searchService.findAllText(it) }
      .map { UiText(this, it) }
    return predicate?.let { allText.filter(predicate) } ?: allText
  }

  /**
   * Returns all UiText objects matching the specified text without waiting.
   * NB: In certain cases (e.g., console output) `getAllTest()` will not much `JEditorUiComponent.text`
   * and `getAllTest()` will return only currently visible text.
   *
   */
  fun getAllTexts(text: String): List<UiText> {
    return withComponent { searchService.findAllText(it) }
      .map { UiText(this, it) }
      .filter { it.text == text }
  }

  fun allTextAsString(): String {
    return getAllTexts().allText()
  }

  /**
   * Waits for a non-empty list of UiText's.
   */
  fun waitAnyTexts(message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT): List<UiText> {
    return waitAny(message = message ?: "Finding at least some texts in $this",
                   timeout = timeout,
                   getter = { getAllTexts() }
    )
  }

  /**
   * Waits for a non-empty list of UiText's matching predicate.
   */
  fun waitAnyTexts(message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT, predicate: (UiText) -> Boolean = { true }): List<UiText> {
    return waitAny(message = message ?: "Finding at least some texts and filter matching predicate in $this",
                   timeout = timeout,
                   getter = { getAllTexts() },
                   checker = { predicate(it) }
    )
  }

  /**
   * Waits for a non-empty list of UiText's with text '$text'.
   */
  fun waitAnyTexts(text: String, message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT): List<UiText> {
    return waitAny(message = message ?: "Finding at least some texts and filter '$text' in $this",
                   timeout = timeout,
                   getter = { getAllTexts() },
                   checker = { it.text == text }
    )
  }

  /**
   * Waits for one UiText with text '$text'.
   */
  fun waitOneText(text: String, message: String, timeout: Duration = DEFAULT_FIND_TIMEOUT): UiText {
    return waitForOne(message = message,
                      timeout = timeout,
                      getter = { getAllTexts() },
                      checker = { it.text == text }
    )
  }

  /**
   * Waits for one UiText with text '$text'.
   */
  fun waitOneText(text: String, timeout: Duration = DEFAULT_FIND_TIMEOUT): UiText {
    return waitOneText(message = "Finding text '$text' in $this", timeout = timeout, text = text)
  }

  /**
   * Waits for one UiText matching predicate.
   */
  fun waitOneText(message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT, predicate: (UiText) -> Boolean): UiText {
    return waitForOne(message = message ?: "Finding one text matching predicate in $this",
                      timeout = timeout,
                      getter = { getAllTexts() },
                      checker = { predicate(it) }
    )
  }

  /**
   * Waits until there is no UiText's.
   */
  fun waitNoTexts(message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT) {
    waitFor(message = message ?: "Finding no texts in $this",
            timeout = timeout,
            getter = { getAllTexts() },
            checker = { it.isEmpty() }
    )
  }

  /**
   * Waits until there is no UiText's with text '$text'.
   */
  fun waitNoTexts(text: String, message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT) {
    waitFor(message = message ?: "Finding no texts '$text' in $this",
            timeout = timeout,
            getter = { getAllTexts() },
            checker = { it.none { it.text == text } }
    )
  }

  /**
   * Waits for a non-empty list of UiText's with substring '$text'.
   */
  fun waitAnyTextsContains(text: String, message: String? = null, timeout: Duration = DEFAULT_FIND_TIMEOUT): List<UiText> {
    return waitAny(message = message ?: "Finding at least some texts and contains '$text' in $this",
                   timeout = timeout,
                   getter = { getAllTexts() },
                   checker = { it.text.contains(text) }
    )
  }

  /**
   * Waits until there is one UiText's with substring '$text'.
   */
  fun waitOneContainsText(text: String, message: String, ignoreCase: Boolean = true, timeout: Duration = DEFAULT_FIND_TIMEOUT): UiText {
    return waitForOne(message = message,
                      timeout = timeout,
                      getter = { getAllTexts() },
                      checker = { it.text.contains(text, ignoreCase = ignoreCase) }
    )
  }

  /**
   * Waits until there is one UiText's with substring '$text'.
   */
  fun waitOneContainsText(text: String, ignoreCase: Boolean = true, timeout: Duration = DEFAULT_FIND_TIMEOUT): UiText {
    return waitOneContainsText(message = "Finding the text containing '$text' in $this",
                               timeout = timeout, text = text, ignoreCase = ignoreCase)
  }

  /**
   * Waits until all text contains 'text'.
   */
  fun waitContainsText(text: String, message: String, ignoreCase: Boolean = true, timeout: Duration = DEFAULT_FIND_TIMEOUT) {
    waitFor(message = message,
            timeout = timeout,
            getter = { getAllTexts().allText() },
            checker = { it.contains(text, ignoreCase = ignoreCase) }
    )
  }

  /**
   * Waits until all text contains 'text'.
   */
  fun waitContainsText(text: String, ignoreCase: Boolean = true, timeout: Duration = DEFAULT_FIND_TIMEOUT) =
    waitContainsText(message = "Finding the text containing '$text' in $this", timeout = timeout, text = text, ignoreCase = ignoreCase)

  fun hasText(text: String): Boolean {
    return getAllTexts(text).isNotEmpty()
  }

  fun hasTextSequence(vararg texts: String, indexOffset: Int = 0): Boolean {
    require(indexOffset >= 0) { "Value must be non-negative" }
    val stringList = texts.toList()
    val uiTextList = getAllTexts()
    return stringList.indices.all { index ->
      val uiTextIndex = index + indexOffset
      uiTextIndex in uiTextList.indices && stringList[index] == uiTextList[uiTextIndex].text
    }
  }

  fun hasSubtext(subtext: String): Boolean {
    return getAllTexts { it.text.contains(subtext) }.isNotEmpty()
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
    message: String? = null,
    text: String,
    fullMatch: Boolean = true,
    timeout: Duration = DEFAULT_FIND_TIMEOUT,
  ): List<UiText> =
    waitForOne(message, timeout,
               getter = { getAllVerticallyOrderedUiText() },
               checker = {
                 if (fullMatch) {
                   text == it.allText()
                 }
                 else {
                   it.allText().contains(text)
                 }
               })

  fun waitOneMatchInVerticallyOrderedText(text: String, fullMatch: Boolean = true, timeout: Duration = DEFAULT_FIND_TIMEOUT): List<UiText> =
    waitOneMatchInVerticallyOrderedText("Find '${text}'(fullMatch = $fullMatch) in vertically ordered text", text, fullMatch, timeout = timeout)

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

  fun hasText(predicate: (UiText) -> Boolean): Boolean {
    return getAllTexts().any(predicate)
  }

  fun isVisible(): Boolean {
    return withComponent { it.isVisible() }
  }

  fun isEnabled(): Boolean {
    return withComponent { it.isEnabled() }
  }

  fun hasVisibleComponent(component: UiComponent): Boolean {
    val components = searchContext.findAll(component.data.xpath)
    if (components.isEmpty()) return false
    return components.any { it.isVisible() }
  }

  fun getParent(): UiComponent {
    val parent = withComponent { it.getParent() }

    return UiComponent(ComponentData(data.xpath + "/..", driver, searchService, robotProvider,
                                     data.parentSearchContext, parent))
  }

  fun getScreenshot(): BufferedImage {
    val screenshotPath = driver.takeScreenshot(this::class.simpleName)!!

    return withComponent { component ->
      val screenshot = ImageIO.read(File(screenshotPath))
        .getSubimage(component.getLocationOnScreen().x, component.getLocationOnScreen().y, component.width, component.height)

      if (SystemInfo.isWindows && this is DialogUiComponent) {
        screenshot.getSubimage(
          7,
          0,
          component.width - 14,
          component.height - 7
        )
      }
      else {
        screenshot
      }
    }
  }

  fun getFullScreenScreenshot(): BufferedImage {
    val screenshotPath = driver.takeScreenshot(this::class.simpleName)!!
    return ImageIO.read(File(screenshotPath))
  }

  // Mouse
  fun click(point: Point? = null, silent: Boolean = false) {
    if (point != null) {
      if (!silent) {
        LOG.info("Click at $this: $point")
      }
      withComponent { robot.click(it, point) }
    }
    else {
      if (!silent) {
        LOG.info("Click at '$this'")
      }
      withComponent { robot.click(it) }
    }
  }

  fun doubleClick(point: Point? = null, silent: Boolean = false) {
    if (point != null) {
      if (!silent) {
        LOG.info("Double click at $this: $point")
      }
      withComponent { robot.click(it, point, RemoteMouseButton.LEFT, 2) }
    }
    else {
      if (!silent) {
        LOG.info("Double click at '$this'")
      }
      withComponent { robot.doubleClick(it) }
    }
  }

  fun rightClick(point: Point? = null, silent: Boolean = false) {
    if (point != null) {
      if (!silent) {
        LOG.info("Right click at $this: $point")
      }
      withComponent { robot.click(it, point, RemoteMouseButton.RIGHT, 1) }
    }
    else {
      if (!silent) {
        LOG.info("Right click at $this")
      }
      withComponent { robot.rightClick(it) }
    }
  }

  fun click(button: RemoteMouseButton, count: Int) {
    LOG.info("Click with $button $count times at $this")
    withComponent { robot.click(it, button, count) }
  }

  fun moveMouse(point: Point? = null, silent: Boolean = false) {
    if (point != null) {
      if (!silent) {
        LOG.info("Move mouse to $this: $point")
      }
      withComponent { robot.moveMouse(it, point) }
    }
    else {
      if (!silent) {
        LOG.info("Move mouse to $this")
      }
      withComponent { robot.moveMouse(it) }
    }
  }

  fun hasFocus(): Boolean {
    return withComponent { it.isFocusOwner() }
  }

  fun mousePressMoveRelease(from: Point, to: Point) {
    with(robot) {
      withComponent { moveMouse(it, from) }
      pressMouse(RemoteMouseButton.LEFT)

      withComponent { moveMouse(it, to) }
      releaseMouse(RemoteMouseButton.LEFT)
    }
  }

  fun getBackgroundColor(): Color {
    return withComponent { Color(it.getBackground().getRGB()) }
  }

  fun getForegroundColor(): Color {
    return withComponent { Color(it.getForeground().getRGB()) }
  }
}