// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dgm

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.*
import com.intellij.util.asSafely
import com.intellij.util.containers.addIfNotNull
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTuple
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroRegistryService
import java.util.concurrent.ConcurrentHashMap

class GroovyMacroRegistryServiceImpl(val project: Project) : GroovyMacroRegistryService, Disposable {

  private val availableModules: MutableMap<Module, CachedValue<Set<SmartPsiElementPointer<PsiMethod>>>> = ConcurrentHashMap()

  init {
    project.messageBus.connect().subscribe(WorkspaceModelTopics.CHANGED, GroovyMacroModuleListener())
  }

  override fun resolveAsMacro(call: GrMethodCall): PsiMethod? {
    val module = ModuleUtilCore.findModuleForPsiElement(call) ?: return null
    val invokedName = call.invokedExpression.asSafely<GrReferenceExpression>()?.referenceName ?: return null
    val candidates = getModuleRegistry(module).filter { it.element?.name == invokedName }
    return candidates.find { it.element?.canBeAppliedTo(call) == true }?.element
  }

  override fun getAllKnownMacros(context: PsiElement): List<PsiMethod> {
    val module = ModuleUtilCore.findModuleForPsiElement(context) ?: return emptyList()
    return getModuleRegistry(module).mapNotNull { it.element }
  }

  fun refreshModule(module: Module) {
    availableModules.remove(module)
  }

  private fun getModuleRegistry(module: Module) : Set<SmartPsiElementPointer<PsiMethod>> {
    return availableModules.computeIfAbsent(module) {
      val moduleRegistry: CachedValue<Set<SmartPsiElementPointer<PsiMethod>>> = CachedValuesManager.getManager(module.project).createCachedValue {
        val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
        val extensionFiles = FileTypeIndex.getFiles(DGMFileType, scope)
        if (extensionFiles.isEmpty()) {
          return@createCachedValue CachedValueProvider.Result(emptySet(), PsiModificationTracker.MODIFICATION_COUNT)
        }
        val macroRegistry = mutableSetOf<PsiMethod>()
        val extensionClassFiles = mutableListOf<VirtualFile>()
        for (extensionVirtualFile in extensionFiles) {
          val psi = PsiUtilBase.getPsiFile(module.project, extensionVirtualFile)
          val inst = psi.asSafely<PropertiesFile>()?.findPropertyByKey("extensionClasses")
          val extensionClassName = inst?.value ?: continue
          val extensionClass = JavaPsiFacade.getInstance(module.project).findClass(extensionClassName, scope) ?: continue
          extensionClassFiles.addIfNotNull(extensionClass.containingFile.virtualFile)
          val macroMethods = extensionClass.methods.filter {
            it.hasAnnotation(GroovyCommonClassNames.ORG_CODEHAUS_GROOVY_MACRO_RUNTIME_MACRO) &&
            it.parameterList.parameters[0].typeElement != null &&
            it.parameterList.parameters[0].type.equalsToText("org.codehaus.groovy.macro.runtime.MacroContext")
          }
          macroRegistry.addAll(macroMethods)
        }
        CachedValueProvider.Result(macroRegistry.mapTo(HashSet()) { SmartPointerManager.createPointer(it) }, *extensionFiles.toTypedArray(), *extensionClassFiles.toTypedArray())
      }
      moduleRegistry
    }.value
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
      type.equalsToText("org.codehaus.groovy.ast.expr.ListExpression") && argument.asSafely<GrListOrMap>()?.isMap != false -> return false
      type.equalsToText("org.codehaus.groovy.ast.expr.MapExpression") && argument.asSafely<GrListOrMap>()?.isMap != true -> return false
      type.equalsToText("org.codehaus.groovy.ast.expr.TupleExpression") && argument !is GrTuple -> return false
      type.equalsToText("org.codehaus.groovy.ast.expr.MethodCallExpression") && argument !is GrMethodCallExpression -> return false
    }
  }
  return true
}