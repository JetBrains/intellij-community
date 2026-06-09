// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInsight.options.JavaClassValidator
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.column
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.options.OptPane.table
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.SerializationFilterBase
import com.intellij.util.xmlb.XmlSerializer
import com.siyeh.ig.BaseInspection
import org.jdom.Element
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class KotlinLoggerInitializedWithForeignClassInspection :
    KotlinApplicableInspectionBase<KtCallExpression, KotlinLoggerInitializedWithForeignClassInspection.Context>() {

    @Suppress("MemberVisibilityCanBePrivate")
    var loggerFactoryClassName: String = DEFAULT_LOGGER_FACTORY_CLASS_NAME

    @Suppress("MemberVisibilityCanBePrivate")
    var loggerFactoryMethodName: String = DEFAULT_LOGGER_FACTORY_METHOD_NAME

    private val loggerFactoryClassNames = DEFAULT_LOGGER_FACTORY_CLASS_NAMES.toMutableList()
    private val loggerFactoryMethodNames = DEFAULT_LOGGER_FACTORY_METHOD_NAMES.toMutableList()
    private val loggerFactoryFqNames
        get() = loggerFactoryClassNames.zip(loggerFactoryMethodNames).groupBy(
            { (_, methodName) -> methodName },
            { (className, methodName) -> FqName("$className.$methodName") }
        )

    internal data class Context(
        val classLiteralName: String,
        val containingClassName: String,
    )

    override fun getOptionsPane(): OptPane {
        return pane(
            table(
                "",
                column(
                    "loggerFactoryClassNames", KotlinBundle.message("title.logger.factory.class.name"),
                    JavaClassValidator().withTitle(KotlinBundle.message("title.choose.logger.factory.class"))
                ),
                column("loggerFactoryMethodNames", KotlinBundle.message("title.logger.factory.method.name"))
            )
        )
    }

    override fun readSettings(element: Element) {
        super.readSettings(element)
        BaseInspection.parseString(loggerFactoryClassName, loggerFactoryClassNames)
        BaseInspection.parseString(loggerFactoryMethodName, loggerFactoryMethodNames)
        if (loggerFactoryClassNames.isEmpty() || loggerFactoryClassNames.size != loggerFactoryMethodNames.size) {
            BaseInspection.parseString(DEFAULT_LOGGER_FACTORY_CLASS_NAME, loggerFactoryClassNames)
            BaseInspection.parseString(DEFAULT_LOGGER_FACTORY_METHOD_NAME, loggerFactoryMethodNames)
        }
    }

    override fun writeSettings(element: Element) {
        loggerFactoryClassName = BaseInspection.formatString(loggerFactoryClassNames)
        loggerFactoryMethodName = BaseInspection.formatString(loggerFactoryMethodNames)
        XmlSerializer.serializeInto(this, element, object : SerializationFilterBase() {
            override fun accepts(accessor: Accessor, bean: Any, beanValue: Any?): Boolean {
                if (accessor.name == "loggerFactoryClassName" && beanValue == DEFAULT_LOGGER_FACTORY_CLASS_NAME) return false
                if (accessor.name == "loggerFactoryMethodName" && beanValue == DEFAULT_LOGGER_FACTORY_METHOD_NAME) return false
                return true
            }
        })
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean = element.foreignClassLiteral() != null

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> {
        val classLiteral = element.foreignClassLiteral()?.classLiteral ?: return emptyList()
        return listOf(TextRange.from(classLiteral.textRange.startOffset - element.textRange.startOffset, classLiteral.textLength))
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val (_, classLiteralName, containingClassName) = element.foreignClassLiteral() ?: return null

        val callee = element.calleeExpression ?: return null
        val loggerMethodFqNames = loggerFactoryFqNames[callee.text] ?: return null
        val methodFqName = element.resolveToCall()
            ?.successfulFunctionCallOrNull()
            ?.symbol
            ?.callableId
            ?.asSingleFqName()
            ?: return null
        if (methodFqName !in loggerMethodFqNames) return null

        return Context(classLiteralName, containingClassName)
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtCallExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor = createProblemDescriptor(
        element,
        rangeInElement,
        KotlinBundle.message("logger.initialized.with.foreign.class", "${context.classLiteralName}::class"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        onTheFly,
        ReplaceForeignFix(context.containingClassName),
    )

    private fun KtCallExpression.foreignClassLiteral(): ForeignClassLiteral? {
        val containingClassNames = containingClassNames()
        if (containingClassNames.isEmpty()) return null

        val callee = calleeExpression ?: return null
        if (loggerFactoryFqNames[callee.text] == null) return null

        val argument = valueArguments.singleOrNull()?.getArgumentExpression() as? KtDotQualifiedExpression ?: return null
        val argumentSelector = argument.selectorExpression ?: return null
        val classLiteral = when (val argumentReceiver = argument.receiverExpression) {
            // Foo::class.java, Foo::class.qualifiedName, Foo::class.simpleName
            is KtClassLiteralExpression -> {
                val selectorText = (argumentSelector as? KtNameReferenceExpression)?.text
                if (selectorText !in listOf("java", "qualifiedName", "simpleName")) return null
                argumentReceiver
            }
            // Foo::class.java.name, Foo::class.java.simpleName, Foo::class.java.canonicalName
            is KtDotQualifiedExpression -> {
                val classLiteral = argumentReceiver.receiverExpression as? KtClassLiteralExpression ?: return null
                if ((argumentReceiver.selectorExpression as? KtNameReferenceExpression)?.text != "java") return null
                val selectorText = (argumentSelector as? KtNameReferenceExpression)?.text
                    ?: (argumentSelector as? KtCallExpression)?.calleeExpression?.text
                if (selectorText !in listOf("name", "simpleName", "canonicalName", "getName", "getSimpleName", "getCanonicalName")) {
                    return null
                }
                classLiteral
            }

            else -> return null
        }
        val classLiteralName = classLiteral.receiverExpression?.text ?: return null
        if (classLiteralName in containingClassNames) return null

        return ForeignClassLiteral(classLiteral, classLiteralName, containingClassNames.last())
    }

    private fun KtCallExpression.containingClassNames(): List<String> {
        val classOrObject = getStrictParentOfType<KtClassOrObject>() ?: return emptyList()
        return if (classOrObject is KtObjectDeclaration && classOrObject.isCompanion()) {
            listOfNotNull(classOrObject.name, classOrObject.containingClass()?.name)
        } else {
            listOfNotNull(classOrObject.name)
        }
    }

    private class ReplaceForeignFix(private val containingClassName: String) : KotlinModCommandQuickFix<KtCallExpression>() {
        override fun getFamilyName() = KotlinBundle.message("logger.initialized.with.foreign.class.fix.family")

        override fun getName(): String = KotlinBundle.message("replace.with.0", "$containingClassName::class")

        override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
            val argument = element.valueArguments.singleOrNull()?.getArgumentExpression() as? KtDotQualifiedExpression ?: return
            val receiver = argument.receiverExpression
            val selector = argument.selectorExpression ?: return
            val psiFactory = KtPsiFactory(project)
            val newArgument = when (receiver) {
                is KtClassLiteralExpression -> {
                    psiFactory.createExpressionByPattern("${containingClassName}::class.$0", selector)
                }

                is KtDotQualifiedExpression -> {
                    psiFactory.createExpressionByPattern("${containingClassName}::class.java.$0", selector)
                }

                else -> return
            }
            argument.replace(newArgument)
        }
    }

    private data class ForeignClassLiteral(
        val classLiteral: KtClassLiteralExpression,
        val classLiteralName: String,
        val containingClassName: String,
    )
}

private val DEFAULT_LOGGER_FACTORIES = listOf(
    "java.util.logging.Logger" to "getLogger",
    "org.slf4j.LoggerFactory" to "getLogger",
    "org.apache.commons.logging.LogFactory" to "getLog",
    "org.apache.log4j.Logger" to "getLogger",
    "org.apache.logging.log4j.LogManager" to "getLogger",
)
private val DEFAULT_LOGGER_FACTORY_CLASS_NAMES = DEFAULT_LOGGER_FACTORIES.map { (className, _) -> className }
private val DEFAULT_LOGGER_FACTORY_METHOD_NAMES = DEFAULT_LOGGER_FACTORIES.map { (_, methodName) -> methodName }
private val DEFAULT_LOGGER_FACTORY_CLASS_NAME = BaseInspection.formatString(DEFAULT_LOGGER_FACTORY_CLASS_NAMES)
private val DEFAULT_LOGGER_FACTORY_METHOD_NAME = BaseInspection.formatString(DEFAULT_LOGGER_FACTORY_METHOD_NAMES)
