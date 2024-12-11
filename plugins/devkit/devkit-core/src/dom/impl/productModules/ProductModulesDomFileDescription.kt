// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl.productModules

import com.intellij.icons.AllIcons
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.DomFileDescription
import org.jetbrains.idea.devkit.dom.productModules.ProductModulesElement
import javax.swing.Icon

internal class ProductModulesDomFileDescription
  : DomFileDescription<ProductModulesElement>(ProductModulesElement::class.java, "product-modules") {

  override fun getFileIcon(file: XmlFile, flags: Int): Icon = AllIcons.Nodes.ModuleGroup

  override fun isMyFile(file: XmlFile, module: Module?): Boolean {
    return file.name == "product-modules.xml" && module != null && IntelliJProjectUtil.isIntelliJPlatformProject(module.project)
  }
}