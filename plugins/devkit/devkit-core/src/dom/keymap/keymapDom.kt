// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package org.jetbrains.idea.devkit.dom.keymap

import com.intellij.icons.AllIcons
import com.intellij.ide.presentation.Presentation
import com.intellij.openapi.module.Module
import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.*
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupReferencingConverter
import org.jetbrains.idea.devkit.dom.impl.KeymapConverter
import org.jetbrains.idea.devkit.util.PsiUtil
import javax.swing.Icon

internal class KeymapDomFileDescription : DomFileDescription<KeymapXmlRootElement>(KeymapXmlRootElement::class.java, "keymap") {
  override fun getFileIcon(file: XmlFile, flags: Int): Icon = AllIcons.General.Keyboard
  override fun isMyFile(file: XmlFile, module: Module?): Boolean {
    return PsiUtil.isPluginProject(file.project)
  }
}

@DefinesXml
@Presentation(icon = "AllIcons.General.Keyboard")
interface KeymapXmlRootElement : DomElement {

  @get:NameValue
  @get:Required
  val name: GenericAttributeValue<String>

  @get:Attribute("parent")
  @get:Convert(KeymapConverter::class)
  val parentAttribute: GenericAttributeValue<String>

  val version: GenericAttributeValue<Integer>

  val disableMnemonics: GenericAttributeValue<Boolean>

  @get:SubTagList("action")
  val actions: List<KeymapXmlAction>
}

interface KeymapXmlAction : DomElement {

  @get:Required
  @get:Referencing(ActionOrGroupReferencingConverter::class)
  val id: GenericAttributeValue<String>

  @get:SubTagList("keyboard-shortcut")
  val keyboardShortcuts: List<KeymapXmlKeyboardShortcut>

  @get:SubTagList("mouse-shortcut")
  val mouseShortcuts: List<KeymapXmlMouseShortcut>
}

interface KeymapXmlKeyboardShortcut : DomElement {
  val firstKeystroke: GenericAttributeValue<String>
  val secondKeystroke: GenericAttributeValue<String>
}

interface KeymapXmlMouseShortcut : DomElement {
  val keystroke: GenericAttributeValue<String>
}