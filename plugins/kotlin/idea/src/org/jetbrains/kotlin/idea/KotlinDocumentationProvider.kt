// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import com.google.common.html.HtmlEscapers
import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.codeInsight.javadoc.JavaDocExternalFilter
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.CompositeDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.lang.documentation.ExternalDocumentationProvider
import com.intellij.lang.java.JavaDocumentationProvider
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.compiled.ClsMethodImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.elements.KtLightDeclaration
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.completion.DescriptorBasedDeclarationLookupObject
import org.jetbrains.kotlin.idea.decompiler.navigation.SourceNavigationHelper
import org.jetbrains.kotlin.idea.kdoc.*
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.appendCodeSnippetHighlightedByLexer
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.appendHighlighted
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.createHighlightingManager
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.generateJavadoc
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.highlight
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.renderKDoc
import org.jetbrains.kotlin.idea.kdoc.KDocTemplate.DescriptionBodyTemplate
import org.jetbrains.kotlin.idea.parameterInfo.KotlinIdeDescriptorRenderer
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveToDescriptors
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.resolve.frontendService
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.NULLABILITY_ANNOTATIONS
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.renderer.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.deprecation.DeprecationResolver
import org.jetbrains.kotlin.resolve.deprecation.deprecatedByAnnotationReplaceWithExpression
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isDefinitelyNotNullType
import org.jetbrains.kotlin.utils.addToStdlib.constant
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.function.Consumer

class HtmlClassifierNamePolicy(val base: ClassifierNamePolicy) : ClassifierNamePolicyEx {

    override fun renderClassifier(classifier: ClassifierDescriptor, renderer: DescriptorRenderer): String =
        render(classifier, renderer, null)

    override fun renderClassifierWithType(classifier: ClassifierDescriptor, renderer: DescriptorRenderer, type: KotlinType): String =
        render(classifier, renderer, type)

    private fun render(classifier: ClassifierDescriptor, renderer: DescriptorRenderer, type: KotlinType?): String {
        if (DescriptorUtils.isAnonymousObject(classifier)) {

            val supertypes = classifier.typeConstructor.supertypes
            return buildString {
                append("&lt;anonymous object")
                if (supertypes.isNotEmpty()) {
                    append(" : ")
                    supertypes.joinTo(this) {
                        val ref = it.constructor.declarationDescriptor
                        if (ref != null)
                            renderClassifier(ref, renderer)
                        else
                            "&lt;ERROR CLASS&gt;"
                    }
                }
                append("&gt;")
            }
        }

        val name =
            base.renderClassifier(classifier, renderer) + (type?.takeIf { it.isDefinitelyNotNullType }?.let { " & Any" } ?: "")

        return buildString {
            val ref = classifier.fqNameUnsafe.toString()
            DocumentationManagerUtil.createHyperlink(this, ref, name, true)
        }
    }
}

class WrapValueParameterHandler(val base: DescriptorRenderer.ValueParametersHandler) : DescriptorRenderer.ValueParametersHandler {


    override fun appendBeforeValueParameters(parameterCount: Int, builder: StringBuilder) {
        base.appendBeforeValueParameters(parameterCount, builder)
    }

    override fun appendBeforeValueParameter(
        parameter: ValueParameterDescriptor,
        parameterIndex: Int,
        parameterCount: Int,
        builder: StringBuilder
    ) {
        builder.append("\n    ")
        base.appendBeforeValueParameter(parameter, parameterIndex, parameterCount, builder)
    }

    override fun appendAfterValueParameter(
        parameter: ValueParameterDescriptor,
        parameterIndex: Int,
        parameterCount: Int,
        builder: StringBuilder
    ) {
        if (parameterIndex != parameterCount - 1) {
            builder.append(",")
        }
    }

    override fun appendAfterValueParameters(parameterCount: Int, builder: StringBuilder) {
        if (parameterCount > 0) {
            builder.appendLine()
        }
        base.appendAfterValueParameters(parameterCount, builder)
    }
}

class KotlinDocumentationProvider : AbstractDocumentationProvider(), ExternalDocumentationProvider {

    override fun collectDocComments(file: PsiFile, sink: Consumer<in PsiDocCommentBase>) {
        if (file !is KtFile) return

        PsiTreeUtil.processElements(file) {
            val comment = (it as? KtDeclaration)?.docComment
            if (comment != null) sink.accept(comment)
            true
        }
    }

    @Nls
    override fun generateRenderedDoc(comment: PsiDocCommentBase): String? {
        val docComment = comment as? KDoc ?: return null

        val result = StringBuilder().also {
            it.renderKDoc(docComment.getDefaultSection(), docComment.getAllSections())
        }

        @Suppress("HardCodedStringLiteral")
        return JavaDocExternalFilter.filterInternalDocInfo(result.toString())
    }

    override fun getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement?, targetOffset: Int): PsiElement? {
        return if (contextElement.isModifier()) contextElement else null
    }

    @Nls
    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        return if (element == null) null else getText(element, originalElement, true)
    }

    @Nls
    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        return getText(element, originalElement, false)
    }

    override fun getDocumentationElementForLink(psiManager: PsiManager, link: String, context: PsiElement?): PsiElement? {
        val navElement = context?.navigationElement as? KtElement ?: return null
        val resolutionFacade = navElement.getResolutionFacade()
        val bindingContext = navElement.safeAnalyzeNonSourceRootCode(resolutionFacade, BodyResolveMode.PARTIAL)
        val contextDescriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, navElement] ?: return null
        val descriptors = resolveKDocLink(
            bindingContext, resolutionFacade,
            contextDescriptor, navElement, null, link.split('.')
        )
        val target = descriptors.firstOrNull() ?: return null
        return DescriptorToSourceUtilsIde.getAnyDeclaration(psiManager.project, target)
    }

    override fun getDocumentationElementForLookupItem(psiManager: PsiManager, `object`: Any?, element: PsiElement?): PsiElement? {
        if (`object` is DescriptorBasedDeclarationLookupObject) {
            `object`.psiElement?.let { return it }
            `object`.descriptor?.let { descriptor ->
                return DescriptorToSourceUtilsIde.getAnyDeclaration(psiManager.project, descriptor)
            }
        }
        return null
    }

    override fun getUrlFor(element: PsiElement?, originalElement: PsiElement?): List<String>? {
        return KotlinExternalDocUrlsProvider.getExternalJavaDocUrl(element)
    }

    override fun fetchExternalDocumentation(
        project: Project?,
        element: PsiElement?,
        docUrls: List<String>?,
        onHover: Boolean
    ): String? {
        if (docUrls == null
            || project == null
            || element == null
            || !runReadAction { element.language }.`is`(KotlinLanguage.INSTANCE)
        ) {
            return null
        }
        val docFilter = KotlinDocExtractorFromJavaDoc(project)
        for (docURL in docUrls) {
            try {
                val externalDoc = docFilter.getExternalDocInfoForElement(docURL, element)
                if (!StringUtil.isEmpty(externalDoc)) {
                    return externalDoc
                }
            } catch (ignored: ProcessCanceledException) {
                break
            } catch (e: IndexNotReadyException) {
                throw e
            } catch (e: HttpRequests.HttpStatusException) {
                logger<KotlinDocumentationProvider>().info(e.url + ": " + e.statusCode)
            } catch (e: Exception) {
                logger<KotlinDocumentationProvider>().info(e)
            }
        }
        return null
    }

    @Deprecated("Deprecated in Java")
    override fun hasDocumentationFor(element: PsiElement?, originalElement: PsiElement?): Boolean {
        return CompositeDocumentationProvider.hasUrlsFor(this, element, originalElement)
    }

    override fun canPromptToConfigureDocumentation(element: PsiElement?): Boolean {
        return false
    }

    override fun promptToConfigureDocumentation(element: PsiElement?) {
        // do nothing
    }

    private object Lazy {
        val javaDocumentProvider = JavaDocumentationProvider()

        val DESCRIPTOR_RENDERER = KotlinIdeDescriptorRenderer.withOptions {
            textFormat = RenderingFormat.HTML
            modifiers = DescriptorRendererModifier.ALL
            classifierNamePolicy = HtmlClassifierNamePolicy(ClassifierNamePolicy.SHORT)
            valueParametersHandler = WrapValueParameterHandler(valueParametersHandler)
            annotationArgumentsRenderingPolicy = AnnotationArgumentsRenderingPolicy.UNLESS_EMPTY
            renderCompanionObjectName = true
            renderPrimaryConstructorParametersAsProperties = true
            withDefinedIn = false
            eachAnnotationOnNewLine = true
            excludedTypeAnnotationClasses = NULLABILITY_ANNOTATIONS
            defaultParameterValueRenderer = { (it.source.getPsi() as? KtParameter)?.defaultValue?.text ?: "..." }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(KotlinDocumentationProvider::class.java)

        private fun renderEnumSpecialFunction(element: KtClass, descriptor: DeclarationDescriptor, quickNavigation: Boolean): String {
            val kdoc = run {
                val declarationDescriptor = element.resolveToDescriptorIfAny()
                val enumDescriptor = declarationDescriptor?.getSuperClassNotAny() ?: return@run null

                val enumDeclaration =
                    DescriptorToSourceUtilsIde.getAnyDeclaration(element.project, enumDescriptor) as? KtDeclaration ?: return@run null

                val enumSource = SourceNavigationHelper.getNavigationElement(enumDeclaration)
                val functionName = descriptor.fqNameSafe.shortName().asString()
                return@run enumSource.findDescendantOfType<KDoc> { doc ->
                    doc.getChildrenOfType<KDocSection>().any { it.findTagByName(functionName) != null }
                }
            }

            return buildString {
                insert(KDocTemplate()) {
                    definition {
                        renderDefinition(descriptor, Lazy.DESCRIPTOR_RENDERER
                            .withIdeOptions { highlightingManager = createHighlightingManager(element.project) }
                        )
                    }
                    if (!quickNavigation && kdoc != null) {
                        description {
                            renderKDoc(kdoc.getDefaultSection())
                        }
                    }
                }
            }
        }


        @NlsSafe
        private fun renderEnum(element: KtClass, originalElement: PsiElement?, quickNavigation: Boolean): String {
            val referenceExpression = originalElement?.getNonStrictParentOfType<KtReferenceExpression>()
            if (referenceExpression != null) {
                // When caret on special enum function (e.g. SomeEnum.values<caret>())
                // element is not an KtReferenceExpression, but KtClass of enum
                // so reference extracted from originalElement
                val context = referenceExpression.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)
                (context[BindingContext.REFERENCE_TARGET, referenceExpression]
                    ?: context[BindingContext.REFERENCE_TARGET, referenceExpression.getChildOfType<KtReferenceExpression>()])?.let {
                    if (it is FunctionDescriptor || it is PropertyDescriptor) // To protect from Some<caret>Enum.values()
                        return renderEnumSpecialFunction(element, it, quickNavigation)
                }
            }
            return renderKotlinDeclaration(element, quickNavigation)
        }

        @Nls
        private fun getText(element: PsiElement, originalElement: PsiElement?, quickNavigation: Boolean) =
            getTextImpl(element, originalElement, quickNavigation)

        @Nls
        private fun getTextImpl(element: PsiElement, originalElement: PsiElement?, quickNavigation: Boolean): String? {
            (element as? KtElement)?.navigationElement.takeIf { it != element }?.let {
                return getTextImpl(it, originalElement, quickNavigation)
            }

            if (element is PsiWhiteSpace) {
                val itElement = findElementWithText(originalElement, StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier)
                val itReference = itElement?.getParentOfType<KtNameReferenceExpression>(false)
                if (itReference != null) {
                    return getTextImpl(itReference, originalElement, quickNavigation)
                }
            }

            if (element is KtTypeReference) {
                val declaration = element.parent
                if (declaration is KtCallableDeclaration && declaration.receiverTypeReference == element) {
                    val thisElement = findElementWithText(originalElement, "this")
                    if (thisElement != null) {
                        return getTextImpl(declaration, originalElement, quickNavigation)
                    }
                }
            }

            if (element is KtClass && element.isEnum()) {
                // When caret on special enum function (e.g. SomeEnum.values<caret>())
                // element is not an KtReferenceExpression, but KtClass of enum
                return renderEnum(element, originalElement, quickNavigation)
            } else if (element is KtEnumEntry && !quickNavigation) {
                val ordinal = element.containingClassOrObject?.body?.run { getChildrenOfType<KtEnumEntry>().indexOf(element) }

                val project = element.project
                @Suppress("HardCodedStringLiteral")
                return buildString {
                    insert(buildKotlinDeclaration(element, quickNavigation = false)) {
                        definition {
                            it.inherit()
                            ordinal?.let {
                                append("<br>")
                                appendHighlighted("// ", project) { asInfo }
                                appendHighlighted(KotlinBundle.message("quick.doc.text.enum.ordinal", ordinal), project) { asInfo }
                            }
                        }
                    }
                }
            } else if (element is KtDeclaration) {
                return renderKotlinDeclaration(element, quickNavigation)
            } else if (element is KtNameReferenceExpression && element.getReferencedNameAsName() == StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME) {
                return renderKotlinImplicitLambdaParameter(element, quickNavigation)
            } else if (element is KtLightDeclaration<*, *>) {
                val origin = element.kotlinOrigin ?: return null
                return renderKotlinDeclaration(origin, quickNavigation)
            } else if (element is KtValueArgumentList) {
                val referenceExpression = element.prevSibling as? KtSimpleNameExpression ?: return null
                val calledElement = referenceExpression.mainReference.resolve()
                if (calledElement is KtNamedFunction || calledElement is KtConstructor<*>) { // In case of Kotlin function or constructor
                    return renderKotlinDeclaration(calledElement as KtExpression, quickNavigation)
                } else if (calledElement is ClsMethodImpl || calledElement is PsiMethod) { // In case of java function or constructor
                    return Lazy.javaDocumentProvider.generateDoc(calledElement, referenceExpression)
                }
            } else if (element is KtCallExpression) {
                val calledElement = element.referenceExpression()?.mainReference?.resolve()
                return calledElement?.let { getTextImpl(it, originalElement, quickNavigation) }
            } else if (element.isModifier()) {
                when (element.text) {
                    KtTokens.LATEINIT_KEYWORD.value -> return KotlinBundle.message("quick.doc.text.lateinit")
                    KtTokens.TAILREC_KEYWORD.value -> return KotlinBundle.message("quick.doc.text.tailrec")
                }
            }

            if (quickNavigation) {
                val referenceExpression = originalElement?.getNonStrictParentOfType<KtReferenceExpression>()
                if (referenceExpression != null) {
                    val context = referenceExpression.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)
                    val declarationDescriptor = context[BindingContext.REFERENCE_TARGET, referenceExpression]
                    if (declarationDescriptor != null) {
                        return mixKotlinToJava(declarationDescriptor, element, originalElement)
                    }
                }
            }

            // This element was resolved to non-kotlin element, it will be rendered with own provider
            return null
        }

        @NlsSafe
        private fun renderKotlinDeclaration(declaration: KtExpression, quickNavigation: Boolean) = buildString {
            insert(buildKotlinDeclaration(declaration, quickNavigation)) {}
        }

        private fun buildKotlinDeclaration(declaration: KtExpression, quickNavigation: Boolean): KDocTemplate {
            val resolutionFacade = declaration.getResolutionFacade()
            val context = declaration.safeAnalyzeNonSourceRootCode(resolutionFacade, BodyResolveMode.PARTIAL)
            val declarationDescriptor = context[BindingContext.DECLARATION_TO_DESCRIPTOR, declaration]

            if (declarationDescriptor == null) {
                LOG.info("Failed to find descriptor for declaration " + declaration.getElementTextWithContext())
                return KDocTemplate.NoDocTemplate().apply {
                    error {
                        append(KotlinBundle.message("quick.doc.no.documentation"))
                    }
                }
            }

            return buildKotlin(context, declarationDescriptor, quickNavigation, declaration, resolutionFacade)
        }

        @NlsSafe
        private fun renderKotlinImplicitLambdaParameter(element: KtReferenceExpression, quickNavigation: Boolean): String? {
            val resolutionFacade = element.getResolutionFacade()
            val context = element.safeAnalyzeNonSourceRootCode(resolutionFacade, BodyResolveMode.PARTIAL)
            val target = element.mainReference.resolveToDescriptors(context).singleOrNull() as? ValueParameterDescriptor? ?: return null
            return renderKotlin(context, target, quickNavigation, element, resolutionFacade)
        }

        private fun renderKotlin(
            context: BindingContext,
            declarationDescriptor: DeclarationDescriptor,
            quickNavigation: Boolean,
            ktElement: KtElement,
            resolutionFacade: ResolutionFacade,
        ) = buildString {
            insert(buildKotlin(context, declarationDescriptor, quickNavigation, ktElement, resolutionFacade)) {}
        }

        private fun buildKotlin(
            context: BindingContext,
            declarationDescriptor: DeclarationDescriptor,
            quickNavigation: Boolean,
            ktElement: KtElement,
            resolutionFacade: ResolutionFacade,
        ): KDocTemplate {
            @Suppress("NAME_SHADOWING")
            var declarationDescriptor = declarationDescriptor
            if (declarationDescriptor is ValueParameterDescriptor) {
                val property = context[BindingContext.VALUE_PARAMETER_AS_PROPERTY, declarationDescriptor]
                if (property != null) {
                    declarationDescriptor = property
                }
            }

            @OptIn(FrontendInternals::class)
            val deprecationProvider = resolutionFacade.frontendService<DeprecationResolver>()

            return KDocTemplate().apply {
                definition {
                    renderDefinition(declarationDescriptor, Lazy.DESCRIPTOR_RENDERER
                        .withIdeOptions { highlightingManager = createHighlightingManager(ktElement.project) }
                    )
                }

                insertDeprecationInfo(declarationDescriptor, deprecationProvider, ktElement.project)

                if (!quickNavigation) {
                    description {
                        declarationDescriptor.findKDoc { DescriptorToSourceUtilsIde.getAnyDeclaration(ktElement.project, it) }?.let {
                            renderKDoc(it.contentTag, it.sections)
                            return@description
                        }
                        if (declarationDescriptor is ClassConstructorDescriptor && !declarationDescriptor.isPrimary) {
                            declarationDescriptor.constructedClass.findKDoc {
                                DescriptorToSourceUtilsIde.getAnyDeclaration(
                                    ktElement.project,
                                    it
                                )
                            }?.let {
                                renderKDoc(it.contentTag, it.sections)
                                return@description
                            }
                        }
                        if (declarationDescriptor is CallableDescriptor) { // If we couldn't find KDoc, try to find javadoc in one of super's
                            insert(DescriptionBodyTemplate.FromJava()) {
                                body = extractJavaDescription(declarationDescriptor)
                            }
                        }
                    }
                }

                getContainerInfo(ktElement)?.toString()?.takeIf { it.isNotBlank() }?.let { info ->
                    containerInfo {
                        append(info)
                    }
                }
            }
        }

        private fun StringBuilder.renderDefinition(descriptor: DeclarationDescriptor, renderer: DescriptorRenderer) {
            append(renderer.render(descriptor))
        }

        private fun extractJavaDescription(declarationDescriptor: DeclarationDescriptor): String {
            val psi = declarationDescriptor.findPsi() as? KtFunction ?: return ""
            // Light method for super's scan in javadoc info gen
            val lightElement = LightClassUtil.getLightClassMethod(psi) ?: return "" 
            return generateJavadoc(lightElement)
        }

        private fun KDocTemplate.insertDeprecationInfo(
            declarationDescriptor: DeclarationDescriptor,
            deprecationResolver: DeprecationResolver,
            project: Project
        ) {
            val deprecationInfo = deprecationResolver.getDeprecations(declarationDescriptor).firstOrNull() ?: return

            deprecation {
                deprecationInfo.message?.let { message ->
                    append(SECTION_HEADER_START)
                    append(KotlinBundle.message("quick.doc.section.deprecated"))
                    append(SECTION_SEPARATOR)
                    append(message.htmlEscape())
                    append(SECTION_END)
                }
                deprecationInfo.deprecatedByAnnotationReplaceWithExpression()?.let { replaceWith ->
                    append(SECTION_HEADER_START)
                    append(KotlinBundle.message("quick.doc.section.replace.with"))
                    append(SECTION_SEPARATOR)
                    wrapTag("code") {
                        appendCodeSnippetHighlightedByLexer(project, replaceWith.htmlEscape())
                    }
                    append(SECTION_END)
                }
            }
        }

        private fun getContainerInfo(element: PsiElement?): HtmlChunk? {
            val ktExpression = element as? KtExpression ?: return null

            val resolutionFacade = element.getResolutionFacade()
            val context = element.safeAnalyzeNonSourceRootCode(resolutionFacade, BodyResolveMode.PARTIAL)
            val descriptor = context[BindingContext.DECLARATION_TO_DESCRIPTOR, element] ?: return null
            if (DescriptorUtils.isLocal(descriptor)) return null

            val containingDeclaration = descriptor.containingDeclaration ?: return null

            val fqNameSection = containingDeclaration.fqNameSafe
                .takeUnless { it.isRoot }
                ?.let {
                    @Nls val link = StringBuilder().apply {
                        val highlighted =
                            if (DocumentationSettings.isSemanticHighlightingOfLinksEnabled()) highlight(it.asString(), element.project) { asClassName }
                            else it.asString()
                        DocumentationManagerUtil.createHyperlink(this, it.asString(), highlighted, false)
                    }
                    HtmlChunk.fragment(
                        HtmlChunk.tag("icon").attr(
                            "src",
                            if (ktExpression.isTopLevelKtOrJavaMember()) {
                                "AllIcons.Nodes.Package"
                            } else {
                                "KotlinBaseResourcesIcons.ClassKotlin"
                            }
                        ),
                        HtmlChunk.nbsp(),
                        HtmlChunk.raw(link.toString()),
                        HtmlChunk.br()
                    )
                }
                ?: HtmlChunk.empty()

            val fileNameSection = descriptor
                .safeAs<DeclarationDescriptorWithSource>()
                ?.source
                ?.containingFile
                ?.name
                ?.takeIf { containingDeclaration is PackageFragmentDescriptor }
                ?.let {  fileName: @NlsSafe String ->
                    HtmlChunk.fragment(
                        HtmlChunk.tag("icon").attr("src", "KotlinBaseResourcesIcons.Kotlin_file"),
                        HtmlChunk.nbsp(),
                        HtmlChunk.text(fileName),
                        HtmlChunk.br()
                    )
                }
                ?: HtmlChunk.empty()

            return HtmlChunk.fragment(fqNameSection, fileNameSection)
        }

        private fun String.htmlEscape(): String = HtmlEscapers.htmlEscaper().escape(this)

        private inline fun StringBuilder.wrap(prefix: String, postfix: String, crossinline body: () -> Unit) {
            this.append(prefix)
            body()
            this.append(postfix)
        }

        private inline fun StringBuilder.wrapTag(tag: String, crossinline body: () -> Unit) {
            wrap("<$tag>", "</$tag>", body)
        }

        @NlsSafe
        private fun mixKotlinToJava(
            declarationDescriptor: DeclarationDescriptor,
            element: PsiElement,
            originalElement: PsiElement?
        ): String? {
            if (KotlinPlatformUtils.isCidr) {
                // No Java support in CIDR
                return null
            }

            val originalInfo = JavaDocumentationProvider().getQuickNavigateInfo(element, originalElement)
            if (originalInfo != null) {
                val renderedDecl = constant { Lazy.DESCRIPTOR_RENDERER.withIdeOptions { withDefinedIn = false } }.render(declarationDescriptor)
                return "$renderedDecl<br/>" + KotlinBundle.message("quick.doc.section.java.declaration") + "<br/>$originalInfo"
            }

            return null
        }

        private fun findElementWithText(element: PsiElement?, text: String): PsiElement? {
            return when {
                element == null -> null
                element.text == text -> element
                element.prevLeaf()?.text == text -> element.prevLeaf()
                else -> null
            }
        }

        private fun PsiElement?.isModifier() =
            this != null && parent is KtModifierList && KtTokens.MODIFIER_KEYWORDS_ARRAY.firstOrNull { it.value == text } != null
    }
}
