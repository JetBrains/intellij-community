package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForOne
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import java.awt.Point
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
## Use it to work with JCEF embedded browser.

The JCefUI provides some requirements:
- found component must have a parent with JBCefBrowser.instance clientProperty
- `ide.browser.jcef.jsQueryPoolSize=10000` - possible count of callback slots must be specified for running IDE

One JCefUI takes a one slot of the reserved callback slots. So if you create a lot of instances across the one browser,
the slots could be run out. It is preferable to create one JCefUI and then reuse it.

Example:
```kotlin
val driver = Driver.create(JmxHost(address = "127.0.0.1:7777"))

// find the component with embedded browser with the xpath in Idea UI Hierarchy
val embeddedBrowser = driver.ui.jcef("//div[@class='JBCefOsrComponent']")

// find an html element in the embedded browser by xpath in the html DOM
val htmlElement = embeddedBrowser.findElement("//a[contains(@href, 'jetbrains.com')]")

// click the link
htmlElement.click()
```
 */
fun Finder.jcef(@Language("xpath") xpath: String? = null, action: JCefUI.() -> Unit = {}): JCefUI {
  return x(xpath ?: "//div[contains(@class, 'Canvas') or contains(@class, 'JBCef')]", JCefUI::class.java).apply(action)
}

class JCefUI(data: ComponentData) : UiComponent(data) {
  private val jcefWorker by lazy {
    driver.new(JcefComponentWrapper::class, component).apply {
      waitFor("Document exists") { hasDocument() }
      runJs(initScript)
    }
  }

  private val json = Json {
    ignoreUnknownKeys = true
  }

  fun getYOffset(): Double {
    return callJs("window.pageYOffset.toString()").toDouble()
  }

  fun findElement(@Language("XPath") xpath: String, wait: Duration = 5.seconds): DomElement {
    return waitForOne("Find element by '$xpath' in the embedded browser($component)", wait,
            getter = { findElements(xpath) } )
  }

  fun findElements(@Language("xpath") xpath: String): List<DomElement> {
    val response = callJs("""window.elementFinder.findElements("${xpath.escapeXpath()}")""")
    return json.decodeFromString<ElementDataList>("""{ "elements": $response}""").elements.map {
      DomElement(this, it)
    }
  }

  fun findElementByText(text: String): DomElement {
    return findElement("//*[text()='$text']")
  }

  fun findElementsByText(text: String): List<DomElement> {
    return findElements("//*[text()='$text']")
  }

  fun findElementByContainsText(text: String): DomElement {
    return findElement("//*[contains(text(), '$text')]")
  }

  fun findElementsByContainsText(text: String): List<DomElement> {
    return findElements("//*[contains(text(), '$text')]")
  }

  fun exist(@Language("XPath") xpath: String): Boolean = findElements(xpath).isNotEmpty()

  fun scrollTo(@Language("XPath") xpath: String) {
    val result = callJs("""window.elementFinder.scrollByXpath("${xpath.escapeXpath()}")""")
    if (result != "success") {
      throw IllegalStateException("Failed to scroll to the element[$xpath]")
    }
  }

  fun hasDocument(): Boolean = jcefWorker.hasDocument()

  fun getUrl(): String =  jcefWorker.getUrl()

  fun getHtml(): String {
    return callJs("""document.documentElement.outerHTML""")
  }

  fun callJs(@Language("JavaScript") js: String, timeout: Long = 3000): String {
    return jcefWorker.callJs(js, timeout)
  }

  private fun String.escapeXpath() = replace("'", "\\x27").replace("\"", "\\x22")

  /**
   * JavaScript functions we need on the browser side.
   */
  @Language("JavaScript")
  private val initScript = """
    window.elementFinder = {};

    function Element(element) {
      this.tag = element.tagName
      this.html = element.outerHTML
      this.location = element.getBoundingClientRect()
      this.xpath = '/' + window.elementFinder.getPathTo(element).toLowerCase()
    };
    
    window.elementFinder.findElement = (xpath) => {
      return document.evaluate(xpath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue
    };
    
    window.elementFinder.scrollByXpath = (xpath) => {
      const element = window.elementFinder.findElement(xpath)
      element.scrollIntoView()
      return "success"
    };
    
    window.elementFinder.findElements = (xpath) => {
      const foundElements = [];
      const nodeSnapshot = document.evaluate(xpath, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null );
      for (let i = 0; i < nodeSnapshot.snapshotLength; i++ ) {
        foundElements.push( nodeSnapshot.snapshotItem(i) );
      }
      const result = foundElements.map((it) => new Element(it))
      return JSON.stringify(result)
    };
    
    window.elementFinder.getPathTo = (element) => {
      if (element.tagName.toLowerCase() === "html") {
        return element.tagName;
      }
      let ix = 0;
      const siblings = element.parentNode.childNodes;
      for (let i = 0; i < siblings.length; i++) {
        const sibling = siblings[i];
        if (sibling === element) {
          return window.elementFinder.getPathTo(element.parentNode) + '/' + element.tagName + '[' + (ix + 1) + ']';
        }
        if (sibling.nodeType === 1 && sibling.tagName === element.tagName) {
          ix++;
        }
      }
    };
  """

  @Serializable
  data class Location(val x: Double, val y: Double, val width: Double, val height: Double)

  @Serializable
  data class ElementData(val tag: String, val html: String, val location: Location, val xpath: String)

  @Serializable
  data class ElementDataList(val elements: List<ElementData>)
}

@Remote("com.jetbrains.performancePlugin.remotedriver.jcef.JcefComponentWrapper", plugin = REMOTE_ROBOT_MODULE_ID)
private interface JcefComponentWrapper {
  fun runJs(@Language("JavaScript") js: String)
  fun callJs(@Language("JavaScript") js: String, executeTimeoutMs: Long): String
  fun hasDocument(): Boolean
  fun getUrl(): String
}


class DomElement(val browser: JCefUI, private var elementData: JCefUI.ElementData) {
  private val x
    get() = elementData.location.x.roundToInt()

  private val y
    get() = elementData.location.y.roundToInt()

  private val width
    get() = elementData.location.width.roundToInt()

  private val height
    get() = elementData.location.height.roundToInt()

  private val centerX
    get() = x + width / 2

  private val centerY
    get() = y + height / 2

  private val xpath
    get() = elementData.xpath

  val html
    get() = elementData.html

  fun clickAtCenter() {
    scroll()
    browser.click(Point(centerX, centerY))
  }

  fun click() {
    scroll()
    browser.click(Point(x + height / 2, centerY))
  }

  fun scroll() {
    browser.scrollTo(xpath)
    elementData = browser.findElement(xpath).elementData
  }

  override fun toString(): String {
    return elementData.html
  }
}