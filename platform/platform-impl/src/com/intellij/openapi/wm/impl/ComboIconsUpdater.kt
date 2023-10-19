// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.PresentationIconUpdater
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.impl.ExpandableComboAction.Companion.LEFT_ICONS_KEY
import com.intellij.openapi.wm.impl.ExpandableComboAction.Companion.RIGHT_ICONS_KEY
import java.util.function.Function
import javax.swing.Icon

class ComboIconsUpdater: PresentationIconUpdater {

  override fun performUpdate(presentation: Presentation, iconTransformer: Function<Icon, Icon>) {
    updateAndSubscribe(presentation, LEFT_ICONS_KEY, iconTransformer)
    updateAndSubscribe(presentation, RIGHT_ICONS_KEY, iconTransformer)
  }

  private fun updateAndSubscribe(presentation: Presentation, key: Key<List<Icon>>, iconTransformer: Function<Icon, Icon>) {
    updateIconsIfNecessary(presentation, key, iconTransformer)
    presentation.addPropertyChangeListener { evt ->
      if (evt.propertyName == key.toString()) {
        updateIconsIfNecessary(presentation, key, iconTransformer)
      }
    }
  }

  private fun updateIconsIfNecessary(presentation: Presentation, key: Key<List<Icon>>, transformer: Function<Icon, Icon>) {
    presentation.getClientProperty(key)
      ?.let { getUpdatedIcons(it, transformer) }
      ?.let { presentation.putClientProperty(key, it) }
  }


  private fun getUpdatedIcons(icons: List<Icon>, transformer: Function<Icon, Icon>): List<Icon>? {
    val res = mutableListOf<Icon>()
    var anyChanged = false
    icons.forEach { src ->
      val updated = transformer.apply(src)
      res.add(updated)
      if (updated != src) anyChanged = true
    }

    return if (anyChanged) res else null
  }
}