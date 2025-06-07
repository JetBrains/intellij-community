// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package org.jetbrains.idea.devkit.dom.templates

import com.intellij.icons.AllIcons
import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.*
import org.jetbrains.idea.devkit.dom.Option
import javax.swing.Icon

internal class TemplateSetDescription: DomFileDescription<TemplateSet>(TemplateSet::class.java, "templateSet") {

  override fun getFileIcon(file: XmlFile, flags: Int): Icon? = AllIcons.Nodes.Template

}

@DefinesXml
interface TemplateSet: DomElement {
  val group: GenericAttributeValue<String>
  val templates: List<Template>
  val replace: GenericAttributeValue<String>
}

@NameStrategy(JavaNameStrategy::class)
interface Template: DomElement {
  val id: GenericAttributeValue<String>
  val name: GenericAttributeValue<String>
  val value: GenericAttributeValue<String>

  @get:Attribute("resource-bundle")
  val resourceBundle: GenericAttributeValue<String>
  val key: GenericAttributeValue<String>
  val description: GenericAttributeValue<String>
  val shortcut: GenericAttributeValue<String>

  val toReformat: GenericAttributeValue<Boolean>
  val toShortenFQNames: GenericAttributeValue<Boolean>
  val useStaticImport: GenericAttributeValue<Boolean>
  val deactivated: GenericAttributeValue<Boolean>

  val variables: List<Variable>
  val context: Context
}

@NameStrategy(JavaNameStrategy::class)
interface Variable: DomElement {
  val name: GenericAttributeValue<String>
  val expression: GenericAttributeValue<String>
  val defaultValue: GenericAttributeValue<String>
  val alwaysStopAt: GenericAttributeValue<Boolean>
}

interface Context: DomElement {
  val options: List<Option>
}