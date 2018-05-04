// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.configurationStore.serialize
import com.intellij.ui.dumpComponentBounds
import com.intellij.ui.getComponentKey
import com.intellij.ui.layout.migLayout.patched.*
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import net.miginfocom.layout.*
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.introspector.Property
import org.yaml.snakeyaml.nodes.NodeTuple
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer
import java.awt.Container
import java.awt.Rectangle

private val filter by lazy {
  object : Representer() {
    private val emptyLC = LC()
    private val emptyAC = AC()
    private val emptyCC = CC()
    private val emptyDimConstraint = DimConstraint()
    private val emptyBoundSize = BoundSize.NULL_SIZE

    private val xmlSerializationFilter = SkipDefaultsSerializationFilter(emptyAC, emptyCC, emptyDimConstraint, emptyBoundSize, UnitValue(Float.MIN_VALUE * Math.random().toFloat(), UnitValue.CM, "") /* no default value, some unique unit value */)

    override fun representJavaBeanProperty(bean: Any, property: Property, propertyValue: Any?, customTag: Tag?): NodeTuple? {
      if (propertyValue == null || property.name == "animSpec") {
        return null
      }

      if (bean is Rectangle && (!property.isWritable || property.name == "size" || property.name == "location")) {
        return null
      }

      if (bean is BoundSize && property.name == "unset") {
        return null
      }
      if (bean is UnitValue) {
        if (property.name == "unitString") {
          if (propertyValue != "px" && bean.value != 0f) {
            throw RuntimeException("Only px must be used")
          }
          return null
        }
        if (property.name == "unit" && bean.value != 0f) {
          if (propertyValue != UnitValue.PIXEL) {
            throw RuntimeException("Only px must be used")
          }
          return null
        }
        if (property.name == "operation" && propertyValue == 100) {
          // ignore static operation
          return null
        }
      }

      val emptyBean = when (bean) {
        is AC -> emptyAC
        is CC -> emptyCC
        is DimConstraint -> emptyDimConstraint
        is BoundSize -> emptyBoundSize
        is LC -> emptyLC
        else -> null
      }

      if (emptyBean != null) {
        val oldValue = property.get(emptyBean)
        if (oldValue == propertyValue) {
          return null
        }

        if (propertyValue is DimConstraint && propertyValue.serialize(xmlSerializationFilter) == null) {
          return null
        }
      }

      return super.representJavaBeanProperty(bean, property, propertyValue, customTag)
    }
  }
}

private fun dumpCellBounds(layout: MigLayout): Any {
  val gridField = MigLayout::class.java.getDeclaredField("grid")
  gridField.isAccessible = true
  return MigLayoutTestUtil.getRectangles(gridField.get(layout) as Grid)
}

@Suppress("UNCHECKED_CAST")
internal fun serializeLayout(component: Container, isIncludeCellBounds: Boolean = true): String {
  val layout = component.layout as MigLayout

  val componentConstrains = LinkedHashMap<String, Any>()
  val componentToConstraints = layout.getComponentConstraints()
  for ((index, c) in component.components.withIndex()) {
    componentConstrains.put(getComponentKey(c, index), componentToConstraints.get(c)!!)
  }

  val dumperOptions = DumperOptions()
  dumperOptions.isAllowReadOnlyProperties = true
  dumperOptions.lineBreak = DumperOptions.LineBreak.UNIX
  val yaml = Yaml(filter, dumperOptions)
  return yaml.dump(linkedMapOf(
    "layoutConstraints" to layout.layoutConstraints,
    "rowConstraints" to layout.rowConstraints,
    "columnConstraints" to layout.columnConstraints,
    "componentConstrains" to componentConstrains,
    "cellBounds" to if (isIncludeCellBounds) dumpCellBounds(layout) else null,
    "componentBounds" to dumpComponentBounds(component)
  ))
    .replace("constaints", "constraints")
    .replace(" !!net.miginfocom.layout.CC", "")
    .replace(" !!net.miginfocom.layout.AC", "")
    .replace(" !!net.miginfocom.layout.LC", "")
}