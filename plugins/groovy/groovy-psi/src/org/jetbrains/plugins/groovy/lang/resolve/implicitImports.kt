/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.scope.DelegatingScopeProcessor
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

class Import(
    val name: String,
    val type: ImportType = ImportType.REGULAR
)

enum class ImportType {
  /**
   * Class
   */
  REGULAR,
  /**
   * Class member
   */
  STATIC,
  /**
   * Classes of package
   */
  STAR,
  /**
   * Members of class
   */
  STATIC_STAR
}

abstract class GrImportContributorBase : GrImportContributor {

  abstract fun appendImplicitlyImportedPackages(file: GroovyFile): List<String>

  final override fun getImports(file: GroovyFile): Collection<Import> {
    return appendImplicitlyImportedPackages(file).map {
      Import(it, ImportType.STAR)
    }
  }
}

fun getImplicitImports(file: GroovyFile): Collection<Import> = GrImportContributor.EP_NAME.extensions.flatMap { it.getImports(file) }

fun processImplicitImports(processor: PsiScopeProcessor,
                           state: ResolveState,
                           lastParent: PsiElement?,
                           place: PsiElement,
                           file: GroovyFile): Boolean {
  val hint = processor.getHint(com.intellij.psi.scope.ElementClassHint.KEY)
  val facade = JavaPsiFacade.getInstance(file.project)

  val packageSkipper = lazy(LazyThreadSafetyMode.NONE) { PackageSkippingProcessor(processor) }
  val staticMemberFilter = lazy(LazyThreadSafetyMode.NONE) {
    object : DelegatingScopeProcessor(processor) {
      override fun execute(element: PsiElement, state: ResolveState): Boolean {
        return element !is PsiMember || !element.hasModifierProperty(PsiModifier.STATIC) || super.execute(element, state)
      }
    }
  }

  loop@for (implicitImport in getImplicitImports(file)) {
    when (implicitImport.type) {
      ImportType.REGULAR -> {
        if (!ResolveUtil.shouldProcessClasses(hint)) continue@loop
        val clazz = facade.findClass(implicitImport.name, file.resolveScope) ?: continue@loop
        if (!ResolveUtil.processElement(processor, clazz, state)) return false
      }
      ImportType.STATIC -> {
        val className = StringUtil.getPackageName(implicitImport.name)
        val memberName = StringUtil.getShortName(implicitImport.name)
        if (StringUtil.isEmptyOrSpaces(className) || StringUtil.isEmptyOrSpaces(memberName)) continue@loop
        val clazz = facade.findClass(className, file.resolveScope) ?: continue@loop
        if (ResolveUtil.shouldProcessMethods (hint)) {
          for (method in clazz.findMethodsByName(memberName, true)) {
            if (!ResolveUtil.processElement(processor, method, state)) return false
          }
        }
        if (ResolveUtil.shouldProcessProperties(hint)) {
          val field = clazz.findFieldByName(memberName, true) ?: continue@loop
          if (!ResolveUtil.processElement(processor, field, state)) return false
        }
      }
      ImportType.STAR -> {
        val pckg = facade.findPackage(implicitImport.name) ?: continue@loop
        if (!pckg.processDeclarations(packageSkipper.value, state, lastParent, place)) return false
      }
      ImportType.STATIC_STAR -> {
        val clazz = facade.findClass(implicitImport.name, file.resolveScope) ?: continue@loop
        if (!clazz.processDeclarations(staticMemberFilter.value, state, lastParent, place)) return false
      }
    }
  }

  return true
}
