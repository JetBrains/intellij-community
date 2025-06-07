// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.ide.util.EditorHelper
import com.intellij.ide.util.MemberChooser
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.showOkNoDialog
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaNonPublicApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinPsiElementMemberChooserObject
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.psi.isAlwaysActual
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.codeinsight.utils.getExpressionShortText
import org.jetbrains.kotlin.idea.core.createFileForDeclaration
import org.jetbrains.kotlin.idea.core.overrideImplement.MemberGenerateMode
import org.jetbrains.kotlin.idea.core.overrideImplement.generateClassWithMembers
import org.jetbrains.kotlin.idea.core.overrideImplement.generateMember
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.getUnResolvableInScope
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.CreateClassUtil.getTypeDescription
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHint
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.idea.search.ExpectActualUtils
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.lexer.KtTokens.ACTUAL_KEYWORD
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal object ActualWithoutExpectFactory {

    val fixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ActualWithoutExpect ->
        val declaration = diagnostic.declaration.psi as? KtNamedDeclaration ?: return@IntentionBased emptyList()
        val compatibility = diagnostic.compatibility
        val hasVisibilityProblem = compatibility.isNotEmpty()
        if (hasVisibilityProblem && declaration !is KtFunction) return@IntentionBased emptyList()
        val (actualDeclaration, expectedContainingClass) = findFirstActualWithExpectedClass(declaration)
        if (hasVisibilityProblem && actualDeclaration !is KtFunction) return@IntentionBased emptyList()
        // If there is already an expected class, we suggest only for its module,
        // otherwise we suggest for all relevant expected modules
        val expectedModules = expectedContainingClass?.module?.let { listOf(it) } ?: actualDeclaration.module?.implementedModules ?: return@IntentionBased emptyList()
        expectedModules.mapNotNull { expectedModule ->
            val fileToCreateDeclaration = findExistingFileToCreateDeclaration(actualDeclaration.containingKtFile, declaration, expectedModule)
            when (actualDeclaration) {
                is KtProperty, is KtParameter, is KtFunction -> {
                    if (hasVisibilityProblem && findExpectWithConflictingVisibility(
                            actualDeclaration,
                            fileToCreateDeclaration,
                            expectedContainingClass
                        ) != null) {
                        return@mapNotNull null
                    }
                    CreateExpectedCallableMemberFix(actualDeclaration, expectedContainingClass, fileToCreateDeclaration, expectedModule)
                }
                is KtClassOrObject -> CreateExpectedClassFix(actualDeclaration, expectedContainingClass, fileToCreateDeclaration, expectedModule)
                else -> null
            }
        }
    }

    private fun KaSession.findExpectWithConflictingVisibility(
        actualDeclaration: KtCallableDeclaration,
        fileToCreateDeclaration: KtFile?,
        expectedContainingClass: KtClassOrObject?
    ): KtFunction? {
        if (fileToCreateDeclaration != null && actualDeclaration is KtFunction) {
            fun KaSession.types(decl: KtFunction): List<KaType?> =
                (decl.valueParameters.map { it.typeReference } + listOf(decl.receiverTypeReference) + decl.contextReceivers.map { it.typeReference() }).map { it?.type }

            val types = types(actualDeclaration)
            val sameSignatureFunction = (expectedContainingClass ?: fileToCreateDeclaration).findDescendantOfType<KtFunction> { decl ->
                //check only signatures without visibility, naming, ect
                decl.name == actualDeclaration.name &&
                        decl.valueParameters.size == actualDeclaration.valueParameters.size &&
                        (decl.receiverTypeReference != null) == (actualDeclaration.receiverTypeReference != null) &&
                        decl.contextReceivers.size == actualDeclaration.contextReceivers.size &&
                        types(decl).zip(types).all { (t1, t2) -> if (t2 == null) t1 == null else t1?.semanticallyEquals(t2) == true }
            }
            return sameSignatureFunction
        }
        return null
    }

    private fun findExistingFileToCreateDeclaration(
        originalFile: KtFile,
        originalDeclaration: KtNamedDeclaration,
        module: Module
    ): KtFile? {
        for (otherDeclaration in originalFile.declarations) {
            if (otherDeclaration === originalDeclaration) continue
            if (!otherDeclaration.hasActualModifier()) continue
            val expectDeclaration = ExpectActualUtils.liftToExpect(otherDeclaration) ?: continue
            if (expectDeclaration.module != module) continue
            return expectDeclaration.containingKtFile
        }
        return null
    }

    /**
     * For an [actualDeclaration] returns expectClass where corresponding expect declaration should be placed
     */
    private tailrec fun findFirstActualWithExpectedClass(actualDeclaration: KtNamedDeclaration): Pair<KtNamedDeclaration, KtClassOrObject?> {
        val containingClass = actualDeclaration.containingClassOrObject ?: return actualDeclaration to null
        val expectedContainingClass = ExpectActualUtils.liftToExpect(containingClass) as? KtClassOrObject
        return if (expectedContainingClass == null) findFirstActualWithExpectedClass(containingClass)
        else actualDeclaration to expectedContainingClass
    }
}


private val INACCESSIBLE_MODIFIERS = listOf(KtTokens.PRIVATE_KEYWORD, KtTokens.CONST_KEYWORD, KtTokens.LATEINIT_KEYWORD)

sealed class CreateExpectedFix<D : KtNamedDeclaration>(
    declaration: D,
    targetExpectedClass: KtClassOrObject?,
    val module: Module,
    val targetFile: KtFile?,
) : KotlinQuickFixAction<D>(declaration) {

    override fun getFamilyName(): String = KotlinBundle.message("fix.create.expect.actual")

    override fun startInWriteAction(): Boolean = false

    private val targetExpectedClassPointer = targetExpectedClass?.createSmartPointer()

    override fun getText(): String = KotlinBundle.message("create.expected.0.in.common.module.1", element.getTypeDescription(), module.name)

    final override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val targetExpectedClass = targetExpectedClassPointer?.element
        val expectedFile = targetExpectedClass?.containingKtFile ?: getOrCreateImplementationFile() ?: return
        val declaration = element ?: return
        val expectPrototype = generate(project, targetExpectedClass, expectedFile, declaration) ?: return
        val target = project.executeWriteCommand(familyName, null) {
            val initial = (when {
                targetExpectedClass != null && expectPrototype is KtPrimaryConstructor -> targetExpectedClass.add(expectPrototype)
                targetExpectedClass != null -> targetExpectedClass.addDeclaration<KtDeclaration>(expectPrototype)
                else -> expectedFile.add(expectPrototype)
            }) as KtElement
            val shortened = shortenReferences(initial)
            initial.takeIf { initial.isValid } ?: shortened ?: initial
        }

        EditorHelper.openInEditor(target)?.caretModel?.moveToOffset(
            (target as? KtNamedDeclaration)?.nameIdentifier?.startOffset ?: target.startOffset,
            true,
        )
    }

    abstract fun generate(project: Project, targetExpectedClass: KtClassOrObject?, expectedFile: KtFile, declaration: D): D?

    fun showInaccessibleDeclarationError(
        element: PsiElement,
        @NlsContexts.DialogMessage message: String,
        editor: Editor? = element.findExistingEditor()
    ) {
        if (editor != null) {
            showErrorHint(element.project, editor, StringUtil.escapeXmlEntities(message), KotlinBundle.message("inaccessible.declaration"))
        }
    }

    @OptIn(KaExperimentalApi::class, KaNonPublicApi::class)
    protected fun isCorrectAndHaveAccessibleModifiers(
        declaration: KtNamedDeclaration,
        expectedFile: KtFile,
        existingNames: Set<String>,
        showErrorHint: Boolean
    ): Boolean {
        val inaccessibleModifier = INACCESSIBLE_MODIFIERS.find { declaration.hasModifier(it) }
        if (inaccessibleModifier != null) {
            if (showErrorHint) {
                showInaccessibleDeclarationError(
                    declaration,
                    KotlinBundle.message("the.declaration.has.0.modifier", inaccessibleModifier)
                )
            }
            return false
        }
        if (declaration is KtFunction && declaration.hasBody() && declaration.containingClassOrObject?.isInterfaceClass() == true) {
            if (showErrorHint) {
                showInaccessibleDeclarationError(
                    declaration,
                    KotlinBundle.message("the.function.declaration.shouldn.t.have.a.default.implementation")
                )
            }
            return false
        }

        val unresolvedTypes = analyzeInModalWindow(declaration, KotlinBundle.message("fix.change.signature.prepare")) {
            fun MutableList<KaType>.processTypeParametersOwner(owner: KtTypeParameterListOwner) {
                owner.typeParameters.forEach { addIfNotNull(it.extendsBound?.type) }
                owner.typeConstraints.forEach { addIfNotNull(it.boundTypeReference?.type) }
                owner.annotationEntries.forEach { addIfNotNull(it.typeReference?.type) }
            }

            val usedTypes = when (declaration) {
                is KtConstructor<*> -> declaration.valueParameters.mapNotNull { it.typeReference?.type }
                is KtFunction -> buildList {
                    addIfNotNull(declaration.receiverTypeReference?.type)
                    addIfNotNull(declaration.returnType)
                    for (parameter in declaration.valueParameters) {
                        addIfNotNull(parameter.returnType)
                    }
                    processTypeParametersOwner(declaration)
                }

                is KtParameter -> listOfNotNull(declaration.typeReference?.type)

                is KtProperty -> buildList {
                    addIfNotNull(declaration.receiverTypeReference?.type)
                    addIfNotNull(declaration.returnType)
                    processTypeParametersOwner(declaration)

                }
                is KtClass -> buildList {
                    if (declaration.isInline()) {
                        declaration.primaryConstructor?.valueParameters?.forEach { addIfNotNull(it.returnType) }
                    }
                    processTypeParametersOwner(declaration)
                }

                else -> emptyList()
            }.map { it.abbreviation ?: it }.distinct()

            val expectedVirtualFile = expectedFile.virtualFile
            usedTypes.mapNotNull {
                getUnResolvableInScope(it, expectedFile, mutableSetOf()) { classSymbol ->
                    val psi = (classSymbol.getExpectsForActual().firstOrNull() ?: classSymbol).psi
                    val useScope = psi?.useScope
                    classSymbol.classId?.asSingleFqName()?.asString() in existingNames || psi == declaration || useScope != null && expectedVirtualFile != null && useScope.contains(expectedVirtualFile)
                }
            }.mapNotNull { (it as? KaClassType)?.classId?.shortClassName?.asString() ?: (it as? KaErrorType)?.presentableText }
        }
        if (unresolvedTypes.isNotEmpty()) {
            if (showErrorHint) {
                showInaccessibleDeclarationError(
                    declaration,
                    KotlinBundle.message(
                        "some.types.are.not.accessible.from.0.1",
                        module.name,
                        unresolvedTypes.joinToString()
                    )
                )
            }
            return false
        }

        return true
    }

    private fun getOrCreateImplementationFile(): KtFile? {
        val declaration = element as? KtNamedDeclaration ?: return null
        return targetFile ?: createFileForDeclaration(module, declaration)
    }
}

internal class CreateExpectedClassFix(
    declaration: KtClassOrObject,
    targetExpectedClass: KtClassOrObject?,
    targetFile: KtFile?,
    commonModule: Module,
) : CreateExpectedFix<KtClassOrObject>(declaration, targetExpectedClass, commonModule, targetFile) {
    @OptIn(KaExperimentalApi::class)
    override fun generate(
        project: Project,
        targetExpectedClass: KtClassOrObject?,
        expectedFile: KtFile,
        declaration: KtClassOrObject,
    ): KtClassOrObject? {

        val klass = declaration
        val declarationsWhichCanHaveActualModifiers = klass.collectDeclarationsForPossibleActualModifier(withSelf = false)
        val existingClasses = findAndApplyExistingClasses(declarationsWhichCanHaveActualModifiers + klass, expectedFile)
        if (!isCorrectAndHaveAccessibleModifiers(klass, expectedFile, existingClasses, showErrorHint = true)) return null

        val (members, declarationsWithNonExistentClasses) = declarationsWhichCanHaveActualModifiers.partition {
            isCorrectAndHaveAccessibleModifiers(it, expectedFile, existingClasses, showErrorHint = false)
        }

        if (!showUnknownTypeInDeclarationDialog(project, declarationsWithNonExistentClasses)) return null

        val membersForSelection = members.filter {
            !it.isAlwaysActual() && if (it is KtParameter) it.hasValOrVar() else true
        }

        val selectedElements = when {
            membersForSelection.all(KtDeclaration::hasActualModifier) -> membersForSelection
            isUnitTestMode() -> membersForSelection.filter(KtDeclaration::hasActualModifier)
            else -> {
                val prefix = klass.fqName?.asString()?.plus(".") ?: ""
                chooseMembers(project, membersForSelection, prefix) ?: return null
            }
        }.asSequence().plus(klass).plus(members.filter(KtNamedDeclaration::isAlwaysActual)).flatMap(KtNamedDeclaration::getContainingDeclarations).toSet()

        val selectedClasses = findAndApplyExistingClasses(selectedElements, expectedFile)
        val resultDeclarations = if (selectedClasses != existingClasses) {
            if (!isCorrectAndHaveAccessibleModifiers(klass, expectedFile, selectedClasses, showErrorHint = true)) return null

            val (resultDeclarations, withErrors) = selectedElements.partition {
                isCorrectAndHaveAccessibleModifiers(it, expectedFile, selectedClasses, showErrorHint = false)
            }
            if (!showUnknownTypeInDeclarationDialog(project, withErrors)) return null
            resultDeclarations
        } else
            selectedElements

        project.executeWriteCommand(KotlinBundle.message("repair.actual.members")) {
            repairActualModifiers(declarationsWhichCanHaveActualModifiers + klass, resultDeclarations)
        }

        return analyzeInModalWindow(klass, KotlinBundle.message("fix.change.signature.prepare")) {
            val classSymbol = klass.symbol as? KaClassSymbol ?: return@analyzeInModalWindow null

            generateClassWithMembers(
                project = klass.project,
                ktClassMember = null,
                symbol = classSymbol,
                targetClass = targetExpectedClass,
                mode = MemberGenerateMode.EXPECT,
            )
        }
    }

    /***
     * @return null if close without OK
     */
    private fun chooseMembers(project: Project, collection: Collection<KtNamedDeclaration>, prefixToRemove: String): List<KtNamedDeclaration>? {
        val classMembers = analyzeInModalWindow(collection.first(), KotlinBundle.message("fix.change.signature.prepare")) {
            collection.map {
                KotlinPsiElementMemberChooserObject.getKotlinMemberChooserObject(it)
            }
        }
        val filter = if (collection.any(KtDeclaration::hasActualModifier)) {
            { declaration: KtDeclaration -> declaration.hasActualModifier() }
        } else {
            { true }
        }
        return MemberChooser(
            classMembers.toTypedArray(),
            true,
            true,
            project
        ).run {
            title = KotlinBundle.message("choose.actual.members.title")
            setCopyJavadocVisible(false)
            selectElements(classMembers.filter { filter((it.element as KtNamedDeclaration)) }.toTypedArray())
            show()
            if (!isOK) null else selectedElements?.map { it.element as KtNamedDeclaration }.orEmpty()
        }
    }

    private fun repairActualModifiers(
        originalElements: Collection<KtNamedDeclaration>,
        selectedElements: Collection<KtNamedDeclaration>
    ) {
        if (originalElements.size == selectedElements.size) {
            for (original in originalElements) {
                original.makeActualWithParents()
            }
        } else {
            for (original in originalElements) {
                if (original in selectedElements)
                    original.makeActualWithParents()
                else {
                    original.removeModifier(ACTUAL_KEYWORD)
                }
            }
        }
    }

    private fun showUnknownTypeInDeclarationDialog(
        project: Project,
        declarationsWithNonExistentClasses: Collection<KtNamedDeclaration>
    ): Boolean {
        if (declarationsWithNonExistentClasses.isEmpty()) return true
        @NlsSafe
        val message = StringUtil.escapeXmlEntities(
            declarationsWithNonExistentClasses.joinToString(
                prefix = "${KotlinBundle.message("these.declarations.cannot.be.transformed")}\n",
                separator = "\n",
                transform = ::getExpressionShortText
            )
        )

        ExpectActualUtils.testLog?.append("$message\n")
        return isUnitTestMode() || showOkNoDialog(
            KotlinBundle.message("unknown.types.title"),
            message,
            project
        )
    }

    private fun KtClassOrObject.collectDeclarationsForPossibleActualModifier(withSelf: Boolean = true): List<KtNamedDeclaration> {
        val primaryConstructorSequence: List<KtNamedDeclaration> =  primaryConstructor.let {
            if (it != null) it.valueParameterList?.parameters.orEmpty() + listOf(it) else emptyList()
        }

        return (if (withSelf) listOf(this) else emptyList()) + primaryConstructorSequence + declarations.flatMap {
            if (it.canAddActualModifier())
                when (it) {
                    is KtClassOrObject -> it.collectDeclarationsForPossibleActualModifier()
                    is KtNamedDeclaration -> listOf(it)
                    else -> emptyList()
                }
            else {
                emptyList()
            }
        }
    }

    private fun KtDeclaration.canAddActualModifier() = when (this) {
        is KtEnumEntry, is KtClassInitializer -> false
        is KtParameter -> hasValOrVar()
        else -> true
    }


    private tailrec fun KtDeclaration.makeActualWithParents() {
        addModifier(ACTUAL_KEYWORD)
        containingClassOrObject?.takeUnless(KtDeclaration::hasActualModifier)?.makeActualWithParents()
    }

    fun findAndApplyExistingClasses(elements: Collection<KtNamedDeclaration>, expectedFile: KtFile): Set<String> {

        var existingTypeNames = mutableSetOf<String>()
        var classes = elements.filterIsInstance<KtClassOrObject>()
        while (classes.isNotEmpty()) {
            val existingNames = classes.mapNotNull { it.fqName?.asString() }.toHashSet()
            existingTypeNames = existingNames

            val newExistingClasses = classes.filter { isCorrectAndHaveAccessibleModifiers(it, expectedFile, existingNames, showErrorHint = false) }
            if (classes.size == newExistingClasses.size) return existingNames

            classes = newExistingClasses
        }

        return existingTypeNames
    }
}

private fun KtNamedDeclaration.getContainingDeclarations(): Set<KtNamedDeclaration> {
    val additionalElements = ((this as? KtParameter)?.parent?.parent as? KtPrimaryConstructor)?.let {
        setOf(it)
    } ?: emptySet()
    return setOf(this) + additionalElements + containingClassOrObject?.getContainingDeclarations().orEmpty()
}


internal class CreateExpectedCallableMemberFix(
    declaration: KtCallableDeclaration,
    targetExpectedClass: KtClassOrObject?,
    targetFile: KtFile?,
    commonModule: Module,
) : CreateExpectedFix<KtNamedDeclaration>(declaration, targetExpectedClass, commonModule, targetFile) {
    @OptIn(KaExperimentalApi::class)
    override fun generate(
        project: Project,
        targetExpectedClass: KtClassOrObject?,
        expectedFile: KtFile,
        declaration: KtNamedDeclaration,
    ): KtNamedDeclaration? {
        if (!isCorrectAndHaveAccessibleModifiers(declaration, expectedFile, emptySet(), showErrorHint = true)) return null

        return analyzeInModalWindow(declaration, KotlinBundle.message("fix.change.signature.prepare")) {
            val callableSymbol = declaration.symbol as? KaCallableSymbol ?: return@analyzeInModalWindow null

            generateMember(
                project = declaration.project,
                ktClassMember = null,
                symbol = (callableSymbol as? KaValueParameterSymbol)?.generatedPrimaryConstructorProperty ?: callableSymbol,
                targetClass = targetExpectedClass,
                copyDoc = false,
                mode = MemberGenerateMode.EXPECT,
            )
        }
    }
}