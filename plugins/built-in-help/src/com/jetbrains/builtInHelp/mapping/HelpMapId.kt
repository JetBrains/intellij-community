package com.jetbrains.builtInHelp.mapping

import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlAttribute
import javax.xml.bind.annotation.XmlRootElement

/**
 * Created by Egor.Malyshev on 20.04.2015.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "mapID")
class HelpMapId {
  @XmlAttribute(name = "url")
  var url: String = ""
  @XmlAttribute(name = "target")
  var target: String = ""
  @XmlAttribute(name = "default")
  var isDefault: String = "no"
}