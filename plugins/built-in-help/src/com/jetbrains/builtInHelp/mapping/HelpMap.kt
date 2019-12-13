// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp.mapping

import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.util.*
import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlRootElement

/**
 * Created by Egor.Malyshev on 7/17/2017.
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "map")
class HelpMap {

  @XmlElement(name = "mapID")
  var mappings: List<HelpMapId> = ArrayList()

  @Nullable
  fun getUrlForId(@NotNull target: String): String? {
    return mappings
             .firstOrNull { it.target == target }?.url ?: getDefaultUrl()
  }

  @NotNull
  private fun getDefaultUrl(): String? {
    return mappings
      .first { "yes" == it.isDefault }.url
  }
}