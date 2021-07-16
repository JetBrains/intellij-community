// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.applicators

import com.intellij.psi.PsiComment
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.HLApplicatorInput
import org.jetbrains.kotlin.idea.api.applicator.applicator
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace

object AddArgumentNamesApplicators {
    class SingleArgumentInput(val name: Name) : HLApplicatorInput

    class MultipleArgumentsInput(val argumentNames: Map<KtValueArgument, Name>) : HLApplicatorInput

    val singleArgumentApplicator = applicator<KtValueArgument, SingleArgumentInput> {
        familyName(KotlinBundle.lazyMessage("add.name.to.argument"))

        actionName { _, input -> KotlinBundle.message("add.0.to.argument", input.name)  }

        isApplicableByPsi { element ->
            // Not applicable when lambda is trailing lambda after argument list (e.g., `run {  }`); element is a KtLambdaArgument.
            // Note: IS applicable when lambda is inside an argument list (e.g., `run({  })`); element is a KtValueArgument in this case.
            !element.isNamed() && element !is KtLambdaArgument
        }

        applyTo { element, input ->
            val expression = element.getArgumentExpression() ?: return@applyTo
            val name = input.name

            val prevSibling = element.getPrevSiblingIgnoringWhitespace()
            if (prevSibling is PsiComment && """/\*\s*$name\s*=\s*\*/""".toRegex().matches(prevSibling.text)) {
                prevSibling.delete()
            }

            val newArgument = KtPsiFactory(element).createArgument(expression, name, element.getSpreadElement() != null)
            element.replace(newArgument)
        }
    }

    val multipleArgumentsApplicator = applicator<KtCallElement, MultipleArgumentsInput> {
        familyAndActionName(KotlinBundle.lazyMessage("add.names.to.call.arguments"))

        isApplicableByPsi { element ->
            // Note: `KtCallElement.valueArgumentList` only includes arguments inside parentheses; it doesn't include a trailing lambda.
            element.valueArgumentList?.arguments?.any { !it.isNamed() } ?: false
        }

        applyTo { element, input, project, editor ->
            for ((argument, name) in input.argumentNames) {
                singleArgumentApplicator.applyTo(argument, SingleArgumentInput(name), project, editor)
            }
        }
    }
}