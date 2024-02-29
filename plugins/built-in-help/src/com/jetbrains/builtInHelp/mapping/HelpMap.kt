// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp.mapping

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import org.jetbrains.annotations.NotNull

/**
 * Created by Egor.Malyshev on 7/17/2017.
 */
@JacksonXmlRootElement(localName = "map")
class HelpMap {

  @JacksonXmlProperty(localName = "mapID")
  @JacksonXmlElementWrapper(useWrapping = false)
  var mappings: List<HelpMapId> = mutableListOf()

  @NotNull
  fun getUrlForId(@NotNull target: String): String {
    return mappings
             .firstOrNull { it.target == target }?.url ?: getDefaultUrl()
  }

  @NotNull
  private fun getDefaultUrl(): String {
    return mappings
      .first { "yes" == it.isDefault }.url
  }
}