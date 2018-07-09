// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.rename

import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenameJavaVariableProcessor
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle.message
import java.util.*
import kotlin.collections.HashMap

/**
 * @author ilyas
 */
open class RenameGrFieldProcessor : RenameJavaVariableProcessor() {

  override fun canProcessElement(element: PsiElement): Boolean = element is GrField

  override fun findReferences(element: PsiElement): Collection<PsiReference> {
    assert(element is GrField)

    val refs = ArrayList<PsiReference>()

    val field = element as GrField
    val projectScope = GlobalSearchScope.projectScope(element.getProject())
    val setter = field.setter
    if (setter != null) {
      refs.addAll(RenameAliasedUsagesUtil.filterAliasedRefs(MethodReferencesSearch.search(setter, projectScope, true).findAll(), setter))
    }
    val getters = field.getters
    for (getter in getters) {
      refs.addAll(RenameAliasedUsagesUtil.filterAliasedRefs(MethodReferencesSearch.search(getter, projectScope, true).findAll(), getter))
    }
    refs.addAll(RenameAliasedUsagesUtil.filterAliasedRefs(ReferencesSearch.search(field, projectScope, false).findAll(), field))
    return refs
  }

  override fun renameElement(element: PsiElement,
                             newName: String,
                             usages: Array<out UsageInfo>,
                             listener: RefactoringElementListener?) {
    val oldResolvedRefs = HashMap<GrReferenceExpression, PsiElement>()
    for (usage in usages) {
      val ref = usage.reference as? GrReferenceExpression ?: continue
      val resovled = ref.resolve()
      oldResolvedRefs[ref] = resovled ?: continue
    }

    val field = element as GrField

    for (usage in usages) {
      val ref = usage.reference
      if (ref is GrReferenceExpression) {
        val resolved = oldResolvedRefs[ref]
        ref.handleElementRename(resolved.getNewNameFromTransformations(newName))
      }
      else if (ref != null) {
        handleElementRename(newName, ref, field.name)
      }
    }

    field.name = newName

    val manager = element.getManager()
    for (expression in oldResolvedRefs.keys) {
      val oldResolved = oldResolvedRefs[expression] ?: continue
      val resolved = expression.resolve() ?: continue
      if (manager.areElementsEquivalent(oldResolved, resolved)) continue
      if (oldResolved == field || isQualificationNeeded(manager, oldResolved, resolved)) {
        qualify(field, expression)
      }
    }

    listener?.elementRenamed(element)
  }

  private fun handleElementRename(newName: String, ref: PsiReference, fieldName: String) {
    val refText = if (ref is PsiQualifiedReference) {
      ref.referenceName
    }
    else {
      ref.canonicalText
    }

    val toRename: String = when {
      fieldName == refText -> newName
      GroovyPropertyUtils.getGetterNameNonBoolean(fieldName) == refText -> GroovyPropertyUtils.getGetterNameNonBoolean(newName)
      GroovyPropertyUtils.getGetterNameBoolean(fieldName) == refText -> GroovyPropertyUtils.getGetterNameBoolean(newName)
      GroovyPropertyUtils.getSetterName(fieldName) == refText -> GroovyPropertyUtils.getSetterName(newName)
      else -> newName
    }
    ref.handleElementRename(toRename)
  }

  private fun qualify(member: PsiMember, refExpr: GrReferenceExpression) {
    val referenceName = refExpr.referenceName ?: return
    val clazz = member.containingClass ?: return
    if (refExpr.qualifierExpression != null) return

    val manager = member.manager
    val project = manager.project

    val newText = when {
      member.hasModifierProperty(PsiModifier.STATIC) -> "${clazz.qualifiedName}.$referenceName"
      manager.areElementsEquivalent(refExpr.parentOfType<PsiClass>(), clazz) -> "this.$referenceName"
      else -> "${clazz.qualifiedName}.this.$referenceName"
    }

    val newRefExpr = GroovyPsiElementFactory.getInstance(project).createReferenceExpressionFromText(newText)
    val replaced = refExpr.replace(newRefExpr)
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced)
  }

  override fun findCollisions(element: PsiElement,
                              newName: String,
                              allRenames: Map<out PsiElement, String>,
                              result: MutableList<UsageInfo>) {
    val collisions = ArrayList<UsageInfo>()
    for (info in result) {
      if (info !is MoveRenameUsageInfo) continue
      val refExpr = info.getElement() as? GrReferenceExpression ?: continue
      if (refExpr.parent !is GrCall) continue

      val referencedElement = info.referencedElement
      if (!(referencedElement is GrField || refExpr.advancedResolve().isInvokedOnProperty)) continue

      val argTypes = PsiUtil.getArgumentTypes(refExpr, false)
      val typeArguments = refExpr.typeArguments
      val processor = MethodResolverProcessor(newName, refExpr, false, null, argTypes, typeArguments)
      val resolved = ResolveUtil.resolveExistingElement(refExpr, processor, PsiMethod::class.java) ?: continue

      collisions.add(object : UnresolvableCollisionUsageInfo(resolved, refExpr) {
        override fun getDescription(): String = message(
          "usage.will.be.overriden.by.method",
          refExpr.parent.text,
          PsiFormatUtil.formatMethod(resolved, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME, PsiFormatUtilBase.SHOW_TYPE)
        )
      })
    }
    result.addAll(collisions)
    super.findCollisions(element, newName, allRenames, result)
  }

  override fun findExistingNameConflicts(element: PsiElement, newName: String, conflicts: MultiMap<PsiElement, String>) {
    super.findExistingNameConflicts(element, newName, conflicts)

    val field = element as GrField
    val containingClass = field.containingClass ?: return

    val getter = GroovyPropertyUtils.findGetterForField(field)
    if (getter is GrAccessorMethod) {
      val newGetter = PropertyUtilBase.findPropertyGetter(containingClass, newName, field.hasModifierProperty(PsiModifier.STATIC), true)
      if (newGetter != null && newGetter !is GrAccessorMethod) {
        conflicts.putValue(newGetter, message("implicit.getter.will.by.overriden.by.method", field.name, newGetter.name))
      }
    }
    val setter = GroovyPropertyUtils.findSetterForField(field)
    if (setter is GrAccessorMethod) {
      val newSetter = PropertyUtilBase.findPropertySetter(containingClass, newName, field.hasModifierProperty(PsiModifier.STATIC), true)
      if (newSetter != null && newSetter !is GrAccessorMethod) {
        conflicts.putValue(newSetter, message("implicit.setter.will.by.overriden.by.method", field.name, newSetter.name))
      }
    }
  }
}
