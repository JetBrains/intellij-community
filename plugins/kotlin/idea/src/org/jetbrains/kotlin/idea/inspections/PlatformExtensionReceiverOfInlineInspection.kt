// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptionController
import com.intellij.codeInspection.options.RegexValidator
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.quickfix.getAddExclExclCallFix
import org.jetbrains.kotlin.idea.resolve.dataFlowValueFactory
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfoBefore
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.isNullabilityFlexible
import org.jetbrains.kotlin.types.isNullable
import java.util.regex.PatternSyntaxException

class PlatformExtensionReceiverOfInlineInspection : AbstractKotlinInspection() {

    private var nameRegex: Regex? = defaultNamePattern.toRegex()
    private var namePattern: String = defaultNamePattern
        set(value) {
            field = value
            nameRegex = try {
                value.toRegex()
            } catch (e: PatternSyntaxException) {
                null
            }
        }


    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession) =
        object : KtVisitorVoid() {
            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                super.visitDotQualifiedExpression(expression)

                val languageVersionSettings = expression.languageVersionSettings
                if (!languageVersionSettings.supportsFeature(LanguageFeature.NullabilityAssertionOnExtensionReceiver)) {
                    return
                }
                val nameRegex = nameRegex
                val callExpression = expression.selectorExpression as? KtCallExpression ?: return
                val calleeText = callExpression.calleeExpression?.text ?: return
                if (nameRegex != null && !nameRegex.matches(calleeText)) {
                    return
                }

                val resolutionFacade = expression.getResolutionFacade()
                val context = expression.analyze(resolutionFacade, BodyResolveMode.PARTIAL)
                val resolvedCall = expression.getResolvedCall(context) ?: return
                val extensionReceiverType = resolvedCall.extensionReceiver?.type ?: return
                if (!extensionReceiverType.isNullabilityFlexible()) return
                val descriptor = resolvedCall.resultingDescriptor as? FunctionDescriptor ?: return
                if (!descriptor.isInline || descriptor.extensionReceiverParameter?.type?.isNullable() == true) return

                val receiverExpression = expression.receiverExpression
                val dataFlowValueFactory = resolutionFacade.dataFlowValueFactory
                val dataFlow = dataFlowValueFactory.createDataFlowValue(receiverExpression, extensionReceiverType, context, descriptor)
                val stableNullability = context.getDataFlowInfoBefore(receiverExpression).getStableNullability(dataFlow)
                if (!stableNullability.canBeNull()) return

                getAddExclExclCallFix(receiverExpression)?.let {
                    holder.registerProblem(
                        receiverExpression,
                        KotlinBundle.message("call.of.inline.function.with.nullable.extension.receiver.can.cause.npe.in.kotlin.1.2"),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        IntentionWrapper(it, receiverExpression.containingKtFile)
                    )
                }
            }
        }

    override fun getOptionsPane(): OptPane = OptPane.pane(OptPane.string("namePattern", KotlinBundle.message("text.pattern"), 30, RegexValidator()))

    override fun getOptionController(): OptionController = OptionController.empty()
        .onValue("namePattern", this::namePattern)

    companion object {
        const val defaultNamePattern = "(toBoolean)|(content.*)"
    }
}
