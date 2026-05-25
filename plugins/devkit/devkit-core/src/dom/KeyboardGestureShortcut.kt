// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom

import com.intellij.openapi.actionSystem.KeyboardGestureAction
import com.intellij.spellchecker.xml.NoSpellchecking
import com.intellij.util.xml.Convert
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.GenericAttributeValue
import com.intellij.util.xml.Required
import org.jetbrains.idea.devkit.dom.impl.KeymapConverter
import org.jetbrains.idea.devkit.dom.keymap.KeymapXmlRootElement

interface KeyboardGestureShortcut : DomElement {
  @get:Required
  @get:NoSpellchecking
  val keystroke: GenericAttributeValue<String>

  @get:Required
  val modifier: GenericAttributeValue<KeyboardGestureAction.ModifierType>

  @get:Required
  @get:Convert(KeymapConverter::class)
  val keymap: GenericAttributeValue<KeymapXmlRootElement>

  val remove: GenericAttributeValue<Boolean>

  val replaceAll: GenericAttributeValue<Boolean>
}
