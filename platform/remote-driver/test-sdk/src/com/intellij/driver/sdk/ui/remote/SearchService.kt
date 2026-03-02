package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Driver
import com.intellij.driver.client.impl.RefWrapper.Companion.wrapRef
import com.intellij.driver.model.RdTarget
import com.intellij.driver.model.TextDataList
import com.intellij.driver.model.transport.Ref
import com.intellij.driver.sdk.remoteDev.BeControlComponentBase
import com.intellij.driver.sdk.remoteDev.getBackendRef
import com.intellij.driver.sdk.remoteDev.getFrontendRef
import com.intellij.driver.sdk.remoteDev.validateBeControlElement
import org.intellij.lang.annotations.Language
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

class SearchService(
  private val swingHierarchyService: SwingHierarchyService,
  private val driver: Driver
) {
  private val xPath = XPathFactory.newInstance().newXPath()

  fun findAllText(component: Component): TextDataList {
    return swingHierarchyService.findAllText(component)
  }

  fun findAll(@Language("xpath") xpath: String, component: Component? = null, onlyFrontend: Boolean = false): List<Component> {
    var matchingElements = getSwingHierarchyDOMAndFindMatchingElements(xpath, component, onlyFrontend)
    if (matchingElements.isNotEmpty() && matchingElements.all { isBeControl(it) && !validateBeControlElement(it) }) {
      matchingElements = getSwingHierarchyDOMAndFindMatchingElements(xpath, component, true)
    }
    return matchingElements.mapNotNull { reconstructComponent(it) }
  }

  private fun reconstructComponent(element: Element): Component? {
    if (isBeControl(element)) {
      if (!validateBeControlElement(element)) return null
      return reconstructBeControlComponent(element)
    }

    val ref = Ref(
      element.getAttribute("refId"),
      element.getAttribute("javaclass"),
      element.getAttribute("hashCode").toInt(),
      element.getAttribute("asString"),
      element.getAttribute("rdTarget").let { RdTarget.valueOf(it) }
    )

    return driver.cast(wrapRef(ref), Component::class)
  }

  private fun reconstructBeControlComponent(element: Element): Component {
    val frontendRef = getFrontendRef(element)
    val backendRef = getBackendRef(element)

    val frontendComponent = driver.cast(wrapRef(frontendRef), Component::class)
    val backendComponent = driver.cast(wrapRef(backendRef), Component::class)

    return BeControlComponentBase(driver, frontendComponent, backendComponent)
  }

  private fun isBeControl(element: Element) = element.hasAttribute("beControlDataId")

  private fun getSwingHierarchyDOMAndFindMatchingElements(xpath: String, component: Component? = null, onlyFrontend: Boolean = false): List<Element> {
    val html = swingHierarchyService.getSwingHierarchyAsDOM(component, onlyFrontend)

    val builder = createSecureDocumentBuilder()
    val model = builder.parse(html.byteInputStream())
    val result = xPath.compile(xpath).evaluate(model, XPathConstants.NODESET) as NodeList

    return (0 until result.length).mapNotNull { result.item(it) }.filterIsInstance<Element>()
  }

  @Suppress("HttpUrlsUsage")
  private fun createSecureDocumentBuilder(): DocumentBuilder {
    val factory = DocumentBuilderFactory.newDefaultInstance()
    factory.isValidating = false
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_DTD)
    setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA)
    factory.isXIncludeAware = false
    factory.isExpandEntityReferences = false
    return factory.newDocumentBuilder()
  }

  private fun setAttributeIfSupported(factory: DocumentBuilderFactory, attributeName: String) {
    try {
      factory.setAttribute(attributeName, "")
    }
    catch (_: IllegalArgumentException) {
      // Some XML parser implementations do not support JAXP accessExternal* attributes.
    }
  }
}
