// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.types.Variance
import java.util.Locale.getDefault

object OverrideAccessorFunctionFixFactory {
    @OptIn(KaExperimentalApi::class)
    val nothingToOverrideFixFactory: KotlinQuickFixFactory.ModCommandBased<KaFirDiagnostic.NothingToOverride> = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NothingToOverride ->
        val property =
            (diagnostic.psi as? KtProperty)?.takeIf { it.hasModifier(KtTokens.OVERRIDE_KEYWORD) } ?: return@ModCommandBased emptyList()
        val propertyName = property.capitalizedName ?: return@ModCommandBased emptyList()

        val getterIdentifiers = listOf("get$propertyName", "is$propertyName").mapTo(hashSetOf(),Name::identifier)
        val setterIdentifiers = listOf("set$propertyName").mapTo(hashSetOf(),Name::identifier)

        val containingClassOrObject = property.parent.parent as? KtClassOrObject ?: return@ModCommandBased emptyList()
        analyze(containingClassOrObject) {
            val classSymbol = containingClassOrObject.symbol as KaClassLikeSymbol
            for (superType in classSymbol.defaultType.allSupertypes) {
                val callables = superType.expandedSymbol?.declaredMemberScope
                    ?.callables?.filterIsInstance<KaFunctionSymbol>() ?: return@analyze false
                val overridden = buildList {
                    addIfNotNull(callables.firstOrNull { it.name in getterIdentifiers && it.valueParameters.isEmpty() })
                    addIfNotNull(callables.firstOrNull { it.name in setterIdentifiers && it.valueParameters.size == 1 })
                }
                if (overridden.isNotEmpty()) {
                    val first = overridden.first()
                    val type = if (first.name in getterIdentifiers) first.returnType else first.valueParameters.first().returnType
                    return@ModCommandBased listOf(
                        PropertyToAccessors(
                            property,
                            type.render(
                                KaTypeRendererForSource.WITH_QUALIFIED_NAMES,
                                position = Variance.OUT_VARIANCE
                            )
                        )
                    )
                }
            }
        }
        emptyList()
    }

    private val KtProperty.capitalizedName: String?
        get() = name?.let { name -> name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() } }


    private class PropertyToAccessors(element: KtProperty, private val propertyTypeWithFqNames: String) :
        PsiUpdateModCommandAction<KtProperty>(element) {

        override fun getPresentation(context: ActionContext, element: KtProperty): Presentation =
            Presentation.of(KotlinBundle.message("make.override.accessor.function.0", element.name.toString()))

        override fun getFamilyName(): String = KotlinBundle.message("override.accessor.functions.instead")

        override fun invoke(context: ActionContext, element: KtProperty, updater: ModPsiUpdater) {
            val propertyName = element.capitalizedName ?: return

            val factory = KtPsiFactory(context.project)
            element.setter?.let { setter ->
                val body = setter.bodyBlockExpression?.text ?: setter.bodyExpression?.text?.let { ": Unit = $it" } ?: "{}"
                val newSetter =
                    factory.createFunction("${element.modifierList?.text.orEmpty()} fun set$propertyName(value: $propertyTypeWithFqNames) $body")
                val ktElement = element.parent.addAfter(newSetter, element) as KtElement
                shortenReferences(ktElement)
            }

            element.getter?.let { getter ->
                val body = getter.bodyBlockExpression ?: getter.bodyExpression?.text?.let { "= $it" }
                val newGetter =
                    factory.createFunction("${element.modifierList?.text.orEmpty()} fun get$propertyName(): $propertyTypeWithFqNames $body")
                val ktElement = element.parent.addAfter(newGetter, element) as KtElement
                shortenReferences(ktElement)
            }

            element.delete()
        }

    }
}