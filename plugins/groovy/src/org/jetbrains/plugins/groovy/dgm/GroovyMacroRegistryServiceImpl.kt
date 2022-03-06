// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dgm

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilBase
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTuple
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroRegistryService

class GroovyMacroRegistryServiceImpl(val project: Project) : GroovyMacroRegistryService, Disposable {

  private val availableModules: MutableMap<Module, MacroRegistry> = mutableMapOf()

  override fun resolveAsMacro(call: GrMethodCall): PsiMethod? {
    val module = ModuleUtilCore.findModuleForPsiElement(call) ?: return null
    val invokedName = call.invokedExpression.castSafelyTo<GrReferenceExpression>()?.referenceName ?: return null
    val candidates = availableModules[module]?.filter { it.name == invokedName } ?: return null
    return candidates.find { it.canBeAppliedTo(call) }
  }

  override fun getAllMacros(context: PsiElement): List<PsiMethod> {
    val module = ModuleUtilCore.findModuleForPsiElement(context) ?: return emptyList()
    return availableModules[module]?.toList() ?: emptyList()
  }

  override fun refreshModule(module: Module) = DumbService.getInstance(project).runWithAlternativeResolveEnabled<Throwable> {
    val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
    val extensionFiles = FileTypeIndex.getFiles(DGMFileType, scope)
    val macroRegistry = mutableSetOf<PsiMethod>()
    for (extensionVirtualFile in extensionFiles) {
      val psi = PsiUtilBase.getPsiFile(project, extensionVirtualFile)
      val inst = (psi as PropertiesFile).findPropertyByKey("extensionClasses")
      val extensionClassName = inst?.value ?: continue
      val extensionClass = JavaPsiFacade.getInstance(project).findClass(extensionClassName, scope) ?: continue
      val macroMethods = extensionClass.methods.filter {
        it.hasAnnotation(GroovyCommonClassNames.ORG_CODEHAUS_GROOVY_MACRO_RUNTIME_MACRO) &&
        it.parameterList.parameters[0].type.equalsToText("org.codehaus.groovy.macro.runtime.MacroContext")
      }
      macroRegistry.addAll(macroMethods)
    }
    if (macroRegistry.isNotEmpty()) {
      availableModules[module] = macroRegistry
    }
    else {
      availableModules.remove(module)
    }
  }

  override fun dispose() {
    availableModules.clear()
  }
}

private fun PsiMethod.canBeAppliedTo(call: GrMethodCall): Boolean {
  val methodParameters = parameterList.parameters.drop(1)
  for ((argument, parameter) in (call.expressionArguments + call.closureArguments).zip(methodParameters)) {
    val type = parameter.typeElement?.type ?: return false
    when {
      type.equalsToText("org.codehaus.groovy.ast.expr.ClosureExpression") && argument !is GrClosableBlock -> return false
      type.equalsToText("org.codehaus.groovy.ast.expr.ListExpression") && argument.castSafelyTo<GrListOrMap>()?.isMap != false -> return false
      type.equalsToText("org.codehaus.groovy.ast.expr.MapExpression") && argument.castSafelyTo<GrListOrMap>()?.isMap != true -> return false
      type.equalsToText("org.codehaus.groovy.ast.expr.TupleExpression") && argument !is GrTuple -> return false
      type.equalsToText("org.codehaus.groovy.ast.expr.MethodCallExpression") && argument !is GrMethodCallExpression -> return false
    }
  }
  return true
}


private typealias MacroRegistry = Set<PsiMethod>