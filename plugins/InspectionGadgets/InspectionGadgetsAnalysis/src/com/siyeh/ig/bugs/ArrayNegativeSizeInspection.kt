/*
 * Copyright 2020 Ivo Smid
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
package com.siyeh.ig.bugs

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiNewExpression
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.BaseInspection
import com.siyeh.ig.BaseInspectionVisitor

class ArrayNegativeSizeInspection : BaseInspection() {
  override fun buildErrorString(vararg infos: Any?): String =
    InspectionGadgetsBundle.message("array.allocation.negative.length.problem.descriptor", infos[0])

  override fun isEnabledByDefault(): Boolean {
    return true
  }

  override fun buildVisitor(): BaseInspectionVisitor {
    return ArrayNegativeSizeVisitor()
  }

  private class ArrayNegativeSizeVisitor : BaseInspectionVisitor() {
    override fun visitNewExpression(expression: PsiNewExpression?) {
      super.visitNewExpression(expression)

      if (expression == null) {
        return
      }

      val evaluationHelper = JavaPsiFacade.getInstance(expression.project).constantEvaluationHelper
      expression.arrayDimensions.asSequence()
        .map {
          val constValue = evaluationHelper.computeConstantExpression(it)
          (it ?: error("null expression not expected")) to (constValue as? Number)?.toInt()
        }
        .filter {
          val num = it.second;
          return@filter num != null && num < 0
        }
        .forEach { (expr, constValue) ->
          registerError(expr, constValue)
        }
    }
  }
}