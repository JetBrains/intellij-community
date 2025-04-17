// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("XmlReader")

package com.intellij.platform.plugins.parser.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.plugins.parser.impl.XmlReadUtils.getNullifiedAttributeValue
import com.intellij.platform.plugins.parser.impl.XmlReadUtils.getNullifiedContent
import com.intellij.platform.plugins.parser.impl.elements.*
import com.intellij.platform.plugins.parser.impl.elements.ActionElement.*
import com.intellij.util.Java11Shim
import com.intellij.util.xml.dom.XmlInterner
import com.intellij.util.xml.dom.createNonCoalescingXmlStreamReader
import com.intellij.util.xml.dom.readXmlAsModel
import org.codehaus.stax2.XMLStreamReader2
import org.codehaus.stax2.typed.TypedXMLStreamException
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.InputStream
import java.text.ParseException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader
import javax.xml.stream.events.XMLEvent
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRule as LoadingRule

internal fun readModuleDescriptor(
  consumer: PluginDescriptorFromXmlStreamConsumer,
  reader: XMLStreamReader2,
) {
  try {
    if (reader.eventType != XMLStreamConstants.START_DOCUMENT) {
      throw XMLStreamException("State ${XMLStreamConstants.START_DOCUMENT} is expected, but current state is ${getEventTypeString(reader.eventType)}", reader.location)
    }

    @Suppress("ControlFlowWithEmptyBody")
    while (reader.next() != XMLStreamConstants.START_ELEMENT) ;
    if (!reader.isStartElement) {
      return
    }

    readRootAttributes(reader, consumer.getBuilder())

    reader.consumeChildElements { localName ->
      readRootElementChild(
        consumer = consumer,
        reader = reader,
        localName = localName,
      )
      assert(reader.isEndElement)
    }
  }
  finally {
    reader.closeCompletely()
  }
}

@Throws(XMLStreamException::class)
fun readBasicDescriptorData(input: InputStream): RawPluginDescriptor? {
  val reader = createNonCoalescingXmlStreamReader(input = input, locationSource = null)
  try {
    if (reader.eventType != XMLStreamConstants.START_DOCUMENT) {
      throw XMLStreamException("Expected: ${XMLStreamConstants.START_DOCUMENT}, got: ${getEventTypeString(reader.eventType)}", reader.location)
    }

    @Suppress("ControlFlowWithEmptyBody")
    while (reader.next() != XMLStreamConstants.START_ELEMENT) ;
    if (!reader.isStartElement) {
      return null
    }

    val builder = PluginDescriptorBuilder.builder()
    reader.consumeChildElements { localName ->
      when (localName) {
        PluginXmlConst.ID_ELEM -> builder.id = getNullifiedContent(reader)
        PluginXmlConst.NAME_ELEM -> builder.name = getNullifiedContent(reader)
        PluginXmlConst.VERSION_ELEM -> builder.version = getNullifiedContent(reader)
        PluginXmlConst.DESCRIPTION_ELEM -> builder.description = getNullifiedContent(reader)
        PluginXmlConst.IDEA_VERSION_ELEM -> readIdeaVersion(reader, builder)
        PluginXmlConst.PRODUCT_DESCRIPTOR_ELEM -> readProduct(reader, builder)
        else -> reader.skipElement()
      }
      assert(reader.isEndElement)
    }
    return builder.build()
  }
  finally {
    reader.close()
  }
}

private fun readRootAttributes(reader: XMLStreamReader2, builder: PluginDescriptorBuilder) {
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      PluginXmlConst.PLUGIN_PACKAGE_ATTR -> builder.`package` = getNullifiedAttributeValue(reader, i)
      PluginXmlConst.PLUGIN_URL_ATTR -> builder.url = getNullifiedAttributeValue(reader, i)
      PluginXmlConst.PLUGIN_USE_IDEA_CLASSLOADER_ATTR -> builder.isUseIdeaClassLoader = reader.getAttributeAsBoolean(i)
      PluginXmlConst.PLUGIN_ALLOW_BUNDLED_UPDATE_ATTR -> builder.isBundledUpdateAllowed = reader.getAttributeAsBoolean(i)
      PluginXmlConst.PLUGIN_IMPLEMENTATION_DETAIL_ATTR -> builder.isImplementationDetail = reader.getAttributeAsBoolean(i)
      PluginXmlConst.PLUGIN_REQUIRE_RESTART_ATTR -> builder.isRestartRequired = reader.getAttributeAsBoolean(i)
      PluginXmlConst.PLUGIN_DEPENDENT_ON_CORE_ATTR -> builder.isIndependentFromCoreClassLoader = !reader.getAttributeAsBoolean(i)
      PluginXmlConst.PLUGIN_IS_SEPARATE_JAR_ATTR -> builder.isSeparateJar = reader.getAttributeAsBoolean(i)
      PluginXmlConst.PLUGIN_VERSION_ATTR -> {
        // internalVersionString - why it is not used, but just checked?
        getNullifiedAttributeValue(reader, i)?.let {
          try {
            it.toInt()
          }
          catch (e: NumberFormatException) {
            LOG.error("Cannot parse version: $it'", e)
          }
        }
      }
    }
  }
}

/**
 * Keep in sync with KotlinPluginUtil.KNOWN_KOTLIN_PLUGIN_IDS
 */
private val KNOWN_KOTLIN_PLUGIN_IDS = Java11Shim.INSTANCE.copyOf(listOf(
  "org.jetbrains.kotlin",
  "com.intellij.appcode.kmm",
  "org.jetbrains.kotlin.native.appcode"
))

fun isKotlinPlugin(pluginId: String): Boolean =
  pluginId in KNOWN_KOTLIN_PLUGIN_IDS

private val K2_ALLOWED_PLUGIN_IDS = Java11Shim.INSTANCE.copyOf(KNOWN_KOTLIN_PLUGIN_IDS + listOf(
  "org.jetbrains.android",
  "androidx.compose.plugins.idea",
  "com.jetbrains.kmm",
  "com.jetbrains.kotlin.ocswift",
  "com.jetbrains.rider.android"
))

private fun readRootElementChild(
  consumer: PluginDescriptorFromXmlStreamConsumer,
  reader: XMLStreamReader2,
  localName: String
) {
  val builder = consumer.getBuilder()
  val readContext = consumer.readContext
  when (localName) {
    PluginXmlConst.ID_ELEM -> {
      when {
        builder.id == null -> {
          builder.id = getNullifiedContent(reader)
        }
        !KNOWN_KOTLIN_PLUGIN_IDS.contains(builder.id) && builder.id != "com.intellij" -> {
          // no warning and no redefinition for kotlin - compiler.xml is a known issue
          LOG.warn("id redefinition (${reader.locationInfo.location})")
          builder.id = getNullifiedContent(reader)
        }
        else -> {
          reader.skipElement()
        }
      }
    }
    PluginXmlConst.NAME_ELEM -> builder.name = getNullifiedContent(
      reader)
    PluginXmlConst.CATEGORY_ELEM -> builder.category = getNullifiedContent(
      reader)
    PluginXmlConst.VERSION_ELEM -> {
      // kotlin includes compiler.xml that due to some reasons duplicates a version
      if (builder.version == null || !KNOWN_KOTLIN_PLUGIN_IDS.contains(builder.id)) {
        builder.version = getNullifiedContent(reader)
      }
      else {
        reader.skipElement()
      }
    }
    PluginXmlConst.DESCRIPTION_ELEM -> builder.description = getNullifiedContent(reader)
    PluginXmlConst.CHANGE_NOTES_ELEM -> builder.changeNotes = getNullifiedContent(reader)
    PluginXmlConst.RESOURCE_BUNDLE_ELEM -> builder.resourceBundleBaseName = getNullifiedContent(reader)
    PluginXmlConst.PRODUCT_DESCRIPTOR_ELEM -> readProduct(reader, builder)
    PluginXmlConst.MODULE_ELEM -> {
      XmlReadUtils.findAttributeValue(reader, PluginXmlConst.MODULE_VALUE_ATTR)?.let { moduleName ->
        builder.addPluginAlias(moduleName)
      }
      reader.skipElement()
    }
    PluginXmlConst.IDEA_VERSION_ELEM -> readIdeaVersion(reader, builder)
    PluginXmlConst.VENDOR_ELEM -> {
      for (i in 0 until reader.attributeCount) {
        when (reader.getAttributeLocalName(i)) {
          PluginXmlConst.VENDOR_EMAIL_ATTR -> builder.vendorEmail = getNullifiedAttributeValue(reader, i)
          PluginXmlConst.VENDOR_URL_ATTR -> builder.vendorUrl = getNullifiedAttributeValue(reader, i)
        }
      }
      builder.vendor = getNullifiedContent(reader)
    }
    PluginXmlConst.INCOMPATIBLE_WITH_ELEM -> {
      getNullifiedContent(reader)?.let { id ->
        builder.addIncompatibleWith(id)
      }
    }
    PluginXmlConst.APPLICATION_COMPONENTS_ELEM -> readComponents(reader, builder.appContainerBuilder)
    PluginXmlConst.PROJECT_COMPONENTS_ELEM -> readComponents(reader, builder.projectContainerBuilder)
    PluginXmlConst.MODULE_COMPONENTS_ELEM -> readComponents(reader, builder.moduleContainerBuilder)
    PluginXmlConst.APPLICATION_LISTENERS_ELEM -> readListeners(reader, builder.appContainerBuilder)
    PluginXmlConst.PROJECT_LISTENERS_ELEM -> readListeners(reader, builder.projectContainerBuilder)
    PluginXmlConst.EXTENSIONS_ELEM -> readExtensions(reader, builder, readContext.interner)
    PluginXmlConst.EXTENSION_POINTS_ELEM -> readExtensionPoints(
      consumer = consumer,
      reader = reader,
    )
    PluginXmlConst.CONTENT_ELEM -> readContent(reader, builder, readContext)
    PluginXmlConst.DEPENDENCIES_ELEM-> readDependencies(reader, builder, readContext.interner)
    PluginXmlConst.DEPENDS_ELEM -> readOldDepends(reader, builder)
    PluginXmlConst.ACTIONS_ELEM -> readActions(builder, reader, readContext)
    PluginXmlConst.INCLUDE_ELEM -> readInclude(
      consumer = consumer,
      reader = reader,
      allowedPointer = PluginXmlConst.DEFAULT_XPOINTER_VALUE,
    )
    PluginXmlConst.HELPSET_ELEM -> {
      // deprecated and not used element
      reader.skipElement()
    }
    PluginXmlConst.LOCALE_ELEM -> {
      // not used in descriptor
      reader.skipElement()
    }
    else -> {
      LOG.error("Unknown element: $localName")
      reader.skipElement()
    }
  }

  if (!reader.isEndElement) {
    throw XMLStreamException("Unexpected state (expected=END_ELEMENT, actual=${getEventTypeString(reader.eventType)}, lastProcessedElement=$localName)", reader.location)
  }
}

private fun readIdeaVersion(reader: XMLStreamReader2, builder: PluginDescriptorBuilder) {
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      PluginXmlConst.IDEA_VERSION_SINCE_ATTR -> builder.sinceBuild = getNullifiedAttributeValue(reader, i)
      PluginXmlConst.IDEA_VERSION_UNTIL_ATTR -> builder.untilBuild = getNullifiedAttributeValue(reader, i)
    }
  }
  reader.skipElement()
}

private val actionNameToEnum = run {
  val entries = ActionElementName.entries
  entries.associateByTo(HashMap<String, ActionElementName>(entries.size), ActionElementName::name)
}

private fun readActions(builder: PluginDescriptorBuilder, reader: XMLStreamReader2, readContext: ReadModuleContext) {
  val resourceBundle = XmlReadUtils.findAttributeValue(reader, PluginXmlConst.ACTIONS_RESOURCE_BUNDLE_ATTR)
  reader.consumeChildElements { elementName ->
    if (checkXInclude(elementName, reader)) {
      return@consumeChildElements
    }

    val name = actionNameToEnum[elementName]
    if (name == null) {
      LOG.error("Unexpected name of element: $elementName at ${reader.location}")
      reader.skipElement()
      return@consumeChildElements
    }

    val element = readXmlAsModel(reader, elementName, readContext.interner)

    val attributes = element.attributes
    when (name) {
      ActionElementName.action -> {
        val className = attributes["class"]
        if (className.isNullOrEmpty()) {
          LOG.error("action element should have specified \"class\" attribute at ${reader.location}")
          reader.skipElement()
          return@consumeChildElements
        }
        builder.addAction(ActionDescriptorAction(className, isInternal = attributes["internal"].toBoolean(), element, resourceBundle))
      }
      ActionElementName.group -> {
        var className = attributes["class"]
        if (className.isNullOrEmpty()) {
          className = if (attributes["compact"] == "true") "com.intellij.openapi.actionSystem.DefaultCompactActionGroup" else null
        }
        val id = attributes["id"]
        if (id != null && id.isEmpty()) {
          LOG.error("ID of the group cannot be an empty string at ${reader.location}")
          reader.skipElement()
          return@consumeChildElements
        }
        builder.addAction(ActionElementGroup(className, id, element, resourceBundle))
      }
      else -> {
        builder.addAction(ActionElementMisc(name, element, resourceBundle))
      }
    }
  }
}

private fun readOldDepends(reader: XMLStreamReader2, builder: PluginDescriptorBuilder) {
  var isOptional = false
  var configFile: String? = null
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      PluginXmlConst.DEPENDS_OPTIONAL_ATTR -> isOptional = reader.getAttributeAsBoolean(i)
      PluginXmlConst.DEPENDS_CONFIG_FILE_ATTR -> configFile = reader.getAttributeValue(i)
    }
  }
  val dependencyIdString = getNullifiedContent(reader) ?: return
  builder.addDepends(DependsElement(pluginId = dependencyIdString, configFile = configFile, isOptional = isOptional))
}

private fun readExtensions(reader: XMLStreamReader2, builder: PluginDescriptorBuilder, interner: XmlInterner) {
  val ns = XmlReadUtils.findAttributeValue(reader, PluginXmlConst.EXTENSIONS_DEFAULT_EXTENSION_NS_ATTR)
  reader.consumeChildElements { elementName ->
    if (checkXInclude(elementName, reader)) {
      return@consumeChildElements
    }

    var implementation: String? = null
    var os: OS? = null
    var qualifiedExtensionPointName: String? = null
    var order: String? = null
    var orderId: String? = null

    var hasExtraAttributes = false
    for (i in 0 until reader.attributeCount) {
      when (reader.getAttributeLocalName(i)) {
        PluginXmlConst.EXTENSION_IMPLEMENTATION_ATTR -> implementation = reader.getAttributeValue(i)
        PluginXmlConst.EXTENSION_IMPLEMENTATION_CLASS_ATTR -> implementation = reader.getAttributeValue(i)  // deprecated attribute
        PluginXmlConst.EXTENSION_OS_ATTR -> os = readOSValue(reader.getAttributeValue(i))
        PluginXmlConst.EXTENSION_ORDER_ID_ATTR -> orderId = getNullifiedAttributeValue(reader, i)
        PluginXmlConst.EXTENSION_ORDER_ATTR -> order = reader.getAttributeValue(i)
        PluginXmlConst.EXTENSION_POINT_ATTR -> qualifiedExtensionPointName = getNullifiedAttributeValue(reader, i)
        else -> hasExtraAttributes = true
      }
    }

    if (qualifiedExtensionPointName == null) {
      qualifiedExtensionPointName = interner.name("${ns ?: reader.namespaceURI}.${elementName}")
    }

    val containerBuilder: ScopedElementsContainerBuilder
    when (qualifiedExtensionPointName) {
      PluginXmlConst.FQN_APPLICATION_SERVICE -> containerBuilder = builder.appContainerBuilder
      PluginXmlConst.FQN_PROJECT_SERVICE -> containerBuilder = builder.projectContainerBuilder
      PluginXmlConst.FQN_MODULE_SERVICE -> containerBuilder = builder.moduleContainerBuilder
      else -> {
        // bean EP can use id / implementation attributes for own bean class
        // - that's why we have to create XmlElement even if all attributes are common
        val element = if (qualifiedExtensionPointName == PluginXmlConst.FQN_POST_STARTUP_ACTIVITY) {
          reader.skipElement()
          null
        }
        else {
          readXmlAsModel(reader, rootName = null, interner).takeIf {
            !it.children.isEmpty() || !it.attributes.keys.isEmpty()
          }
        }

        val extensionElement = ExtensionElement(
          implementation = implementation,
          os = os,
          orderId = orderId,
          order = order,
          element = element,
          hasExtraAttributes = hasExtraAttributes,
        )
        builder.addExtension(qualifiedExtensionPointName, extensionElement)

        assert(reader.isEndElement)
        return@consumeChildElements
      }
    }

    containerBuilder.addService(readServiceElement(reader, os))
    reader.skipElement()
  }
}

private fun checkXInclude(elementName: String, reader: XMLStreamReader2): Boolean {
  if (elementName == PluginXmlConst.INCLUDE_ELEM && reader.namespaceURI == PluginXmlConst.XINCLUDE_NAMESPACE_URI) {
    LOG.error("`include` is supported only on a root level (${reader.location})")
    reader.skipElement()
    return true
  }
  return false
}

@Suppress("DuplicatedCode")
private fun readExtensionPoints(
  consumer: PluginDescriptorFromXmlStreamConsumer,
  reader: XMLStreamReader2,
) {
  val builder = consumer.getBuilder()
  reader.consumeChildElements { elementName ->
    if (elementName != PluginXmlConst.EXTENSION_POINT_ELEM) {
      if (elementName == PluginXmlConst.INCLUDE_ELEM && reader.namespaceURI == PluginXmlConst.XINCLUDE_NAMESPACE_URI) {
        val partial = PluginDescriptorFromXmlStreamConsumer.withIncludeBase(
          consumer.readContext,
          consumer.xIncludeLoader,
          consumer.includeBase,
        )
        readInclude(
          partial,
          reader,
          allowedPointer = PluginXmlConst.EXTENSION_POINTS_XINCLUDE_VALUE
        )
        LOG.warn("`include` is supported only on a root level (${reader.location})")
        // TODO: note, this function does not copy misc extensions (bug?)
        copyExtensionPoints(partial.getBuilder(), builder) { it.appContainerBuilder }
        copyExtensionPoints(partial.getBuilder(), builder) { it.projectContainerBuilder }
        copyExtensionPoints(partial.getBuilder(), builder) { it.moduleContainerBuilder }
      }
      else {
        LOG.error("Unknown element: $elementName (${reader.location})")
        reader.skipElement()
      }
      return@consumeChildElements
    }

    var area: String? = null
    var qualifiedName: String? = null
    var name: String? = null
    var beanClass: String? = null
    var `interface`: String? = null
    var isDynamic = false
    var hasAttributes = false
    for (i in 0 until reader.attributeCount) {
      when (reader.getAttributeLocalName(i)) {
        PluginXmlConst.EXTENSION_POINT_AREA_ATTR -> area = getNullifiedAttributeValue(
          reader, i)
        PluginXmlConst.EXTENSION_POINT_QUALIFIED_NAME_ATTR -> qualifiedName = reader.getAttributeValue(i)
        PluginXmlConst.EXTENSION_POINT_NAME_ATTR -> name = getNullifiedAttributeValue(
          reader, i)
        PluginXmlConst.EXTENSION_POINT_BEAN_CLASS_ATTR -> beanClass = getNullifiedAttributeValue(
          reader, i)
        PluginXmlConst.EXTENSION_POINT_INTERFACE_ATTR -> `interface` = getNullifiedAttributeValue(
          reader, i)
        PluginXmlConst.EXTENSION_POINT_DYNAMIC_ATTR -> isDynamic = reader.getAttributeAsBoolean(i)
        PluginXmlConst.EXTENSION_POINT_HAS_ATTRIBUTES_ATTR -> hasAttributes = reader.getAttributeAsBoolean(i)
      }
    }

    if (qualifiedName == null && name == null) {
      throw RuntimeException("`name` attribute not specified for extension point at ${reader.location}")
    }
    if (beanClass == null && `interface` == null) {
      throw RuntimeException("Neither beanClass nor interface attribute is specified for extension point at ${reader.location}")
    }
    if (beanClass != null && `interface` != null) {
      throw RuntimeException("Both beanClass and interface attributes are specified for extension point at ${reader.location}")
    }

    reader.skipElement()

    val containerBuilder = when (area) {
      null -> builder.appContainerBuilder
      PluginXmlConst.EXTENSION_POINT_AREA_IDEA_PROJECT_VALUE -> builder.projectContainerBuilder
      PluginXmlConst.EXTENSION_POINT_AREA_IDEA_MODULE_VALUE -> builder.moduleContainerBuilder
      else -> {
        LOG.error("Unknown area: $area")
        return@consumeChildElements
      }
    }

    containerBuilder.addExtensionPoint(ExtensionPointElement(
      name = name,
      qualifiedName = qualifiedName,
      `interface` = `interface`,
      beanClass = beanClass,
      hasAttributes = hasAttributes,
      isDynamic = isDynamic,
    ))
  }
}

private inline fun copyExtensionPoints(from: PluginDescriptorBuilder, to: PluginDescriptorBuilder, crossinline extractor: (PluginDescriptorBuilder) -> ScopedElementsContainerBuilder) {
  extractor(from).removeAllExtensionPoints().takeIf { it.isNotEmpty() }?.let {
    val toContainer = extractor(to)
    toContainer.addExtensionPoints(it)
  }
}

@Suppress("DuplicatedCode")
private fun readServiceElement(reader: XMLStreamReader2, os: OS?): ServiceElement {
  var serviceInterface: String? = null
  var serviceImplementation: String? = null
  var testServiceImplementation: String? = null
  var headlessImplementation: String? = null
  var configurationSchemaKey: String? = null
  var overrides = false
  var preload = PreloadMode.FALSE
  var client: ClientKind? = null
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      PluginXmlConst.SERVICE_EP_SERVICE_INTERFACE_ATTR -> serviceInterface = getNullifiedAttributeValue(
        reader, i)
      PluginXmlConst.SERVICE_EP_SERVICE_IMPLEMENTATION_ATTR-> serviceImplementation = getNullifiedAttributeValue(
        reader, i)
      PluginXmlConst.SERVICE_EP_TEST_SERVICE_IMPLEMENTATION_ATTR -> testServiceImplementation = getNullifiedAttributeValue(
        reader, i)
      PluginXmlConst.SERVICE_EP_HEADLESS_IMPLEMENTATION_ATTR -> headlessImplementation = getNullifiedAttributeValue(
        reader, i)
      PluginXmlConst.SERVICE_EP_CONFIGURATION_SCHEMA_KEY_ATTR -> configurationSchemaKey = reader.getAttributeValue(i)
      PluginXmlConst.SERVICE_EP_OVERRIDES_ATTR -> overrides = reader.getAttributeAsBoolean(i)
      PluginXmlConst.SERVICE_EP_PRELOAD_ATTR -> {
        when (reader.getAttributeValue(i)) {
          PluginXmlConst.SERVICE_EP_PRELOAD_TRUE_VALUE -> preload = PreloadMode.TRUE
          PluginXmlConst.SERVICE_EP_PRELOAD_AWAIT_VALUE -> preload = PreloadMode.AWAIT
          PluginXmlConst.SERVICE_EP_PRELOAD_NOT_HEADLESS_VALUE -> preload = PreloadMode.NOT_HEADLESS
          PluginXmlConst.SERVICE_EP_PRELOAD_NOT_LIGHT_EDIT_VALUE -> preload = PreloadMode.NOT_LIGHT_EDIT
          else -> LOG.error("Unknown preload mode value ${reader.getAttributeValue(i)} at ${reader.location}")
        }
      }
      PluginXmlConst.SERVICE_EP_CLIENT_ATTR -> {
        @Suppress("DEPRECATION")
        when (reader.getAttributeValue(i)) {
          PluginXmlConst.SERVICE_EP_CLIENT_LOCAL_VALUE -> client = ClientKind.LOCAL
          PluginXmlConst.SERVICE_EP_CLIENT_GUEST_VALUE -> client = ClientKind.GUEST
          PluginXmlConst.SERVICE_EP_CLIENT_CONTROLLER_VALUE -> client = ClientKind.CONTROLLER
          PluginXmlConst.SERVICE_EP_CLIENT_OWNER_VALUE -> client = ClientKind.OWNER
          PluginXmlConst.SERVICE_EP_CLIENT_REMOTE_VALUE -> client = ClientKind.REMOTE
          PluginXmlConst.SERVICE_EP_CLIENT_FRONTEND_VALUE -> client = ClientKind.FRONTEND
          PluginXmlConst.SERVICE_EP_CLIENT_ALL_VALUE -> client = ClientKind.ALL
          else -> LOG.error("Unknown client value: ${reader.getAttributeValue(i)} at ${reader.location}")
        }
      }
    }
  }
  return ServiceElement(
    serviceInterface = serviceInterface,
    serviceImplementation = serviceImplementation,
    testServiceImplementation = testServiceImplementation,
    headlessImplementation = headlessImplementation,
    overrides = overrides,
    configurationSchemaKey = configurationSchemaKey,
    preload = preload,
    client = client,
    os = os
  )
}

private fun readProduct(reader: XMLStreamReader2, builder: PluginDescriptorBuilder) {
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      PluginXmlConst.PRODUCT_DESCRIPTOR_CODE_ATTR -> builder.productCode = getNullifiedAttributeValue(
        reader, i)
      PluginXmlConst.PRODUCT_DESCRIPTOR_RELEASE_DATE_ATTR -> builder.releaseDate = parseReleaseDate(reader.getAttributeValue(i))
      PluginXmlConst.PRODUCT_DESCRIPTOR_RELEASE_VERSION_ATTR -> {
        try {
          builder.releaseVersion = reader.getAttributeAsInt(i)
        }
        catch (_: TypedXMLStreamException) {
          builder.releaseVersion = 0
        }
      }
      PluginXmlConst.PRODUCT_DESCRIPTOR_OPTIONAL_ATTR -> builder.isLicenseOptional = reader.getAttributeAsBoolean(i)
    }
  }
  reader.skipElement()
}

private fun readComponents(reader: XMLStreamReader2, containerDescriptor: ScopedElementsContainerBuilder) {
  reader.consumeChildElements(PluginXmlConst.COMPONENT_ELEM) {
    var isApplicableForDefaultProject = false
    var interfaceClass: String? = null
    var implementationClass: String? = null
    var headlessImplementationClass: String? = null
    var os: OS? = null
    var overrides = false
    var options: MutableMap<String, String>? = null

    reader.consumeChildElements { elementName ->
      when (elementName) {
        PluginXmlConst.COMPONENT_SKIP_FOR_DEFAULT_PROJECT_ELEM -> {
          val value = reader.elementText
          if (!value.isEmpty() && value.equals("false", ignoreCase = true)) {
            isApplicableForDefaultProject = true
          }
        }
        PluginXmlConst.COMPONENT_LOAD_FOR_DEFAULT_PROJECT_ELEM -> {
          val value = reader.elementText
          isApplicableForDefaultProject = value.isEmpty() || value.equals("true", ignoreCase = true)
        }
        PluginXmlConst.COMPONENT_INTERFACE_CLASS_ELEM -> interfaceClass = getNullifiedContent(reader)
        // empty value must be supported
        PluginXmlConst.COMPONENT_IMPLEMENTATION_CLASS_ELEM -> implementationClass = getNullifiedContent(reader)
        // empty value must be supported
        PluginXmlConst.COMPONENT_HEADLESS_IMPLEMENTATION_CLASS_ELEM -> headlessImplementationClass = reader.elementText
        PluginXmlConst.COMPONENT_OPTION_ELEM -> {
          var name: String? = null
          var value: String? = null
          for (i in 0 until reader.attributeCount) {
            when (reader.getAttributeLocalName(i)) {
              PluginXmlConst.COMPONENT_OPTION_NAME_ATTR -> name = getNullifiedAttributeValue(reader, i)
              PluginXmlConst.COMPONENT_OPTION_VALUE_ATTR -> value = getNullifiedAttributeValue(reader, i)
            }
          }

          reader.skipElement()

          if (name != null && value != null) {
            when {
              name == PluginXmlConst.COMPONENT_OPTION_NAME_OS_VALUE -> os = readOSValue(value)
              name == PluginXmlConst.COMPONENT_OPTION_NAME_OVERRIDES_VALUE -> overrides = value.toBoolean()
              options == null -> {
                options = Collections.singletonMap(name, value)
              }
              else -> {
                if (options!!.size == 1) {
                  options = HashMap(options)
                }
                options.put(name, value)
              }
            }
          }
        }
        else -> reader.skipElement()
      }
      assert(reader.isEndElement)
    }
    assert(reader.isEndElement)

    containerDescriptor.addComponent(ComponentElement(
      interfaceClass = interfaceClass,
      implementationClass = implementationClass,
      headlessImplementationClass = headlessImplementationClass,
      loadForDefaultProject = isApplicableForDefaultProject,
      os = os,
      overrides = overrides,
      options = options ?: Java11Shim.INSTANCE.mapOf(),
    ))
  }
}

private fun readContent(reader: XMLStreamReader2, builder: PluginDescriptorBuilder, readContext: ReadModuleContext) {
  reader.consumeChildElements { elementName ->
    if (elementName != PluginXmlConst.CONTENT_MODULE_ELEM) {
      reader.skipElement()
      throw RuntimeException("Unknown content item type: $elementName")
    }

    var name: String? = null
    var loadingRule = LoadingRule.OPTIONAL
    var os: OS? = null
    for (i in 0 until reader.attributeCount) {
      when (reader.getAttributeLocalName(i)) {
        PluginXmlConst.CONTENT_MODULE_NAME_ATTR -> name = readContext.interner.name(reader.getAttributeValue(i))
        PluginXmlConst.CONTENT_MODULE_LOADING_ATTR -> {
          val loading = reader.getAttributeValue(i)
          loadingRule = when (loading) {
            PluginXmlConst.CONTENT_MODULE_LOADING_OPTIONAL_VALUE -> LoadingRule.OPTIONAL
            PluginXmlConst.CONTENT_MODULE_LOADING_REQUIRED_VALUE -> LoadingRule.REQUIRED
            PluginXmlConst.CONTENT_MODULE_LOADING_EMBEDDED_VALUE -> LoadingRule.EMBEDDED
            PluginXmlConst.CONTENT_MODULE_LOADING_ON_DEMAND_VALUE -> LoadingRule.ON_DEMAND
            else -> error("Unexpected value '$loading' of 'loading' attribute at ${reader.location}")
          }
        }
        PluginXmlConst.CONTENT_MODULE_OS_ATTR -> os = readOSValue(reader.getAttributeValue(i))
      }
    }

    if (name.isNullOrEmpty()) {
      throw RuntimeException("Name is not specified at ${reader.location}")
    }

    val isEndElement = reader.next() == XMLStreamConstants.END_ELEMENT
    if (isEndElement) {
      if (os == null || readContext.elementOsFilter(os)) {
        builder.addContentModule(ContentElement.Module(name = name, loadingRule = loadingRule, embeddedDescriptorContent = null))
      }
    }
    else {
      if (os == null || readContext.elementOsFilter(os)) {
        val fromIndex = reader.textStart
        val toIndex = fromIndex + reader.textLength
        val length = toIndex - fromIndex
        val descriptorContent = if (length == 0) null else reader.textCharacters.copyOfRange(fromIndex, toIndex)
        builder.addContentModule(ContentElement.Module(name = name, loadingRule = loadingRule, embeddedDescriptorContent = descriptorContent))
      }

      var nesting = 1
      while (true) {
        val type = reader.next()
        if (type == XMLStreamConstants.START_ELEMENT) {
          nesting++
        }
        else if (type == XMLStreamConstants.END_ELEMENT) {
          if (--nesting == 0) {
            break
          }
        }
      }
    }
  }
  assert(reader.isEndElement)
}

private fun readDependencies(reader: XMLStreamReader2, builder: PluginDescriptorBuilder, interner: XmlInterner) {
  reader.consumeChildElements { elementName ->
    when (elementName) {
      PluginXmlConst.DEPENDENCIES_MODULE_ELEM -> {
        var name: String? = null
        for (i in 0 until reader.attributeCount) {
          if (reader.getAttributeLocalName(i) == PluginXmlConst.DEPENDENCIES_MODULE_NAME_ATTR) {
            name = interner.name(reader.getAttributeValue(i))
            break
          }
        }
        builder.addDependency(DependenciesElement.ModuleDependency(name!!))
      }
      PluginXmlConst.DEPENDENCIES_PLUGIN_ELEM -> {
        var id: String? = null
        for (i in 0 until reader.attributeCount) {
          if (reader.getAttributeLocalName(i) == PluginXmlConst.DEPENDENCIES_PLUGIN_ID_ATTR) {
            id = interner.name(reader.getAttributeValue(i))
            break
          }
        }
        builder.addDependency(DependenciesElement.PluginDependency(id!!))
      }
      else -> throw RuntimeException("Unknown content item type: $elementName")
    }
    reader.skipElement()
  }
  assert(reader.isEndElement)
}

@ApiStatus.Internal
interface ReadModuleContext {
  val interner: XmlInterner

  val elementOsFilter: (OS) -> Boolean

  val isMissingIncludeIgnored: Boolean
    get() = false
}

private fun readInclude(
  consumer: PluginDescriptorFromXmlStreamConsumer,
  reader: XMLStreamReader2,
  allowedPointer: String,
) {
  val builder = consumer.getBuilder()
  val xIncludeLoader = consumer.xIncludeLoader ?: throw XMLStreamException("include is not supported because no pathResolver", reader.location)
  var path: String? = null
  var pointer: String? = null
  for (i in 0 until reader.attributeCount) {
    when (reader.getAttributeLocalName(i)) {
      PluginXmlConst.INCLUDE_HREF_ATTR -> path = getNullifiedAttributeValue(reader, i)
      PluginXmlConst.INCLUDE_XPOINTER_ATTR -> pointer = reader.getAttributeValue(i)?.takeIf { !it.isEmpty() && it != allowedPointer }
      PluginXmlConst.INCLUDE_INCLUDE_IF_ATTR -> {
        checkConditionalIncludeIsSupported("includeIf", builder)
        val value = reader.getAttributeValue(i)?.let { System.getProperty(it) }
        if (value != "true") {
          reader.skipElement()
          return
        }
      }
      PluginXmlConst.INCLUDE_INCLUDE_UNLESS_ATTR -> {
        checkConditionalIncludeIsSupported("includeUnless", builder)
        val value = reader.getAttributeValue(i)?.let { System.getProperty(it) }
        if (value == "true") {
          reader.skipElement()
          return
        }
      }
      else -> throw RuntimeException("Unknown attribute ${reader.getAttributeLocalName(i)} (${reader.location})")
    }
  }

  if (pointer != null) {
    throw RuntimeException("Attribute `xpointer` is not supported anymore (xpointer=$pointer, location=${reader.location})")
  }

  if (path == null) {
    throw RuntimeException("Missing `href` attribute (${reader.location})")
  }

  var isOptional = false
  reader.consumeChildElements(PluginXmlConst.INCLUDE_FALLBACK_ELEM) {
    isOptional = true
    reader.skipElement()
  }

  var readError: IOException? = null
  val loadedXInclude = try {
    val targetPath = LoadPathUtil.toLoadPath(relativePath = path, baseDir = consumer.includeBase)
    xIncludeLoader.loadXIncludeReference(path = targetPath)
  }
  catch (e: IOException) {
    readError = e
    null
  }
  if (loadedXInclude != null) {
    consumer.pushIncludeBase(LoadPathUtil.getChildBaseDir(base = consumer.includeBase, relativePath = path))
    try {
      consumer.consume(loadedXInclude.inputStream, loadedXInclude.diagnosticReferenceLocation)
    } finally {
      consumer.popIncludeBase()
    }
    return
  }

  if (isOptional) {
    return
  }

  if (consumer.readContext.isMissingIncludeIgnored) {
    LOG.info("$path include ignored (loader=${consumer.xIncludeLoader})", readError)
    return
  }
  else {
    throw RuntimeException("Cannot resolve $path (loader=${consumer.xIncludeLoader})", readError)
  }
}

private fun checkConditionalIncludeIsSupported(attribute: String, builder: PluginDescriptorBuilder) {
  if (builder.id !in K2_ALLOWED_PLUGIN_IDS) {
    throw IllegalArgumentException("$attribute of 'include' is not supported")
  }
}

private var dateTimeFormatter: DateTimeFormatter? = null

private object PluginParser

private val LOG: Logger
  get() = logger<PluginParser>()

private fun parseReleaseDate(dateString: String): LocalDate? {
  if (dateString.isEmpty() || dateString == PluginXmlConst.PRODUCT_DESCRIPTOR_RELEASE_DATE_PLACEHOLDER_VALUE) {
    return null
  }

  var formatter = dateTimeFormatter
  if (formatter == null) {
    formatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)!!
    dateTimeFormatter = formatter
  }

  try {
    return LocalDate.parse(dateString, formatter)
  }
  catch (e: ParseException) {
    LOG.error("Cannot parse release date", e)
  }
  return null
}

private fun readListeners(reader: XMLStreamReader2, containerDescriptor: ScopedElementsContainerBuilder) {
  reader.consumeChildElements(PluginXmlConst.LISTENER_ELEM) {
    var os: OS? = null
    var listenerClassName: String? = null
    var topicClassName: String? = null
    var activeInTestMode = true
    var activeInHeadlessMode = true
    for (i in 0 until reader.attributeCount) {
      when (reader.getAttributeLocalName(i)) {
        PluginXmlConst.LISTENER_OS_ATTR -> os = readOSValue(reader.getAttributeValue(i))
        PluginXmlConst.LISTENER_CLASS_ATTR -> listenerClassName = getNullifiedAttributeValue(
          reader, i)
        PluginXmlConst.LISTENER_TOPIC_ATTR -> topicClassName = getNullifiedAttributeValue(
          reader, i)
        PluginXmlConst.LISTENER_ACTIVE_IN_TEST_MODE_ATTR -> activeInTestMode = reader.getAttributeAsBoolean(i)
        PluginXmlConst.LISTENER_ACTIVE_IN_HEADLESS_MODE_ATTR -> activeInHeadlessMode = reader.getAttributeAsBoolean(i)
      }
    }

    if (listenerClassName == null || topicClassName == null) {
      LOG.error("Listener descriptor is not correct as ${reader.location}")
    }
    else {
      containerDescriptor.addListener(ListenerElement(
        listenerClassName = listenerClassName,
        topicClassName = topicClassName,
        activeInTestMode = activeInTestMode,
        activeInHeadlessMode = activeInHeadlessMode,
        os = os
      ))
    }
    reader.skipElement()
  }

  assert(reader.isEndElement)
}

private fun readOSValue(value: String): OS {
  return when (value) {
    PluginXmlConst.OS_MAC_VALUE -> OS.MAC
    PluginXmlConst.OS_LINUX_VALUE -> OS.LINUX
    PluginXmlConst.OS_WINDOWS_VALUE -> OS.WINDOWS
    PluginXmlConst.OS_UNIX_VALUE -> OS.UNIX
    PluginXmlConst.OS_FREEBSD_VALUE -> OS.FREEBSD
    else -> throw IllegalArgumentException("Unknown OS: $value")
  }
}

private inline fun XMLStreamReader.consumeChildElements(crossinline consumer: (name: String) -> Unit) {
  // the cursor must be at the start of the parent element
  assert(isStartElement)

  var depth = 1
  while (true) {
    when (next()) {
      XMLStreamConstants.START_ELEMENT -> {
        depth++
        consumer(localName)
        assert(isEndElement)
        depth--
      }

      XMLStreamConstants.END_ELEMENT -> {
        if (depth != 1) {
          throw IllegalStateException("Expected depth: 1")
        }
        return
      }

      XMLStreamConstants.CDATA,
      XMLStreamConstants.SPACE,
      XMLStreamConstants.CHARACTERS,
      XMLStreamConstants.ENTITY_REFERENCE,
      XMLStreamConstants.COMMENT,
      XMLStreamConstants.PROCESSING_INSTRUCTION -> {
        // ignore
      }
      else -> throw XMLStreamException("Unexpected state: ${getEventTypeString(eventType)}", location)
    }
  }
}

private inline fun XMLStreamReader2.consumeChildElements(name: String, crossinline consumer: () -> Unit) {
  consumeChildElements {
    if (name == it) {
      consumer()
      assert(isEndElement)
    }
    else {
      skipElement()
    }
  }
}

private fun getEventTypeString(eventType: Int): String {
  return when (eventType) {
    XMLEvent.START_ELEMENT -> "START_ELEMENT"
    XMLEvent.END_ELEMENT -> "END_ELEMENT"
    XMLEvent.PROCESSING_INSTRUCTION -> "PROCESSING_INSTRUCTION"
    XMLEvent.CHARACTERS -> "CHARACTERS"
    XMLEvent.COMMENT -> "COMMENT"
    XMLEvent.START_DOCUMENT -> "START_DOCUMENT"
    XMLEvent.END_DOCUMENT -> "END_DOCUMENT"
    XMLEvent.ENTITY_REFERENCE -> "ENTITY_REFERENCE"
    XMLEvent.ATTRIBUTE -> "ATTRIBUTE"
    XMLEvent.DTD -> "DTD"
    XMLEvent.CDATA -> "CDATA"
    XMLEvent.SPACE -> "SPACE"
    else -> "UNKNOWN_EVENT_TYPE, $eventType"
  }
}
