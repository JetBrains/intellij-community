// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.elements

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.lang.jvm.types.JvmSubstitutor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.PsiModifier.ModifierConstant
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SubtypeConstraint
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass

internal typealias ExpectedParameters = List<ExpectedParameter>

@ModifierConstant
internal fun JvmModifier.toPsiModifier(): String = when (this) {
  JvmModifier.PUBLIC -> PsiModifier.PUBLIC
  JvmModifier.PROTECTED -> PsiModifier.PROTECTED
  JvmModifier.PRIVATE -> PsiModifier.PRIVATE
  JvmModifier.PACKAGE_LOCAL -> PsiModifier.PACKAGE_LOCAL
  JvmModifier.STATIC -> PsiModifier.STATIC
  JvmModifier.ABSTRACT -> PsiModifier.ABSTRACT
  JvmModifier.FINAL -> PsiModifier.FINAL
  JvmModifier.NATIVE -> PsiModifier.NATIVE
  JvmModifier.SYNCHRONIZED -> PsiModifier.NATIVE
  JvmModifier.STRICTFP -> PsiModifier.STRICTFP
  JvmModifier.TRANSIENT -> PsiModifier.TRANSIENT
  JvmModifier.VOLATILE -> PsiModifier.VOLATILE
  JvmModifier.TRANSITIVE -> PsiModifier.TRANSITIVE
}

/**
 * Compiled classes, type parameters, light classes(except GroovyScriptClass) are not considered classes.
 *
 * @return GrTypeDefinition or `null` if the receiver is not a Groovy type definition.
 */
internal fun JvmClass.toGroovyClassOrNull(): GrTypeDefinition? {
  if (this !is GrTypeDefinition) return null
  if (this is PsiTypeParameter) return null
  if (this is ClsClassImpl) return null
  if (this is GroovyScriptClass) return this
  if (this is LightElement) return null
  return this
}

internal val visibilityModifiers = setOf(
  JvmModifier.PUBLIC,
  JvmModifier.PROTECTED,
  JvmModifier.PACKAGE_LOCAL,
  JvmModifier.PRIVATE
)

internal fun createConstraints(project: Project, expectedTypes: ExpectedTypes): List<TypeConstraint> {
  return expectedTypes.mapNotNull {
    toTypeConstraint(project, it)
  }
}

private fun toTypeConstraint(project: Project, expectedType: ExpectedType): TypeConstraint? {
  val helper = JvmPsiConversionHelper.getInstance(project)
  val psiType = helper.convertType(expectedType.theType)
  return if (expectedType.theKind == ExpectedType.Kind.SUPERTYPE) SupertypeConstraint.create(psiType) else SubtypeConstraint.create(psiType)
}

internal fun JvmSubstitutor.toPsiSubstitutor(project: Project): PsiSubstitutor {
  return JvmPsiConversionHelper.getInstance(project).convertSubstitutor(this)
}

internal fun CreateMethodRequest.createPropertyTypeConstraints(kind: PropertyKind): ExpectedTypes {
  return when (kind) {
    PropertyKind.GETTER -> returnType
    PropertyKind.BOOLEAN_GETTER -> listOf(expectedType(PsiType.BOOLEAN))
    PropertyKind.SETTER -> expectedParameters.single().expectedTypes
  }
}
