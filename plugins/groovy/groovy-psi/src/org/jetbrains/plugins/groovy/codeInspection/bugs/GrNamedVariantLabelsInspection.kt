// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs

import com.intellij.psi.CommonClassNames
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil
import org.jetbrains.plugins.groovy.transformations.impl.namedVariant.GROOVY_TRANSFORM_NAMED_DELEGATE
import org.jetbrains.plugins.groovy.transformations.impl.namedVariant.GROOVY_TRANSFORM_NAMED_PARAM
import org.jetbrains.plugins.groovy.transformations.impl.namedVariant.NamedVariantTransformationSupport

class GrNamedVariantLabelsInspection : BaseInspection() {

  override fun buildErrorString(vararg args: Any?): String {
    return GroovyBundle.message("inspection.message.label.name.ref.not.supported.by.0", args[0])
  }

  override fun buildVisitor(): BaseInspectionVisitor = object : BaseInspectionVisitor() {
    override fun visitCallExpression(callExpression: GrCallExpression) {
      val namedArguments = callExpression.namedArguments.takeIf { it.isNotEmpty() } ?: return
      val resolvedMethod = callExpression.resolveMethod() as? GrMethod ?: return
      val mapParameter = resolvedMethod.parameters.singleOrNull { InheritanceUtil.isInheritor(it.type, CommonClassNames.JAVA_UTIL_MAP) } ?: return
      val definedNames =
        mapParameter.annotations
          .filter { it.hasQualifiedName(GROOVY_TRANSFORM_NAMED_PARAM) || it.hasQualifiedName(GROOVY_TRANSFORM_NAMED_DELEGATE) }
          .mapNotNullTo(HashSet()) { GrAnnotationUtil.inferStringAttribute(it, "value") }.takeIf { it.size > 0 } ?: return
      for (namedArg in namedArguments) {
        val label = namedArg.label ?: continue
        if (namedArg?.labelName in definedNames) continue
        val reason = if (resolvedMethod is NamedVariantTransformationSupport.NamedVariantGeneratedMethod) "@NamedVariant" else "@NamedParam"
        registerError(label, reason)
      }
    }
  }
}