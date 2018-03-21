// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.GsonBuilder
import com.intellij.testFramework.assertions.dumpData
import net.miginfocom.swing.MigLayout
import java.io.StringReader
import java.util.*
import javax.swing.JPanel

@Suppress("UNCHECKED_CAST")
fun configurationToJson(component: JPanel, layout: MigLayout, isIncludeLayoutConstraints: Boolean): String {
  val objectMapper = ObjectMapper()
  objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT)

  val componentConstrains = LinkedHashMap<String, Any>()
  for ((index, c) in component.components.withIndex()) {
    componentConstrains.put("${c.javaClass.simpleName} #${index}", layout.getComponentConstraints(c))
  }

  val json = objectMapper
    .writerWithDefaultPrettyPrinter()
    .writeValueAsString(linkedMapOf(
      "layoutConstraints" to if (isIncludeLayoutConstraints) layout.layoutConstraints else null,
      "rowConstraints" to layout.rowConstraints,
      "columnConstraints" to layout.columnConstraints,
      "componentConstrains" to componentConstrains
    ))
  // *** *** jackson has ugly API and not clear how to write custom filter, so, GSON is used
  val gson = GsonBuilder()
    .setPrettyPrinting()
    .disableHtmlEscaping()
    .create()
  val map = gson.fromJson(StringReader(json), MutableMap::class.java)
  @Suppress("UNCHECKED_CAST")
  for (cc in (map.get("componentConstrains") as MutableMap<String, MutableMap<String, Any?>>).values) {
    removeDefaultCc(cc)
    cc.remove("animSpec")

    for (axisName in arrayOf("horizontal", "vertical")) {
      val p = cc.get(axisName) as MutableMap<String, Any?>? ?: continue
      val size = p.get("size") as MutableMap<*, *>?
      if (size != null && size.get("unset") == true) {
        size.remove("unset")
        if (size.isEmpty()) {
          p.remove("size")
        }
      }

      for (name2 in arrayOf("size", "gapBefore", "gapAfter")) {
        val v = p.get(name2) as? MutableMap<*, *>? ?: continue
        for (name in arrayOf("min", "max", "preferred")) {
          val p2 = v.get(name) as? MutableMap<*, *> ?: continue
          if (p2.get("unit") == 1.0) {
            p2.remove("unit")
          }
          if (p2.get("operation") == 100.0) {
            p2.remove("operation")
          }
          if (p2.get(axisName) == true) {
            p2.remove(axisName)
          }

          if (p2.isEmpty()) {
            v.remove(name)
          }
        }
      }

      removeDefaultCc(p)
      if (p.isEmpty()) {
        cc.remove(axisName)
      }
    }
  }

  (map.get("columnConstraints") as? MutableMap<String, Any>)?.let { cleanupColumnConstraints(it) }
  return dumpData(map)
}

@Suppress("UNCHECKED_CAST")
private fun cleanupColumnConstraints(columnConstraints: MutableMap<String, Any>) {
  val acList = columnConstraints.remove("constaints")!! as List<MutableMap<String, Any>>
  columnConstraints.put("constraints", acList)
  for (ac in acList) {
    val size = ac.get("size") as MutableMap<*, *>?
    if (size != null && size.get("unset") == true) {
      size.remove("unset")
      if (size.isEmpty()) {
        ac.remove("size")
      }

      if (ac.get("shrinkPriority") == 100.0) {
        ac.remove("shrinkPriority")
      }
      if (ac.get("shrink") == 100.0) {
        ac.remove("shrink")
      }
      if (ac.get("growPriority") == 100.0) {
        ac.remove("growPriority")
      }
    }
  }
}

private fun removeDefaultCc(cc: MutableMap<String, Any?>) {
  for ((name, value) in DEFAULT_CC) {
    if (cc.get(name) == value) {
      cc.remove(name)
    }
  }
}

private val DEFAULT_CC = mapOf(
  "dockSide" to -1.0,
  "split" to 1.0,
  "spanX" to 1.0,
  "spanY" to 1.0,
  "cellX" to -1.0,
  "cellY" to -1.0,
  "hideMode" to -1.0,
  "growPriority" to 100.0,
  "shrinkPriority" to 100.0,
  "shrink" to 100.0,
  "grow" to 100.0,
  "boundsInGrid" to true,
  "" to ""
)