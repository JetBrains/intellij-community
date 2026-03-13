// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.java.generate.template.TemplateResource
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClass

/**
 * Internal extension for changing equals/hashCode generation in Kotlin classes.
 *
 * It is generally recommended to create a separate generation action instead of using this extension.
 * Use the extension only if it is necessary to implicitly affect generator clients, such as quick fixes, for a narrow set of classes.
 */
@ApiStatus.Internal
interface KotlinEqualsHashCodeGeneratorExtension {
    companion object {
        private val EP_NAME: ExtensionPointName<KotlinEqualsHashCodeGeneratorExtension> =
            ExtensionPointName.create("org.jetbrains.kotlin.equalsHashCodeGeneratorExtension")

        /**
         * Returns all applicable extensions for [klass].
         */
        fun getApplicableExtensions(klass: KtClass): List<KotlinEqualsHashCodeGeneratorExtension> =
            EP_NAME.extensionList.filter { it.isApplicable(klass) }

        /**
         * Returns a single applicable extension for [klass].
         *
         * @return The applicable extension for the class or `null` if there are no extensions or more than one matching the class.
         */
        fun getSingleApplicableFor(klass: KtClass): KotlinEqualsHashCodeGeneratorExtension? =
            getApplicableExtensions(klass).singleOrNull()
    }

    /**
     * Filters for members that should be passed for `equals`/`hashCode` generation.
     * Candidates for the filtering include members from the class scope and members from its supertypes.
     * By default, only the members from the class itself are included.
     */
    val memberFilters: MemberFilters
        get() = DefaultMemberFilters

    /**
     * Checks if the extension is applicable for a given class.
     */
    fun isApplicable(klass: KtClass): Boolean

    /**
     * Returns extra context for equals generation that will be available in the template.
     * An extension can override base context variables.
     * No more than one extension can provide context for a given class.
     * Interference between contexts of different extensions is not possible.
     */
    context(session: KaSession)
    fun extraEqualsContext(klass: KtClass): Map<String, Any>

    /**
     * Returns extra context for hashCode generation that will be available in the template.
     * An extension can override base context variables.
     * No more than one extension can provide context for a given class.
     * Interference between contexts of different extensions is not possible.
     */
    context(session: KaSession)
    fun extraHashCodeContext(klass: KtClass): Map<String, Any>

    /**
     * Provides the name of an alternative default template for equals/hashCode generation, if any.
     *
     * The alternative template is used in the `Generate` action and in the generation utility used by quickfixes.
     * The default template will be preselected during the template selection step.
     * After an action that uses the alternative template, the previous default will be restored.
     *
     * The name should follow the convention enforced by the [com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManagerBase].
     * The resulting template names will include additional 'equals' and 'hashCode' suffixes.
     * Note that the template name is not the Apache Velocity template file name.
     *
     * [TemplateResource]s for `equals` and `hashCode` with a matching name should be provided by the [getTemplatesFor].
     */
    fun alternativeDefaultTemplateFor(klass: KtClass): String?

    /**
     * Provides additional templates for equals/hashCode generation.
     *
     * The extension templates will be added to KotlinEqualsHashCodeTemplatesManager temporarily if the extension is applicable for [klass].
     * Additional templates should go in equals-hashCode pairs.
     * Their names should follow the convention enforced by the [com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManagerBase].
     */
    fun getTemplatesFor(klass: KtClass): List<TemplateResource>

    /**
     * Checks if the given template name belongs to this extension.
     * Should return `true` if the template name is present among the templates returned by [getTemplatesFor].
     * The passed [name] follows the rules of [com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManagerBase],
     * i.e., it doesn't include the 'equals' or 'hashCode' suffix.
     */
    fun isExtensionTemplate(name: String): Boolean
}

/**
 * Member filters for equals/hashCode generation.
 *
 * The filters are used to choose members from a class and its supertypes that should be passed to the generator.
 */
@ApiStatus.Internal
interface MemberFilters {
    /**
     * Checks if the given declaration is applicable for equals generation.
     * @param ktClass is the class for which the generator is invoked.
     * @param declaration is a property from [ktClass] or its supertypes.
     */
    fun isApplicableForEqualsInClass(declaration: KtNamedDeclaration, ktClass: KtClass): Boolean
    /**
     * Checks if the given declaration is applicable for hashCode generation.
     * @param ktClass is the class for which the generator is invoked.
     * @param declaration is a property from [ktClass] or its supertypes.
     */
    fun isApplicableForHashCodeInClass(declaration: KtNamedDeclaration, ktClass: KtClass): Boolean
}

/**
 * Default member filters that include only the members from the class itself.
 */
@ApiStatus.Internal
object DefaultMemberFilters : MemberFilters {
    override fun isApplicableForEqualsInClass(declaration: KtNamedDeclaration, ktClass: KtClass): Boolean =
        declaration.containingClass() == ktClass

    override fun isApplicableForHashCodeInClass(declaration: KtNamedDeclaration, ktClass: KtClass): Boolean =
        declaration.containingClass() == ktClass
}
