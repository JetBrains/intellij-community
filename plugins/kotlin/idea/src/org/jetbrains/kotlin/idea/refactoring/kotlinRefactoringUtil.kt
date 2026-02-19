// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring

import com.intellij.ide.IdeBundle
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.util.ConflictsUtil
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.impl.AnonymousFunctionDescriptor
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.CHECK_SUPER_METHODS_YES_NO_DIALOG
import org.jetbrains.kotlin.idea.base.util.showYesNoCancelDialog
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeAsReplacement
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.createKotlinFile
import org.jetbrains.kotlin.idea.core.getFqNameWithImplicitPrefix
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KtPsiClassWrapper
import org.jetbrains.kotlin.idea.refactoring.rename.canonicalRender
import org.jetbrains.kotlin.idea.roots.isOutsideKotlinAwareSourceRoot
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.liftToExpected
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.AnalyzingUtils
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.OperatorModifierChecker
import org.jetbrains.kotlin.resolve.calls.util.getCallWithAssert
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.typeUtil.unCapture
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory as newToPsiDirectory
import org.jetbrains.kotlin.idea.core.util.toPsiFile as newToPsiFile

@K1Deprecation
@ApiStatus.Internal
@Deprecated("Was moved to the common part", replaceWith = ReplaceWith("canRefactorElement()"))
fun PsiElement.canRefactor(): Boolean {
    return when {
        !isValid -> false
        this is PsiPackage ->
            directories.any { it.canRefactor() }
        this is KtElement || this is PsiMember && language == JavaLanguage.INSTANCE || this is PsiDirectory ->
            RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = true).matches(this)
        else -> false
    }
}

@K1Deprecation
@JvmOverloads
fun getOrCreateKotlinFile(
    fileName: String,
    targetDir: PsiDirectory,
    packageName: String? = targetDir.getFqNameWithImplicitPrefix()?.asString()
): KtFile =
    (targetDir.findFile(fileName) ?: createKotlinFile(fileName, targetDir, packageName)) as KtFile

@K1Deprecation
fun PsiElement.getUsageContext(): PsiElement {
    return when (this) {
        is KtElement -> PsiTreeUtil.getParentOfType(
            this,
            KtPropertyAccessor::class.java,
            KtProperty::class.java,
            KtNamedFunction::class.java,
            KtConstructor::class.java,
            KtClassOrObject::class.java
        ) ?: containingFile
        else -> ConflictsUtil.getContainer(this)
    }
}

@K1Deprecation
fun PsiElement.isInKotlinAwareSourceRoot(): Boolean =
    !isOutsideKotlinAwareSourceRoot(containingFile)


@K1Deprecation
fun reportDeclarationConflict(
    conflicts: MultiMap<PsiElement, String>,
    declaration: PsiElement,
    message: (renderedDeclaration: String) -> String
) {
    conflicts.putValue(declaration, message(RefactoringUIUtil.getDescription(declaration, true).capitalize()))
}

@K1Deprecation
@Deprecated(
    "Use org.jetbrains.kotlin.idea.base.psi.getLineStartOffset() instead",
    ReplaceWith("this.getLineStartOffset(line)", "org.jetbrains.kotlin.idea.base.psi.getLineStartOffset"),
    DeprecationLevel.ERROR
)
fun PsiFile.getLineStartOffset(line: Int): Int? {
    val doc = viewProvider.document ?: PsiDocumentManager.getInstance(project).getDocument(this)
    if (doc != null && line >= 0 && line < doc.lineCount) {
        val startOffset = doc.getLineStartOffset(line)
        val element = findElementAt(startOffset) ?: return startOffset

        if (element is PsiWhiteSpace || element is PsiComment) {
            return PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace::class.java, PsiComment::class.java)?.startOffset ?: startOffset
        }
        return startOffset
    }

    return null
}

internal fun broadcastRefactoringExit(project: Project, refactoringId: String) {
    // We send events both to the old K1 specific and new frontend-agnostic topic from here
    project.messageBus.syncPublisher(KotlinRefactoringEventListener.EVENT_TOPIC).onRefactoringExit(refactoringId)
    project.messageBus.syncPublisher(KotlinRefactoringListener.EVENT_TOPIC).onRefactoringExit(refactoringId)
}

// IMPORTANT: Target refactoring must support KotlinRefactoringEventListener
internal abstract class CompositeRefactoringRunner(
    val project: Project,
    val refactoringId: String
) {
    protected abstract fun runRefactoring()

    protected open fun onRefactoringDone() {}
    protected open fun onExit() {}

    fun run() {
        val connection = project.messageBus.connect()
        connection.subscribe(
            RefactoringEventListener.REFACTORING_EVENT_TOPIC,
            object : RefactoringEventListener {
                override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
                    if (refactoringId == this@CompositeRefactoringRunner.refactoringId) {
                        WriteAction.run<Throwable> { onRefactoringDone() }
                    }
                }
            }
        )
        connection.subscribe(
            KotlinRefactoringEventListener.EVENT_TOPIC,
            object : KotlinRefactoringEventListener {
                override fun onRefactoringExit(refactoringId: String) {
                    if (refactoringId == this@CompositeRefactoringRunner.refactoringId) {
                        try {
                            onExit()
                        } finally {
                            connection.disconnect()
                        }
                    }
                }
            }
        )
        runRefactoring()
    }
}

@K1Deprecation
@Throws(ConfigurationException::class)
fun KtElement?.validateElement(@NlsContexts.DialogMessage errorMessage: String) {
    if (this == null) throw ConfigurationException(errorMessage)

    try {
        AnalyzingUtils.checkForSyntacticErrors(this)
    } catch (e: Exception) {
        throw ConfigurationException(errorMessage)
    }
}

@K1Deprecation
fun invokeOnceOnCommandFinish(action: () -> Unit) {
    val simpleConnect = ApplicationManager.getApplication().messageBus.simpleConnect()
    simpleConnect.subscribe(CommandListener.TOPIC, object : CommandListener {
        override fun beforeCommandFinished(event: CommandEvent) {
            action()
            simpleConnect.disconnect()
        }
    })
}

@K1Deprecation
fun PsiNamedElement.isInterfaceClass(): Boolean = when (this) {
    is KtClass -> isInterface()
    is PsiClass -> isInterface
    is KtPsiClassWrapper -> psiClass.isInterface
    else -> false
}

@K1Deprecation
fun KtNamedDeclaration.isAbstract(): Boolean = when {
    hasModifier(KtTokens.ABSTRACT_KEYWORD) -> true
    containingClassOrObject?.isInterfaceClass() != true -> false
    this is KtProperty -> initializer == null && delegate == null && accessors.isEmpty()
    this is KtNamedFunction -> !hasBody()
    else -> false
}

@K1Deprecation
fun dropOverrideKeywordIfNecessary(element: KtNamedDeclaration) {
    val callableDescriptor = element.resolveToDescriptorIfAny() as? CallableDescriptor ?: return
    if (callableDescriptor.overriddenDescriptors.isEmpty()) {
        element.removeModifier(KtTokens.OVERRIDE_KEYWORD)
    }
}

@K1Deprecation
fun dropOperatorKeywordIfNecessary(element: KtNamedDeclaration) {
    val callableDescriptor = element.resolveToDescriptorIfAny() as? CallableDescriptor ?: return
    val diagnosticHolder = BindingTraceContext(element.project)
    OperatorModifierChecker.check(element, callableDescriptor, diagnosticHolder, element.languageVersionSettings)
    if (diagnosticHolder.bindingContext.diagnostics.any { it.factory == Errors.INAPPLICABLE_OPERATOR_MODIFIER }) {
        element.removeModifier(KtTokens.OPERATOR_KEYWORD)
    }
}

@K1Deprecation
fun getQualifiedTypeArgumentList(initializer: KtExpression): KtTypeArgumentList? {
    val call = initializer.resolveToCall() ?: return null
    val typeArgumentMap = call.typeArguments
    val typeArguments = call.candidateDescriptor.typeParameters.mapNotNull { typeArgumentMap[it] }
    val renderedList = typeArguments.joinToString(prefix = "<", postfix = ">") {
        IdeDescriptorRenderers.SOURCE_CODE_NOT_NULL_TYPE_APPROXIMATION.renderType(it.unCapture())
    }

    return KtPsiFactory(initializer.project).createTypeArguments(renderedList)
}

@K1Deprecation
fun addTypeArgumentsIfNeeded(expression: KtExpression, typeArgumentList: KtTypeArgumentList) {
    val context = expression.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
    val call = expression.getCallWithAssert(context)
    val callElement = call.callElement as? KtCallExpression ?: return
    if (call.typeArgumentList != null) return
    val callee = call.calleeExpression ?: return
    if (context.diagnostics.forElement(callee).all {
            it.factory != Errors.TYPE_INFERENCE_NO_INFORMATION_FOR_PARAMETER &&
                    it.factory != Errors.NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER
        }
    ) {
        return
    }

    callElement.addAfter(typeArgumentList, callElement.calleeExpression)
    ShortenReferences.DEFAULT.process(callElement.typeArgumentList!!)
}

internal fun DeclarationDescriptor.getThisLabelName(): String {
    if (!name.isSpecial) return name.asString()
    if (this is AnonymousFunctionDescriptor) {
        val function = source.getPsi() as? KtFunction
        val argument = function?.parent as? KtValueArgument
            ?: (function?.parent as? KtLambdaExpression)?.parent as? KtValueArgument
        val callElement = argument?.getStrictParentOfType<KtCallElement>()
        val callee = callElement?.calleeExpression as? KtSimpleNameExpression
        if (callee != null) return callee.text
    }
    return ""
}

internal fun DeclarationDescriptor.explicateAsTextForReceiver(): String {
    val labelName = getThisLabelName()
    return if (labelName.isEmpty()) "this" else "this@$labelName"
}

internal fun ImplicitReceiver.explicateAsText(): String {
    return declarationDescriptor.explicateAsTextForReceiver()
}

@K1Deprecation
val PsiFile.isInjectedFragment: Boolean
    get() = InjectedLanguageManager.getInstance(project).isInjectedFragment(this)

@K1Deprecation
fun checkSuperMethods(
    declaration: KtDeclaration,
    ignore: Collection<PsiElement>?,
    @Nls actionString: String
): List<PsiElement> {
    if (!declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return listOf(declaration)

    val (declarationDescriptor, overriddenElementsToDescriptor) = getSuperDescriptors(declaration, ignore)

    if (overriddenElementsToDescriptor.isEmpty()) return listOf(declaration)

    fun getClassDescriptions(overriddenElementsToDescriptor: Map<PsiElement, CallableDescriptor>): List<String> {
        return overriddenElementsToDescriptor.entries.map { entry ->
            val (element, descriptor) = entry
            val description = when (element) {
                is KtNamedFunction, is KtProperty, is KtParameter -> formatClassDescriptor(descriptor.containingDeclaration)
                is PsiMethod -> {
                    val psiClass = element.containingClass ?: error("Invalid element: ${element.getText()}")
                    formatPsiClass(psiClass, markAsJava = true, inCode = false)
                }
                else -> error("Unexpected element: ${element.getElementTextWithContext()}")
            }
            "    $description\n"
        }
    }

    fun askUserForMethodsToSearch(
        declarationDescriptor: CallableDescriptor,
        overriddenElementsToDescriptor: Map<PsiElement, CallableDescriptor>
    ): List<PsiElement> {
        val superClassDescriptions = getClassDescriptions(overriddenElementsToDescriptor)

        val message = KotlinBundle.message(
            "override.declaration.x.overrides.y.in.class.list",
            DescriptorRenderer.COMPACT_WITH_SHORT_TYPES.render(declarationDescriptor),
            "\n${superClassDescriptions.joinToString(separator = "")}",
            actionString
        )

        val exitCode = showYesNoCancelDialog(
            CHECK_SUPER_METHODS_YES_NO_DIALOG,
            declaration.project, message, IdeBundle.message("title.warning"), Messages.getQuestionIcon(), Messages.YES
        )
        return when (exitCode) {
            Messages.YES -> overriddenElementsToDescriptor.keys.toList()
            Messages.NO -> listOf(declaration)
            else -> emptyList()
        }
    }

    return askUserForMethodsToSearch(declarationDescriptor, overriddenElementsToDescriptor)
}

private fun getSuperDescriptors(
    declaration: KtDeclaration,
    ignore: Collection<PsiElement>?
): Pair<CallableDescriptor, Map<PsiElement, CallableDescriptor>> {
    val progressTitle = KotlinBundle.message("find.usages.progress.text.declaration.superMethods")
    return ActionUtil.underModalProgress(declaration.project, progressTitle) {
        val declarationDescriptor = declaration.unsafeResolveToDescriptor() as CallableDescriptor

        if (declarationDescriptor is LocalVariableDescriptor) {
            return@underModalProgress (declarationDescriptor to emptyMap<PsiElement, CallableDescriptor>())
        }

        val overriddenElementsToDescriptor = HashMap<PsiElement, CallableDescriptor>()
        for (overriddenDescriptor in DescriptorUtils.getAllOverriddenDescriptors(declarationDescriptor)) {
            val overriddenDeclaration = DescriptorToSourceUtilsIde.getAnyDeclaration(
                declaration.project,
                overriddenDescriptor
            ) ?: continue
            if (overriddenDeclaration is KtNamedFunction
                || overriddenDeclaration is KtProperty
                || overriddenDeclaration is PsiMethod
                || overriddenDeclaration is KtParameter
            ) {
                overriddenElementsToDescriptor[overriddenDeclaration] = overriddenDescriptor
            }
        }

        if (ignore != null) {
            overriddenElementsToDescriptor.keys.removeAll(ignore)
        }

        return@underModalProgress (declarationDescriptor to overriddenElementsToDescriptor)
    }
}

@K1Deprecation
fun getSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?): List<PsiElement> {
    if (!declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return listOf(declaration)
    val (_, overriddenElementsToDescriptor) = getSuperDescriptors(declaration, ignore)
    return if (overriddenElementsToDescriptor.isEmpty()) listOf(declaration) else overriddenElementsToDescriptor.keys.toList()
}

internal fun KtDeclaration.resolveToExpectedDescriptorIfPossible(): DeclarationDescriptor {
    val descriptor = unsafeResolveToDescriptor()
    return descriptor.liftToExpected() ?: descriptor
}

@K1Deprecation
fun DialogWrapper.showWithTransaction() {
    TransactionGuard.submitTransaction(disposable, Runnable { show() })
}

@K1Deprecation
fun PsiMethod.checkDeclarationConflict(name: String, conflicts: MultiMap<PsiElement, String>, callables: Collection<PsiElement>) {
    containingClass
        ?.findMethodsByName(name, true)
        // as is necessary here: see KT-10386
        ?.firstOrNull { it.parameterList.parametersCount == 0 && !callables.contains(it.namedUnwrappedElement as PsiElement?) }
        ?.let { reportDeclarationConflict(conflicts, it) { s -> "$s already exists" } }
}

@K1Deprecation
fun <T : KtExpression> T.replaceWithCopyWithResolveCheck(
    resolveStrategy: (T, BindingContext) -> DeclarationDescriptor?,
    context: BindingContext = analyze(),
    preHook: T.() -> Unit = {},
    postHook: T.() -> T? = { this }
): T? {
    val originDescriptor = resolveStrategy(this, context) ?: return null
    @Suppress("UNCHECKED_CAST") val elementCopy = copy() as T
    elementCopy.preHook()
    val newContext = elementCopy.analyzeAsReplacement(this, context)
    val newDescriptor = resolveStrategy(elementCopy, newContext) ?: return null

    return if (originDescriptor.canonicalRender() == newDescriptor.canonicalRender()) elementCopy.postHook() else null
}

@K1Deprecation
@Deprecated(
    "Use org.jetbrains.kotlin.idea.core.util.toPsiDirectory() instead",
    ReplaceWith("this.toPsiDirectory(project)", "org.jetbrains.kotlin.idea.core.util.toPsiDirectory"),
    DeprecationLevel.ERROR
)
fun VirtualFile.toPsiDirectory(project: Project): PsiDirectory? {
    return newToPsiDirectory(project)
}

@K1Deprecation
@Deprecated(
    "Use org.jetbrains.kotlin.idea.core.util.toPsiFile() instead",
    ReplaceWith("this.toPsiFile(project)", "org.jetbrains.kotlin.idea.core.util.toPsiFile"),
    DeprecationLevel.ERROR
)
fun VirtualFile.toPsiFile(project: Project): PsiFile? {
    return newToPsiFile(project)
}

@K1Deprecation
fun KtTypeReference.classForRefactor(): KtClass? {
    val bindingContext = analyze(BodyResolveMode.PARTIAL)
    val type = bindingContext[BindingContext.TYPE, this] ?: return null
    val classDescriptor = type.constructor.declarationDescriptor as? ClassDescriptor ?: return null
    val declaration = DescriptorToSourceUtils.descriptorToDeclaration(classDescriptor) as? KtClass ?: return null
    return declaration.takeIf { declaration.canRefactorElement() }
}
