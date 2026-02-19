// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl.productModules

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.psi.PsiElement
import com.intellij.util.xml.ConvertContext
import com.intellij.util.xml.ResolvingConverter
import org.jetbrains.idea.devkit.symbols.IntellijModuleSymbol

internal class IntellijModuleConverter : ResolvingConverter<IntellijModuleSymbol>() {
  override fun fromString(s: String?, context: ConvertContext): IntellijModuleSymbol? {
    val name = s ?: return null
    val moduleId = ModuleId(name)
    return if (context.project.workspaceModel.currentSnapshot.contains(moduleId)) {
      IntellijModuleSymbol(moduleId)
    }
    else {
      null
    }
  }

  override fun toString(t: IntellijModuleSymbol?, context: ConvertContext): String? = t?.moduleId?.name

  override fun resolve(symbol: IntellijModuleSymbol?, context: ConvertContext): PsiElement? {
    if (symbol == null) return null
    val target = symbol.getNavigationTargets(context.project).firstOrNull() ?: return null
    return target.findModuleDirectory() ?: target.findModuleImlFile()
  }

  override fun createLookupElement(symbol: IntellijModuleSymbol?): LookupElement? {
    if (symbol == null) return null
    return LookupElementBuilder.create(symbol.moduleId.name).withIcon(AllIcons.Nodes.Module)
  }

  override fun getVariants(context: ConvertContext): Collection<IntellijModuleSymbol> {
    return context.project.workspaceModel.currentSnapshot.entities(ModuleEntity::class.java).mapTo(ArrayList()) { 
      IntellijModuleSymbol(it.symbolicId) 
    }
  }
}