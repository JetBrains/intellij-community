// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dgm

import com.intellij.ProjectTopics
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilBase
import com.intellij.util.castSafelyTo
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.lazyPub
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTuple
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroModuleListener
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroRegistryService

class GroovyMacroRegistryServiceImpl(val project: Project) : GroovyMacroRegistryService {

  @Volatile
  private var availableModules: Lazy<Map<Module, MacroRegistry>> = getInitializer()

  init {
    val connection = project.messageBus.connect()
    val listener = GroovyMacroModuleListener()
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, listener)
    WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(connection, listener)
  }

  override fun resolveAsMacro(call: GrMethodCall): PsiMethod? {
    val module = ModuleUtilCore.findModuleForPsiElement(call) ?: return null
    val invokedName = call.invokedExpression.castSafelyTo<GrReferenceExpression>()?.referenceName ?: return null
    val candidates = availableModules.value[module]?.filter { it.element?.name == invokedName } ?: return null
    return candidates.find { it.element?.canBeAppliedTo(call) == true }?.element
  }

  override fun getAllMacros(context: PsiElement): List<PsiMethod> {
    val module = ModuleUtilCore.findModuleForPsiElement(context) ?: return emptyList()
    return availableModules.value[module]?.mapNotNull { it.element } ?: emptyList()
  }

  override fun refreshModule(module: Module) {
    availableModules = getInitializer()
  }

  private fun collectModuleRegistry(): Map<Module, MacroRegistry> {
    val modules = mutableMapOf<Module, MacroRegistry>()
    DumbService.getInstance(project).runWithAlternativeResolveEnabled<Throwable> {
      FileBasedIndex.getInstance().ignoreDumbMode(DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE) {
        for (module in ModuleManager.getInstance(project).modules) {
          val registry = computeModuleRegistry(module)
          if (registry.isNotEmpty()) {
            modules[module] = registry
          }
        }
      }
    }
    return modules
  }

  fun getInitializer(): Lazy<Map<Module, MacroRegistry>> {
    return lazyPub { collectModuleRegistry() }
  }
}

private fun computeModuleRegistry(module : Module) : MacroRegistry {
  val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
  val extensionFiles = FileTypeIndex.getFiles(DGMFileType, scope)
  val macroRegistry = mutableSetOf<PsiMethod>()
  for (extensionVirtualFile in extensionFiles) {
    val psi = PsiUtilBase.getPsiFile(module.project, extensionVirtualFile)
    val inst = psi.castSafelyTo<PropertiesFile>()?.findPropertyByKey("extensionClasses")
    val extensionClassName = inst?.value ?: continue
    val extensionClass = JavaPsiFacade.getInstance(module.project).findClass(extensionClassName, scope) ?: continue
    val macroMethods = extensionClass.methods.filter {
      it.hasAnnotation(GroovyCommonClassNames.ORG_CODEHAUS_GROOVY_MACRO_RUNTIME_MACRO) &&
      it.parameterList.parameters[0].typeElement != null &&
      it.parameterList.parameters[0].type.equalsToText("org.codehaus.groovy.macro.runtime.MacroContext")
    }
    macroRegistry.addAll(macroMethods)
  }
  return macroRegistry.mapTo(mutableSetOf(), SmartPointerManager::createPointer)
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


private typealias MacroRegistry = Set<SmartPsiElementPointer<PsiMethod>>