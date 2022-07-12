// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators

import com.intellij.psi.PsiComment
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicator
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace

object AddArgumentNamesApplicators {
    class SingleArgumentInput(val name: Name) : KotlinApplicatorInput

    class MultipleArgumentsInput(val argumentNames: Map<KtValueArgument, Name>) : KotlinApplicatorInput

    val singleArgumentApplicator = applicator<KtValueArgument, SingleArgumentInput> {
        familyName(KotlinBundle.lazyMessage("add.name.to.argument"))

        actionName { _, input -> KotlinBundle.message("add.0.to.argument", input.name)  }

        isApplicableByPsi { element ->
            // Not applicable when lambda is trailing lambda after argument list (e.g., `run {  }`); element is a KtLambdaArgument.
            // Note: IS applicable when lambda is inside an argument list (e.g., `run({  })`); element is a KtValueArgument in this case.
            !element.isNamed() && element !is KtLambdaArgument
        }

        applyTo { element, input ->
            val argumentExpression = element.getArgumentExpression() ?: return@applyTo
            val name = input.name

            val prevSibling = element.getPrevSiblingIgnoringWhitespace()
            if (prevSibling is PsiComment && """/\*\s*$name\s*=\s*\*/""".toRegex().matches(prevSibling.text)) {
                prevSibling.delete()
            }

            val newArgument = KtPsiFactory(element).createArgument(argumentExpression, name, element.getSpreadElement() != null)
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