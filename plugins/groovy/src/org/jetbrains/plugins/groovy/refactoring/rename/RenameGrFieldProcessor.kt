// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiQualifiedReference
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.modplan.ModPlanEdit
import com.intellij.refactoring.modplan.rename.ModPlanRenameUtil
import com.intellij.refactoring.rename.RenameJavaVariableProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle.message

open class RenameGrFieldProcessor : RenameJavaVariableProcessor() {

  override fun canProcessElement(element: PsiElement): Boolean = element is GrField

  override fun findReferences(element: PsiElement,
                              searchScope: SearchScope,
                              searchInCommentsAndStrings: Boolean): MutableCollection<PsiReference> {
    assert(element is GrField)

    val refs = ArrayList<PsiReference>()

    val field = element as GrField
    val setter = field.setter
    if (setter != null) {
      refs.addAll(RenameAliasedUsagesUtil.filterAliasedRefs(MethodReferencesSearch.search(setter, searchScope, true).findAll(), setter))
    }
    val getters = field.getters
    for (getter in getters) {
      refs.addAll(RenameAliasedUsagesUtil.filterAliasedRefs(MethodReferencesSearch.search(getter, searchScope, true).findAll(), getter))
    }
    refs.addAll(RenameAliasedUsagesUtil.filterAliasedRefs(ReferencesSearch.search(field, searchScope, false).findAll(), field))
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
        ref.handleElementRename(referenceNameAfterFieldRename(ref, field.name, newName))
      }
    }

    field.name = newName

    val manager = element.getManager()
    for (expression in oldResolvedRefs.keys) {
      val oldResolved = oldResolvedRefs[expression] ?: continue
      val resolved = expression.resolve() ?: continue
      if (manager.areElementsEquivalent(oldResolved, resolved)) continue
      if (oldResolved == field || isQualificationNeeded(manager, oldResolved, resolved)) {
        val qualifiedText = qualifiedReferenceText(field, expression, expression.referenceName ?: continue) ?: continue
        replaceWithQualified(expression, qualifiedText)
      }
    }

    listener?.elementRenamed(element)
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
          "usage.will.be.overridden.by.method",
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
        conflicts.putValue(newGetter, message("implicit.getter.will.by.overridden.by.method", field.name, newGetter.name))
      }
    }
    val setter = GroovyPropertyUtils.findSetterForField(field)
    if (setter is GrAccessorMethod) {
      val newSetter = PropertyUtilBase.findPropertySetter(containingClass, newName, field.hasModifierProperty(PsiModifier.STATIC), true)
      if (newSetter != null && newSetter !is GrAccessorMethod) {
        conflicts.putValue(newSetter, message("implicit.setter.will.by.overridden.by.method", field.name, newSetter.name))
      }
    }
  }
}

/**
 * @return the name that [ref], a reference to a field named [fieldName] or to an accessor of it that is no Groovy reference expression,
 * gets in the rename of the field to [newName]: the accessor name of the new name for a reference to an accessor
 */
internal fun referenceNameAfterFieldRename(ref: PsiReference, fieldName: String, newName: String): String {
  val refText = if (ref is PsiQualifiedReference) {
    ref.referenceName
  }
  else {
    ref.canonicalText
  }

  return when {
    fieldName == refText -> newName
    GroovyPropertyUtils.getGetterNameNonBoolean(fieldName) == refText -> GroovyPropertyUtils.getGetterNameNonBoolean(newName)
    GroovyPropertyUtils.getGetterNameBoolean(fieldName) == refText -> GroovyPropertyUtils.getGetterNameBoolean(newName)
    GroovyPropertyUtils.getSetterName(fieldName) == refText -> GroovyPropertyUtils.getSetterName(newName)
    else -> newName
  }
}

/**
 * @return the text of [refExpr], an unqualified reference to [member] named [referenceName], with the qualifier of [member]; null if
 * [refExpr] has a qualifier, or if [member] has no class
 */
internal fun qualifiedReferenceText(member: PsiMember, refExpr: GrReferenceExpression, referenceName: String): String? {
  val clazz = member.containingClass ?: return null
  if (refExpr.qualifierExpression != null) return null

  return when {
    member.hasModifierProperty(PsiModifier.STATIC) -> "${clazz.qualifiedName}.$referenceName"
    member.manager.areElementsEquivalent(refExpr.parentOfType<PsiClass>(), clazz) -> "this.$referenceName"
    else -> "${clazz.qualifiedName}.this.$referenceName"
  }
}

/** Replaces [refExpr] with a reference of [qualifiedText], with short class references. */
internal fun replaceWithQualified(refExpr: GrReferenceExpression, qualifiedText: String) {
  val project = refExpr.project
  val newRefExpr = GroovyPsiElementFactory.getInstance(project).createReferenceExpressionFromText(qualifiedText)
  val replaced = refExpr.replace(newRefExpr)
  JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced)
}

/**
 * The rename of a Groovy field on the ModPlan engine. It decides before the write what [RenameGrFieldProcessor.renameElement] decides:
 * the name of each reference, and the qualifier of a reference that another element takes under the new name.
 */
@ApiStatus.Internal
open class ModPlanGrFieldRenameProvider(
  processorClass: Class<out RenamePsiElementProcessor> = RenameGrFieldProcessor::class.java,
) : ModPlanGroovyRenameProvider(processorClass) {
  /**
   * The default edits give every reference the new name of the field. [RenameGrFieldProcessor.renameElement] gives each reference its
   * own name, and qualifies a reference that another element takes. The legacy test `RenameTest.propertyWithLocalCollision`:
   * ```
   * class Foo {
   *   def prop
   *   def foo() {
   *     def newName = a
   *     print prop        // the field
   *     print getProp()   // the implicit getter
   *     setProp(2)        // the implicit setter
   *     print newName     // the local variable, no usage
   *   }
   * }
   * // rename prop to newName
   * class Foo {
   *   def newName
   *   def foo() {
   *     def newName = a
   *     print this.newName   // the local variable takes newName, so the reference gets the qualifier
   *     print getNewName()   // the name of the getter, not newName
   *     setNewName(2)        // the name of the setter
   *     print newName
   *   }
   * }
   * ```
   */
  override fun referenceEdits(element: PsiElement,
                              newName: String,
                              references: List<UsageInfo>,
                              renamed: Map<PsiElement, String>): List<ModPlanEdit>? {
    val field = element as? GrField ?: return super.referenceEdits(element, newName, references, renamed)
    val result = ArrayList<ModPlanEdit>()
    for (usage in references) {
      val ref = usage.reference ?: return null
      val expression = ref as? GrReferenceExpression
      val name = if (expression != null) {
        nameAfterRename(expression, newName)
      }
      else {
        referenceNameAfterFieldRename(ref, field.name, newName)
      }
      val qualifiedText = expression?.takeIf { isHiddenAfterRename(it, name, renamed) }?.let { qualifiedReferenceText(field, it, name) }
      if (expression == null || qualifiedText == null) {
        result.addAll(ModPlanRenameUtil.referenceEdits(listOf(usage), name) ?: return null)
      }
      else {
        result.add(ModPlanEdit.update(expression) { copy -> replaceWithQualified(copy, qualifiedText) })
      }
    }
    return result
  }

  /**
   * @return the name that [reference] gets, as [RenameGrFieldProcessor.renameElement] gives it: the name of what it resolves to after the
   * rename, see [getNewNameFromTransformations]. A property access to an accessor gets the name of the property, as its
   * `handleElementRename` writes it after the rename of the accessor.
   * ```
   * class Counter { int hits }
   * counter.hits         // resolves to the getter getHits as a property: getCount, so the access gets count
   * counter.getHits()    // resolves to the getter getHits: getCount
   * // rename hits to count
   * counter.count
   * counter.getCount()
   * ```
   */
  private fun nameAfterRename(reference: GrReferenceExpression, newName: String): String {
    val result = reference.advancedResolve()
    val name = result.element.getNewNameFromTransformations(newName)
    val accessor = result.element as? PsiMethod
    if (result.isInvokedOnProperty && accessor != null) {
      RenameAliasImportedMethodProcessor.propertyNameAfterRename(accessor, name)?.let { return it }
    }
    return name
  }

  /**
   * @return whether an element named [name] that is not renamed is visible at [reference], an unqualified reference: it takes the
   * reference from the field after the rename, so [RenameGrFieldProcessor.renameElement] qualifies the reference. The legacy rename
   * resolves the reference after the write. The plan is made before the write, so it looks for the element under the new name.
   * ```
   * class Foo {
   *   def prop
   *   def foo() {
   *     def newName = a
   *     print prop        // rename prop to newName: the local variable newName is visible here, so true
   *     print getProp()   // the name getNewName: no method getNewName is visible, so false
   *   }
   * }
   * ```
   * In `RenameTest.propertyWithFieldCollision`, the field `ndame` of the inner class takes `print name`, so the reference becomes
   * `A.this.ndame`.
   */
  private fun isHiddenAfterRename(reference: GrReferenceExpression, name: String, renamed: Map<PsiElement, String>): Boolean {
    if (reference.qualifierExpression != null) return false
    val visible = if (reference.parent is GrMethodCall) {
      val processor = MethodResolverProcessor(name, reference, false, null, PsiUtil.getArgumentTypes(reference, false),
                                              reference.typeArguments)
      ResolveUtil.resolveExistingElement(reference, processor, PsiMethod::class.java)
    }
    else {
      ResolveUtil.resolveProperty(reference, name)
    }
    return visible != null && visible !in renamed
  }
}
