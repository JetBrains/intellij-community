// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.icons.AllIcons
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.ui.RowIcon
import org.jetbrains.kotlin.backend.common.descriptors.isSuspend
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.KotlinDescriptorIconProvider
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKaModule
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.completion.implCommon.ActualCompletionLookupElementDecorator
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.completion.DescriptorBasedDeclarationLookupObject
import org.jetbrains.kotlin.idea.core.expectActual.ExpectActualGenerationUtils
import org.jetbrains.kotlin.idea.highlighter.markers.toDescriptor
import org.jetbrains.kotlin.idea.search.ExpectActualUtils
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.actualsForExpected
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPackageDirective

/**
 * Handles the completion of `actual` declarations in a Kotlin project.
 *
 * This class completes `actual` declarations based on the `expect` declarations
 * in the provided project and adds them to the provided [collector].
 *
 * For example, in the following code:
 * ```
 * // example.kt
 * expect fun foo(): String
 * ```
 *
 * ```
 * // example.jvm.kt
 * actual<caret>
 * ```
 * `foo` will be suggested at the caret in the `example.jvm.kt` file.
 *
 * @see OverridesCompletion
 */
internal class ActualDeclarationCompletion(
    private val project: Project,
    private val collector: LookupElementsCollector,
    private val lookupElementFactory: BasicLookupElementFactory,
) {
    private val PRESENTATION_RENDERER = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.withOptions {
        modifiers = emptySet()
        includeAdditionalModifiers = false
    }

    fun complete(position: PsiElement, declaration: KtNamedDeclaration?) {
        val module = position.module ?: return
        val kaModule = position.moduleInfo.toKaModule()
        val dependsOnModules = kaModule.transitiveDependsOnDependencies
        if (dependsOnModules.isEmpty()) return

        val packageQualifiedName = position.packageDirective?.qualifiedName ?: return

        // TODO: Allow completion not only for top level actual declarations
        //  `expect`/`actual` interfaces, objects, annotations, enums and typealiases are in Beta
        //  https://youtrack.jetbrains.com/issue/KT-61573
        val notImplementedExpectDeclarations = ExpectActualUtils.collectTopLevelExpectDeclarations(project, dependsOnModules)
            .filter { expectDeclaration -> canImplementActualForExpect(expectDeclaration, module, packageQualifiedName) }
        val actualDeclarationLookupElements = notImplementedExpectDeclarations
            .mapNotNull { expectDeclaration -> expectDeclaration.createLookupElement(position, module, declaration) }

        actualDeclarationLookupElements.forEach { lookupElement -> collector.addElement(lookupElement) }
    }

    private fun canImplementActualForExpect(
        expectDeclaration: KtNamedDeclaration,
        targetModule: Module,
        packageFqnForActual: String,
    ): Boolean {
        val expectDeclarationPackageQualifiedName = expectDeclaration.packageDirective?.qualifiedName ?: return false
        if (expectDeclarationPackageQualifiedName != packageFqnForActual) return false

        val actualsForExpected = expectDeclaration.actualsForExpected(targetModule)
        return actualsForExpected.isEmpty()
    }

    private fun KtNamedDeclaration.createLookupElement(position: PsiElement, module: Module, declaration: KtNamedDeclaration?): LookupElement? {
        val expectDeclaration = this

        val descriptor = expectDeclaration.toDescriptor() as? CallableMemberDescriptor ?: return null

        val baseLookupElement = lookupElementFactory.createLookupElement(descriptor)
        val descriptorBasedDeclarationLookupObject = baseLookupElement.`object` as DescriptorBasedDeclarationLookupObject

        val baseClass = descriptor.containingDeclaration as? ClassDescriptor
        val baseClassName = baseClass?.name?.asString()

        val baseClassDeclaration = baseClass?.let { DescriptorToSourceUtilsIde.getAnyDeclaration(position.project, it) }
        val baseClassIcon = baseClass?.let { KotlinDescriptorIconProvider.getIcon(it, baseClassDeclaration, 0) }

        return ActualCompletionLookupElementDecorator(
            baseLookupElement,
            text = descriptor.textPresentation(),
            icon = descriptorBasedDeclarationLookupObject.iconPresentation(),
            baseClassName = baseClassName,
            baseClassIcon = baseClassIcon,
            isSuspend = descriptor.isSuspend,
            generateMember = { ExpectActualGenerationUtils.generateActualDeclaration(project, module, expectDeclaration) },
            shortenReferences = ShortenReferences.DEFAULT::process,
            declarationLookupObject = descriptorBasedDeclarationLookupObject.withOverridedDescription(),
            declaration = declaration,
        )
    }

    private fun CallableMemberDescriptor.textPresentation(): String {
        val builder = StringBuilder()
        builder.append(KtTokens.ACTUAL_KEYWORD.value)
        builder.append(" ")
        builder.append(PRESENTATION_RENDERER.render(this))

        if (this is FunctionDescriptor) {
            builder.append(" {...}")
        }

        return builder.toString()
    }

    private fun DescriptorBasedDeclarationLookupObject.iconPresentation(): RowIcon {
        val baseIcon = this.getIcon(0)
        val additionalIcon = AllIcons.Gutter.ImplementingMethod
        val icon = RowIcon(baseIcon, additionalIcon)

        return icon
    }

    // NOTE: Override `object` to avoid `expect` declaration in a lookup object (related to K1)
    private fun DescriptorBasedDeclarationLookupObject.withOverridedDescription(): DescriptorBasedDeclarationLookupObject {
        val declarationLookupObject = this
        return object : DescriptorBasedDeclarationLookupObject by declarationLookupObject {
            @Suppress("OVERRIDE_DEPRECATION")
            override val descriptor: DeclarationDescriptor? = null
        }
    }

    // Scripts have no package directive, all other files must have package directives
    private val PsiElement.packageDirective: KtPackageDirective?
        get() = (containingFile as? KtFile)?.packageDirective
}