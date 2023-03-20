// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils
import com.intellij.codeInsight.unwrap.RangeSplitter
import com.intellij.codeInsight.unwrap.UnwrapHandler
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.file.PsiPackageBase
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.presentation.java.SymbolPresentationUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import com.intellij.refactoring.changeSignature.ChangeSignatureUtil
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.ui.ConflictsDialog
import com.intellij.refactoring.util.ConflictsUtil
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageViewTypeLocation
import com.intellij.util.VisibilityUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.asJava.*
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.AnonymousFunctionDescriptor
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.psi.dropCurlyBracketsIfPossible
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.CHECK_SUPER_METHODS_YES_NO_DIALOG
import org.jetbrains.kotlin.idea.base.util.collapseSpaces
import org.jetbrains.kotlin.idea.base.util.showYesNoCancelDialog
import org.jetbrains.kotlin.idea.caches.resolve.*
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaMemberDescriptor
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.*
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.refactoring.changeSignature.toValVar
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KtPsiClassWrapper
import org.jetbrains.kotlin.idea.refactoring.rename.canonicalRender
import org.jetbrains.kotlin.idea.roots.isOutsideKotlinAwareSourceRoot
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.liftToExpected
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.calls.util.getCallWithAssert
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.typeUtil.unCapture
import java.lang.annotation.Retention
import java.util.*
import javax.swing.Icon
import kotlin.math.min
import org.jetbrains.kotlin.idea.base.psi.getLineCount as newGetLineCount
import org.jetbrains.kotlin.idea.base.psi.getLineNumber as _getLineNumber
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory as newToPsiDirectory
import org.jetbrains.kotlin.idea.core.util.toPsiFile as newToPsiFile

@JvmOverloads
fun getOrCreateKotlinFile(
    fileName: String,
    targetDir: PsiDirectory,
    packageName: String? = targetDir.getFqNameWithImplicitPrefix()?.asString()
): KtFile =
    (targetDir.findFile(fileName) ?: createKotlinFile(fileName, targetDir, packageName)) as KtFile

fun createKotlinFile(
    fileName: String,
    targetDir: PsiDirectory,
    packageName: String? = targetDir.getFqNameWithImplicitPrefix()?.asString()
): KtFile {
    targetDir.checkCreateFile(fileName)
    val packageFqName = packageName?.let(::FqName) ?: FqName.ROOT
    val file = PsiFileFactory.getInstance(targetDir.project).createFileFromText(
        fileName, KotlinFileType.INSTANCE, if (!packageFqName.isRoot) "package ${packageFqName.quoteSegmentsIfNeeded()} \n\n" else ""
    )

    return targetDir.add(file) as KtFile
}

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

fun PsiElement.isInKotlinAwareSourceRoot(): Boolean =
    !isOutsideKotlinAwareSourceRoot(containingFile)

fun KtFile.createTempCopy(text: String? = null): KtFile {
    val tmpFile = KtPsiFactory.contextual(this).createFile(name, text ?: this.text ?: "")
    tmpFile.originalFile = this
    return tmpFile
}

fun PsiElement.getAllExtractionContainers(strict: Boolean = true): List<KtElement> {
    val containers = ArrayList<KtElement>()

    var objectOrNonInnerNestedClassFound = false
    val parents = if (strict) parents else parentsWithSelf
    for (element in parents) {
        val isValidContainer = when (element) {
            is KtFile -> true
            is KtClassBody -> !objectOrNonInnerNestedClassFound || element.parent is KtObjectDeclaration
            is KtBlockExpression -> !objectOrNonInnerNestedClassFound
            else -> false
        }
        if (!isValidContainer) continue

        containers.add(element as KtElement)

        if (!objectOrNonInnerNestedClassFound) {
            val bodyParent = (element as? KtClassBody)?.parent
            objectOrNonInnerNestedClassFound =
                (bodyParent is KtObjectDeclaration && !bodyParent.isObjectLiteral())
                        || (bodyParent is KtClass && !bodyParent.isInner())
        }
    }

    return containers
}

fun PsiElement.getExtractionContainers(strict: Boolean = true, includeAll: Boolean = false): List<KtElement> {
    fun getEnclosingDeclaration(element: PsiElement, strict: Boolean): PsiElement? {
        return (if (strict) element.parents else element.parentsWithSelf)
            .filter {
                (it is KtDeclarationWithBody && it !is KtFunctionLiteral && !(it is KtNamedFunction && it.name == null))
                        || it is KtAnonymousInitializer
                        || it is KtClassBody
                        || it is KtFile
            }
            .firstOrNull()
    }

    if (includeAll) return getAllExtractionContainers(strict)

    val enclosingDeclaration = getEnclosingDeclaration(this, strict)?.let {
        if (it is KtDeclarationWithBody || it is KtAnonymousInitializer) getEnclosingDeclaration(it, true) else it
    }

    return when (enclosingDeclaration) {
        is KtFile -> Collections.singletonList(enclosingDeclaration)
        is KtClassBody -> getAllExtractionContainers(strict).filterIsInstance<KtClassBody>()
        else -> {
            val targetContainer = when (enclosingDeclaration) {
                is KtDeclarationWithBody -> enclosingDeclaration.bodyExpression
                is KtAnonymousInitializer -> enclosingDeclaration.body
                else -> null
            }
            if (targetContainer is KtBlockExpression) Collections.singletonList(targetContainer) else Collections.emptyList()
        }
    }
}

fun Project.checkConflictsInteractively(
    conflicts: MultiMap<PsiElement, String>,
    onShowConflicts: () -> Unit = {},
    onAccept: () -> Unit
) {
    if (!conflicts.isEmpty) {
        if (isUnitTestMode()) throw ConflictsInTestsException(conflicts.values())

        val dialog = ConflictsDialog(this, conflicts) { onAccept() }
        dialog.show()
        if (!dialog.isOK) {
            if (dialog.isShowConflicts) {
                onShowConflicts()
            }
            return
        }
    }

    onAccept()
}

fun reportDeclarationConflict(
    conflicts: MultiMap<PsiElement, String>,
    declaration: PsiElement,
    message: (renderedDeclaration: String) -> String
) {
    conflicts.putValue(declaration, message(RefactoringUIUtil.getDescription(declaration, true).capitalize()))
}

fun <T : PsiElement> getPsiElementPopup(
    editor: Editor,
    elements: List<T>,
    renderer: PsiElementListCellRenderer<T>,
    @NlsContexts.PopupTitle title: String?,
    highlightSelection: Boolean,
    processor: (T) -> Boolean
): JBPopup = with(JBPopupFactory.getInstance().createPopupChooserBuilder(elements)) {
    val highlighter = if (highlightSelection) SelectionAwareScopeHighlighter(editor) else null
    setRenderer(renderer)
    setItemSelectedCallback { element: T? ->
        highlighter?.dropHighlight()
        element?.let {
            highlighter?.highlight(element)
        }
    }

    if (title != null) {
        setTitle(title)
    }
    renderer.installSpeedSearch(this, true)
    setItemChosenCallback { it?.let(processor) }

    addListener(object : JBPopupListener {
        override fun onClosed(event: LightweightWindowEvent) {
            highlighter?.dropHighlight()
        }
    })

    createPopup()
}

class SelectionAwareScopeHighlighter(val editor: Editor) {
    private val highlighters = ArrayList<RangeHighlighter>()

    private fun addHighlighter(r: TextRange, attr: TextAttributes) {
        highlighters.add(
            editor.markupModel.addRangeHighlighter(
                r.startOffset,
                r.endOffset,
                UnwrapHandler.HIGHLIGHTER_LEVEL,
                attr,
                HighlighterTargetArea.EXACT_RANGE
            )
        )
    }

    fun highlight(wholeAffected: PsiElement) {
        dropHighlight()

        val affectedRange = wholeAffected.textRange ?: return

        val attributes = EditorColorsManager.getInstance().globalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)!!
        val selectedRange = with(editor.selectionModel) { TextRange(selectionStart, selectionEnd) }
        val textLength = editor.document.textLength
        for (r in RangeSplitter.split(affectedRange, Collections.singletonList(selectedRange))) {
            if (r.endOffset <= textLength) addHighlighter(r, attributes)
        }
    }

    fun dropHighlight() {
        highlighters.forEach { it.dispose() }
        highlighters.clear()
    }
}

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

@Deprecated(
    "Use org.jetbrains.kotlin.idea.base.psi.getLineEndOffset() instead",
    ReplaceWith("this.getLineEndOffset(line)", "org.jetbrains.kotlin.idea.base.psi.getLineEndOffset"),
    DeprecationLevel.ERROR
)
fun PsiFile.getLineEndOffset(line: Int): Int? {
    val document = viewProvider.document ?: PsiDocumentManager.getInstance(project).getDocument(this)
    return document?.getLineEndOffset(line)
}

@Deprecated("Use org.jetbrains.kotlin.idea.base.psi.PsiLinesUtilsKt.getLineNumber instead")
fun PsiElement.getLineNumber(start: Boolean = true): Int {
   return _getLineNumber(start)
}

class SeparateFileWrapper(manager: PsiManager) : LightElement(manager, KotlinLanguage.INSTANCE) {
    override fun toString() = ""
}

fun <T> chooseContainerElement(
    containers: List<T>,
    editor: Editor,
    @NlsContexts.PopupTitle title: String,
    highlightSelection: Boolean,
    toPsi: (T) -> PsiElement,
    onSelect: (T) -> Unit
) {
    val psiElements = containers.map(toPsi)
    choosePsiContainerElement(
        elements = psiElements,
        editor = editor,
        title = title,
        highlightSelection = highlightSelection,
        psi2Container = { containers[psiElements.indexOf(it)] },
        onSelect = onSelect
    )
}

fun <T : PsiElement> chooseContainerElement(
    elements: List<T>,
    editor: Editor,
    @NlsContexts.PopupTitle title: String,
    highlightSelection: Boolean,
    onSelect: (T) -> Unit
): Unit = choosePsiContainerElement(
    elements = elements,
    editor = editor,
    title = title,
    highlightSelection = highlightSelection,
    psi2Container = { it },
    onSelect = onSelect,
)

private fun psiElementRenderer() = object : PsiElementListCellRenderer<PsiElement>() {
    private fun PsiElement.renderName(): String = when {
        this is KtPropertyAccessor -> property.renderName() + if (isGetter) ".get" else ".set"
        this is KtObjectDeclaration && isCompanion() -> {
            val name = getStrictParentOfType<KtClassOrObject>()?.renderName() ?: "<anonymous>"
            "Companion object of $name"
        }

        else -> (this as? PsiNamedElement)?.name ?: "<anonymous>"
    }

    private fun PsiElement.renderDeclaration(): String? {
        if (this is KtFunctionLiteral || isFunctionalExpression()) return renderText()
        val descriptor = when (this) {
            is KtFile -> name
            is KtElement -> analyze()[BindingContext.DECLARATION_TO_DESCRIPTOR, this]
            is PsiMember -> getJavaMemberDescriptor()
            else -> null
        } ?: return null

        val name = renderName()
        val params = (descriptor as? FunctionDescriptor)?.valueParameters?.joinToString(
            ", ",
            "(",
            ")"
        ) { DescriptorRenderer.SHORT_NAMES_IN_TYPES.renderType(it.type) } ?: ""

        return "$name$params"
    }

    private fun PsiElement.renderText(): String = when (this) {
        is SeparateFileWrapper -> KotlinBundle.message("refactoring.extract.to.separate.file.text")
        is PsiPackageBase -> qualifiedName
        else -> {
            val text = text ?: "<invalid text>"
            StringUtil.shortenTextWithEllipsis(text.collapseSpaces(), 53, 0)
        }
    }

    private fun PsiElement.getRepresentativeElement(): PsiElement = when (this) {
        is KtBlockExpression -> (parent as? KtDeclarationWithBody) ?: this
        is KtClassBody -> parent as KtClassOrObject
        else -> this
    }

    override fun getElementText(element: PsiElement): String {
        val representativeElement = element.getRepresentativeElement()
        return representativeElement.renderDeclaration() ?: representativeElement.renderText()
    }

    override fun getContainerText(element: PsiElement, name: String?): String? = null

    override fun getIcon(element: PsiElement): Icon? = super.getIcon(element.getRepresentativeElement())
}

private fun <T, E : PsiElement> choosePsiContainerElement(
    elements: List<E>,
    editor: Editor,
    @NlsContexts.PopupTitle title: String,
    highlightSelection: Boolean,
    psi2Container: (E) -> T,
    onSelect: (T) -> Unit,
) {
    val popup = getPsiElementPopup(
        editor,
        elements,
        psiElementRenderer(),
        title,
        highlightSelection,
    ) { psiElement ->
        @Suppress("UNCHECKED_CAST")
        onSelect(psi2Container(psiElement as E))
        true
    }

    invokeLater {
        popup.showInBestPositionFor(editor)
    }
}

fun <T> chooseContainerElementIfNecessary(
    containers: List<T>,
    editor: Editor,
    @NlsContexts.PopupTitle title: String,
    highlightSelection: Boolean,
    toPsi: (T) -> PsiElement,
    onSelect: (T) -> Unit
): Unit = chooseContainerElementIfNecessaryImpl(containers, editor, title, highlightSelection, toPsi, onSelect)

fun <T : PsiElement> chooseContainerElementIfNecessary(
    containers: List<T>,
    editor: Editor,
    @NlsContexts.PopupTitle title: String,
    highlightSelection: Boolean,
    onSelect: (T) -> Unit
): Unit = chooseContainerElementIfNecessaryImpl(containers, editor, title, highlightSelection, null, onSelect)

private fun <T> chooseContainerElementIfNecessaryImpl(
    containers: List<T>,
    editor: Editor,
    @NlsContexts.PopupTitle title: String,
    highlightSelection: Boolean,
    toPsi: ((T) -> PsiElement)?,
    onSelect: (T) -> Unit
) {
    when {
        containers.isEmpty() -> return
        containers.size == 1 || isUnitTestMode() -> onSelect(containers.first())
        toPsi != null -> chooseContainerElement(containers, editor, title, highlightSelection, toPsi, onSelect)
        else -> {
            @Suppress("UNCHECKED_CAST")
            chooseContainerElement(containers as List<PsiElement>, editor, title, highlightSelection, onSelect as (PsiElement) -> Unit)
        }
    }
}

fun PsiElement.isTrueJavaMethod(): Boolean = this is PsiMethod && this !is KtLightMethod

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

private fun copyModifierListItems(from: PsiModifierList, to: PsiModifierList, withPsiModifiers: Boolean = true) {
    if (withPsiModifiers) {
        for (modifier in PsiModifier.MODIFIERS) {
            if (from.hasExplicitModifier(modifier)) {
                to.setModifierProperty(modifier, true)
            }
        }
    }
    for (annotation in from.annotations) {
        val annotationName = annotation.qualifiedName ?: continue

        if (Retention::class.java.name != annotationName) {
            to.addAnnotation(annotationName)
        }
    }
}

private fun <T> copyTypeParameters(
    from: T,
    to: T,
    inserter: (T, PsiTypeParameterList) -> Unit
) where T : PsiTypeParameterListOwner, T : PsiNameIdentifierOwner {
    val factory = PsiElementFactory.getInstance((from as PsiElement).project)
    val templateTypeParams = from.typeParameterList?.typeParameters ?: PsiTypeParameter.EMPTY_ARRAY
    if (templateTypeParams.isNotEmpty()) {
        inserter(to, factory.createTypeParameterList())
        val targetTypeParamList = to.typeParameterList
        val newTypeParams = templateTypeParams.map {
            factory.createTypeParameter(it.name!!, it.extendsList.referencedTypes)
        }

        ChangeSignatureUtil.synchronizeList(
            targetTypeParamList,
            newTypeParams,
            { it!!.typeParameters.toList() },
            BooleanArray(newTypeParams.size)
        )
    }
}

fun createJavaMethod(function: KtFunction, targetClass: PsiClass): PsiMethod {
    val template = LightClassUtil.getLightClassMethod(function)
        ?: throw AssertionError("Can't generate light method: ${function.getElementTextWithContext()}")
    return createJavaMethod(template, targetClass)
}

fun createJavaMethod(template: PsiMethod, targetClass: PsiClass): PsiMethod {
    val factory = PsiElementFactory.getInstance(template.project)
    val methodToAdd = if (template.isConstructor) {
        factory.createConstructor(template.name)
    } else {
        factory.createMethod(template.name, template.returnType)
    }
    val method = targetClass.add(methodToAdd) as PsiMethod

    copyModifierListItems(template.modifierList, method.modifierList)
    if (targetClass.isInterface) {
        method.modifierList.setModifierProperty(PsiModifier.FINAL, false)
    }

    copyTypeParameters(template, method) { psiMethod, typeParameterList ->
        psiMethod.addAfter(typeParameterList, psiMethod.modifierList)
    }

    val targetParamList = method.parameterList
    val newParams = template.parameterList.parameters.map {
        val param = factory.createParameter(it.name, it.type)
        copyModifierListItems(it.modifierList!!, param.modifierList!!)
        param
    }

    ChangeSignatureUtil.synchronizeList(
        targetParamList,
        newParams,
        { it.parameters.toList() },
        BooleanArray(newParams.size)
    )

    if (template.modifierList.hasModifierProperty(PsiModifier.ABSTRACT) || targetClass.isInterface) {
        method.body!!.delete()
    } else if (!template.isConstructor) {
        CreateFromUsageUtils.setupMethodBody(method)
    }

    return method
}

fun createJavaField(property: KtNamedDeclaration, targetClass: PsiClass): PsiField {
    val accessorLightMethods = property.getAccessorLightMethods()
    val template = accessorLightMethods.getter
        ?: throw AssertionError("Can't generate light method: ${property.getElementTextWithContext()}")

    val factory = PsiElementFactory.getInstance(template.project)
    val field = targetClass.add(factory.createField(property.name!!, template.returnType!!)) as PsiField

    with(field.modifierList!!) {
        val templateModifiers = template.modifierList
        setModifierProperty(VisibilityUtil.getVisibilityModifier(templateModifiers), true)
        if ((property as KtValVarKeywordOwner).valOrVarKeyword.toValVar() != KotlinValVar.Var || targetClass.isInterface) {
            setModifierProperty(PsiModifier.FINAL, true)
        }

        copyModifierListItems(templateModifiers, this, false)
    }

    return field
}

fun createJavaClass(klass: KtClass, targetClass: PsiClass?, forcePlainClass: Boolean = false): PsiClass {
    val kind = if (forcePlainClass) ClassKind.CLASS else (klass.unsafeResolveToDescriptor() as ClassDescriptor).kind

    val factory = PsiElementFactory.getInstance(klass.project)
    val className = klass.name!!
    val javaClassToAdd = when (kind) {
        ClassKind.CLASS -> factory.createClass(className)
        ClassKind.INTERFACE -> factory.createInterface(className)
        ClassKind.ANNOTATION_CLASS -> factory.createAnnotationType(className)
        ClassKind.ENUM_CLASS -> factory.createEnum(className)
        else -> throw AssertionError("Unexpected class kind: ${klass.getElementTextWithContext()}")
    }
    val javaClass = (targetClass?.add(javaClassToAdd) ?: javaClassToAdd) as PsiClass

    val template = klass.toLightClass() ?: throw AssertionError("Can't generate light class: ${klass.getElementTextWithContext()}")

    copyModifierListItems(template.modifierList!!, javaClass.modifierList!!)
    if (template.isInterface) {
        javaClass.modifierList!!.setModifierProperty(PsiModifier.ABSTRACT, false)
    }

    copyTypeParameters(template, javaClass) { clazz, typeParameterList ->
        clazz.addAfter(typeParameterList, clazz.nameIdentifier)
    }

    // Turning interface to class
    if (!javaClass.isInterface && template.isInterface) {
        val implementsList = factory.createReferenceListWithRole(
            template.extendsList?.referenceElements ?: PsiJavaCodeReferenceElement.EMPTY_ARRAY,
            PsiReferenceList.Role.IMPLEMENTS_LIST
        )

        implementsList?.let { javaClass.implementsList?.replace(it) }
    } else {
        val extendsList = factory.createReferenceListWithRole(
            template.extendsList?.referenceElements ?: PsiJavaCodeReferenceElement.EMPTY_ARRAY,
            PsiReferenceList.Role.EXTENDS_LIST
        )

        extendsList?.let { javaClass.extendsList?.replace(it) }

        val implementsList = factory.createReferenceListWithRole(
            template.implementsList?.referenceElements ?: PsiJavaCodeReferenceElement.EMPTY_ARRAY,
            PsiReferenceList.Role.IMPLEMENTS_LIST
        )

        implementsList?.let { javaClass.implementsList?.replace(it) }
    }

    for (method in template.methods) {
        if (isSyntheticValuesOrValueOfMethod(method)) continue

        val hasParams = method.parameterList.parametersCount > 0
        val needSuperCall = !template.isEnum &&
                (template.superClass?.constructors ?: PsiMethod.EMPTY_ARRAY).all {
                    it.parameterList.parametersCount > 0
                }

        if (method.isConstructor && !(hasParams || needSuperCall)) continue
        with(createJavaMethod(method, javaClass)) {
            if (isConstructor && needSuperCall) {
                body!!.add(factory.createStatementFromText("super();", this))
            }
        }
    }

    return javaClass
}

internal fun broadcastRefactoringExit(project: Project, refactoringId: String) {
    project.messageBus.syncPublisher(KotlinRefactoringEventListener.EVENT_TOPIC).onRefactoringExit(refactoringId)
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
                override fun undoRefactoring(refactoringId: String) {

                }

                override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {

                }

                override fun conflictsDetected(refactoringId: String, conflictsData: RefactoringEventData) {

                }

                override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
                    if (refactoringId == this@CompositeRefactoringRunner.refactoringId) {
                        onRefactoringDone()
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

@Throws(ConfigurationException::class)
fun KtElement?.validateElement(@NlsContexts.DialogMessage errorMessage: String) {
    if (this == null) throw ConfigurationException(errorMessage)

    try {
        AnalyzingUtils.checkForSyntacticErrors(this)
    } catch (e: Exception) {
        throw ConfigurationException(errorMessage)
    }
}

fun invokeOnceOnCommandFinish(action: () -> Unit) {
    val simpleConnect = ApplicationManager.getApplication().messageBus.simpleConnect()
    simpleConnect.subscribe(CommandListener.TOPIC, object : CommandListener {
        override fun beforeCommandFinished(event: CommandEvent) {
            action()
            simpleConnect.disconnect()
        }
    })
}

fun FqNameUnsafe.hasIdentifiersOnly(): Boolean = pathSegments().all { it.asString().quoteIfNeeded().isIdentifier() }

fun FqName.hasIdentifiersOnly(): Boolean = pathSegments().all { it.asString().quoteIfNeeded().isIdentifier() }

fun PsiNamedElement.isInterfaceClass(): Boolean = when (this) {
    is KtClass -> isInterface()
    is PsiClass -> isInterface
    is KtPsiClassWrapper -> psiClass.isInterface
    else -> false
}

fun KtNamedDeclaration.isAbstract(): Boolean = when {
    hasModifier(KtTokens.ABSTRACT_KEYWORD) -> true
    containingClassOrObject?.isInterfaceClass() != true -> false
    this is KtProperty -> initializer == null && delegate == null && accessors.isEmpty()
    this is KtNamedFunction -> !hasBody()
    else -> false
}

fun <ListType : KtElement> replaceListPsiAndKeepDelimiters(
    changeInfo: KotlinChangeInfo,
    originalList: ListType,
    newList: ListType,
    @Suppress("UNCHECKED_CAST") listReplacer: ListType.(ListType) -> ListType = { replace(it) as ListType },
    itemsFun: ListType.() -> List<KtElement>
): ListType {
    originalList.children.takeWhile { it is PsiErrorElement }.forEach { it.delete() }

    val oldParameters = originalList.itemsFun().toMutableList()
    val newParameters = newList.itemsFun()
    val oldCount = oldParameters.size
    val newCount = newParameters.size

    val commonCount = min(oldCount, newCount)
    val originalIndexes = changeInfo.newParameters.map { it.originalIndex }
    val keepComments = originalList.allChildren.any { it is PsiComment } &&
            oldCount > commonCount && originalIndexes == originalIndexes.sorted()
    if (!keepComments) {
        for (i in 0 until commonCount) {
            oldParameters[i] = oldParameters[i].replace(newParameters[i]) as KtElement
        }
    }

    if (commonCount == 0 && !keepComments) return originalList.listReplacer(newList)

    if (oldCount > commonCount) {
        if (keepComments) {
            ((0 until oldParameters.size) - originalIndexes).forEach { index ->
                val oldParameter = oldParameters[index]
                val nextComma = oldParameter.getNextSiblingIgnoringWhitespaceAndComments()?.takeIf { it.node.elementType == KtTokens.COMMA }
                if (nextComma != null) {
                    nextComma.delete()
                } else {
                    oldParameter.getPrevSiblingIgnoringWhitespaceAndComments()?.takeIf { it.node.elementType == KtTokens.COMMA }?.delete()
                }
                oldParameter.delete()
            }
        } else {
            originalList.deleteChildRange(oldParameters[commonCount - 1].nextSibling, oldParameters.last())
        }
    } else if (newCount > commonCount) {
        val lastOriginalParameter = oldParameters.last()
        val psiBeforeLastParameter = lastOriginalParameter.prevSibling
        val withMultiline =
            (psiBeforeLastParameter is PsiWhiteSpace || psiBeforeLastParameter is PsiComment) && psiBeforeLastParameter.textContains('\n')
        val extraSpace = if (withMultiline) KtPsiFactory(originalList.project).createNewLine() else null
        originalList.addRangeAfter(newParameters[commonCount - 1].nextSibling, newParameters.last(), lastOriginalParameter)
        if (extraSpace != null) {
            val addedItems = originalList.itemsFun().subList(commonCount, newCount)
            for (addedItem in addedItems) {
                val elementBefore = addedItem.prevSibling
                if ((elementBefore !is PsiWhiteSpace && elementBefore !is PsiComment) || !elementBefore.textContains('\n')) {
                    addedItem.parent.addBefore(extraSpace, addedItem)
                }
            }
        }
    }

    return originalList
}

fun KtExpression.removeTemplateEntryBracesIfPossible(): KtExpression {
    val parent = parent as? KtBlockStringTemplateEntry ?: return this
    return parent.dropCurlyBracketsIfPossible().expression!!
}

fun dropOverrideKeywordIfNecessary(element: KtNamedDeclaration) {
    val callableDescriptor = element.resolveToDescriptorIfAny() as? CallableDescriptor ?: return
    if (callableDescriptor.overriddenDescriptors.isEmpty()) {
        element.removeModifier(KtTokens.OVERRIDE_KEYWORD)
    }
}

fun dropOperatorKeywordIfNecessary(element: KtNamedDeclaration) {
    val callableDescriptor = element.resolveToDescriptorIfAny() as? CallableDescriptor ?: return
    val diagnosticHolder = BindingTraceContext()
    OperatorModifierChecker.check(element, callableDescriptor, diagnosticHolder, element.languageVersionSettings)
    if (diagnosticHolder.bindingContext.diagnostics.any { it.factory == Errors.INAPPLICABLE_OPERATOR_MODIFIER }) {
        element.removeModifier(KtTokens.OPERATOR_KEYWORD)
    }
}

fun getQualifiedTypeArgumentList(initializer: KtExpression): KtTypeArgumentList? {
    val call = initializer.resolveToCall() ?: return null
    val typeArgumentMap = call.typeArguments
    val typeArguments = call.candidateDescriptor.typeParameters.mapNotNull { typeArgumentMap[it] }
    val renderedList = typeArguments.joinToString(prefix = "<", postfix = ">") {
        IdeDescriptorRenderers.SOURCE_CODE_NOT_NULL_TYPE_APPROXIMATION.renderType(it.unCapture())
    }

    return KtPsiFactory(initializer.project).createTypeArguments(renderedList)
}

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

val PsiFile.isInjectedFragment: Boolean
    get() = InjectedLanguageManager.getInstance(project).isInjectedFragment(this)

val PsiElement.isInsideInjectedFragment: Boolean
    get() = containingFile.isInjectedFragment

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

fun getSuperMethods(declaration: KtDeclaration, ignore: Collection<PsiElement>?): List<PsiElement> {
    if (!declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return listOf(declaration)
    val (_, overriddenElementsToDescriptor) = getSuperDescriptors(declaration, ignore)
    return if (overriddenElementsToDescriptor.isEmpty()) listOf(declaration) else overriddenElementsToDescriptor.keys.toList()
}

fun KtNamedDeclaration.isCompanionMemberOf(klass: KtClassOrObject): Boolean {
    val containingObject = containingClassOrObject as? KtObjectDeclaration ?: return false
    return containingObject.isCompanion() && containingObject.containingClassOrObject == klass
}

internal fun KtDeclaration.resolveToExpectedDescriptorIfPossible(): DeclarationDescriptor {
    val descriptor = unsafeResolveToDescriptor()
    return descriptor.liftToExpected() ?: descriptor
}

fun DialogWrapper.showWithTransaction() {
    TransactionGuard.submitTransaction(disposable, Runnable { show() })
}

fun PsiMethod.checkDeclarationConflict(name: String, conflicts: MultiMap<PsiElement, String>, callables: Collection<PsiElement>) {
    containingClass
        ?.findMethodsByName(name, true)
        // as is necessary here: see KT-10386
        ?.firstOrNull { it.parameterList.parametersCount == 0 && !callables.contains(it.namedUnwrappedElement as PsiElement?) }
        ?.let { reportDeclarationConflict(conflicts, it) { s -> "$s already exists" } }
}

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

@Deprecated(
    "Use org.jetbrains.kotlin.idea.base.psi.getLineCount() instead",
    ReplaceWith("this.getLineCount()", "org.jetbrains.kotlin.idea.base.psi.getLineCount"),
    DeprecationLevel.ERROR
)
fun PsiElement.getLineCount(): Int {
    return newGetLineCount()
}

@Deprecated(
    "Use org.jetbrains.kotlin.idea.core.util.toPsiDirectory() instead",
    ReplaceWith("this.toPsiDirectory(project)", "org.jetbrains.kotlin.idea.core.util.toPsiDirectory"),
    DeprecationLevel.ERROR
)
fun VirtualFile.toPsiDirectory(project: Project): PsiDirectory? {
    return newToPsiDirectory(project)
}

@Deprecated(
    "Use org.jetbrains.kotlin.idea.core.util.toPsiFile() instead",
    ReplaceWith("this.toPsiFile(project)", "org.jetbrains.kotlin.idea.core.util.toPsiFile"),
    DeprecationLevel.ERROR
)
fun VirtualFile.toPsiFile(project: Project): PsiFile? {
    return newToPsiFile(project)
}