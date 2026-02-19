// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl

import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport
import org.jetbrains.plugins.groovy.transformations.TransformationContext

class AutoFinalTransformationSupport : AstTransformationSupport {

  private enum class AutoFinalMode {
    ENABLED,
    UNKNOWN,
    DISABLED
  }

  companion object {
    private fun getAutoFinalMode(owner: PsiModifierListOwner): AutoFinalMode {
      require(owner is GrTypeDefinition || owner is GrMethod || owner is GrField)
      val modifierList = owner.modifierList as? GrModifierList ?: return AutoFinalMode.UNKNOWN
      val annotations = modifierList.annotations.filter { it.hasQualifiedName(GroovyCommonClassNames.GROOVY_TRANSFORM_AUTO_FINAL) }
      if (annotations.isEmpty()) return AutoFinalMode.UNKNOWN
      val hasEnabledAutoFinal = annotations.asSequence()
        .map { GrAnnotationUtil.inferBooleanAttribute(it, "enabled") }
        .find { it != null }
      return when (hasEnabledAutoFinal) {
        null -> AutoFinalMode.UNKNOWN
        true -> AutoFinalMode.ENABLED
        false -> AutoFinalMode.DISABLED
      }
    }

    private fun getInitialAutoFinalMode(context: TransformationContext): AutoFinalMode {
      var currentClass = context.codeClass
      while (true) {
        val autoFinalMode = getAutoFinalMode(currentClass)
        if (autoFinalMode == AutoFinalMode.UNKNOWN) {
          currentClass = currentClass.containingClass as? GrTypeDefinition ?: break
        }
        else {
          return autoFinalMode
        }
      }
      return AutoFinalMode.UNKNOWN
    }
  }

  override fun applyTransformation(context: TransformationContext) {
    if (!PsiSearchHelper.getInstance(context.project).hasIdentifierInFile(context.codeClass.containingFile, "AutoFinal")) return

    val autoFinalStack: MutableList<AutoFinalMode> = SmartList()
    autoFinalStack.add(getInitialAutoFinalMode(context))

    context.codeClass.acceptChildren(object : GroovyRecursiveElementVisitor() {

      private fun <T : PsiModifierListOwner> visitWithAutoFinalCollecting(element: T, callback: () -> Unit) {
        val autoFinalMode = getAutoFinalMode(element)
        if (autoFinalMode == AutoFinalMode.UNKNOWN) {
          callback()
          return
        }
        autoFinalStack.add(autoFinalMode)
        try {
          callback()
        }
        finally {
          autoFinalStack.removeLast()
        }
      }

      override fun visitMethod(method: GrMethod): Unit = visitWithAutoFinalCollecting(method, { super.visitMethod(method) })

      override fun visitField(field: GrField): Unit = visitWithAutoFinalCollecting(field, { super.visitField(field) })

      override fun visitModifierList(modifierList: GrModifierList) {
        if (autoFinalStack.last() != AutoFinalMode.ENABLED) return
        val owner = modifierList.parent
        if (!(owner is PsiParameter || (owner is GrVariableDeclaration && owner.variables.any { it is PsiField }))) return
        context.addModifier(modifierList, PsiModifier.FINAL)
      }

      override fun visitTypeDefinition(typeDefinition: GrTypeDefinition) {
        // Each type definition has its own TypeDefinitionMembersCache and associated modifiers
        return
      }
    })
  }
}