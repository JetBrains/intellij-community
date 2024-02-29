package com.jetbrains.builtInHelp.mapping

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

/**
 * Created by Egor.Malyshev on 20.04.2015.
 */
@JacksonXmlRootElement(localName = "mapID")
class HelpMapId {
  @JacksonXmlProperty(localName = "url", isAttribute = true)
  var url: String = ""

  @JacksonXmlProperty(localName = "target", isAttribute = true)
  var target: String = ""

  @JacksonXmlProperty(localName = "default", isAttribute = true)
  var isDefault: String = "no"
}