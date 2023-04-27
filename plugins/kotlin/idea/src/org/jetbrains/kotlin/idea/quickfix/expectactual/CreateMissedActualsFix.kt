// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.expectactual

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.icons.AllIcons
import com.intellij.ide.wizard.setMinimumWidthForAllRowLabels
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.externalSystem.service.project.manage.SourceFolderManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.psi.PsiDirectory
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComponentPredicate
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.kotlinSourceRootType
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.core.PureKotlinSourceFoldersHolder
import org.jetbrains.kotlin.idea.core.findExistingNonGeneratedKotlinSourceRootFiles
import org.jetbrains.kotlin.idea.core.findOrConfigureKotlinSourceRoots
import org.jetbrains.kotlin.idea.core.toDescriptor
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.refactoring.getOrCreateKotlinFile
import org.jetbrains.kotlin.idea.util.application.isHeadlessEnvironment
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.idea.util.sourceRoot
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.isMultiPlatform
import org.jetbrains.kotlin.psi.*
import java.io.File
import java.nio.file.Path
import javax.swing.JComponent
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

class CreateMissedActualsFix(
    val declaration: KtNamedDeclaration,
    val notActualizedLeafModules: Collection<Module>
) : KotlinQuickFixAction<KtNamedDeclaration>(declaration) {

    override fun startInWriteAction(): Boolean = false

    override fun getFamilyName(): String = KotlinBundle.message("fix.create.expect.actual")

    override fun getText(): String = KotlinBundle.message("fix.create.missing.actual.declarations")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val moduleWithExpect = declaration.module ?: return
        val simpleModuleNames = moduleWithExpect.implementingModules.plus(moduleWithExpect).getSimpleSourceSetNames()

        val testMode = isUnitTestMode() || isHeadlessEnvironment()

        if (!testMode) {
            CreateMissedActualsDialog(
                project,
                editor,
                declaration,
                moduleWithExpect,
                notActualizedLeafModules,
                simpleModuleNames
            ).show()
        } else {
            val defaultPath = declaration.containingKtFile.let { f ->
                f.virtualFilePath.removePrefix(f.sourceRoot?.path.orEmpty())
            }
            generateActualsForSelectedModules(
                project,
                editor,
                declaration,
                defaultPath,
                notActualizedLeafModules,
                simpleModuleNames
            )
        }
    }

    //returns modules with associated names which are cleared from common prefix
    private fun Collection<Module>.getSimpleSourceSetNames(): Map<Module, String> {
        if (this.size < 2) return this.associateWith { it.name }
        val commonPrefix = this.map { it.name }.zipWithNext().map { (a, b) -> a.commonPrefixWith(b) }.minBy { it.length }
        val prefixToRemove = commonPrefix.substringBeforeLast(".", "").let { if (it.isEmpty()) it else "$it." }
        return this.associateWith { it.name.removePrefix(prefixToRemove) }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? =
            createIntentionAction(listOf(diagnostic))

        override fun doCreateActionsForAllProblems(sameTypeDiagnostics: Collection<Diagnostic>): List<IntentionAction> =
            listOfNotNull(createIntentionAction(sameTypeDiagnostics))

        private fun createIntentionAction(sameTypeDiagnostics: Collection<Diagnostic>): IntentionAction? {
            val first = sameTypeDiagnostics.firstOrNull() ?: return null
            val declaration = first.psiElement as? KtNamedDeclaration ?: return null
            val diagnostics = sameTypeDiagnostics
                .filter { it.psiElement == declaration }
                .map { DiagnosticFactory.cast(it, Errors.NO_ACTUAL_FOR_EXPECT) }
                .filter {
                    val compatibility = it.c
                    // For function declarations we allow it, because overloads are possible
                    compatibility.isEmpty() || declaration is KtFunction
                }
            val notActualizedLeafModules = diagnostics.mapNotNull {
                val actualModuleDescriptor = it.b
                (actualModuleDescriptor.getCapability(ModuleInfo.Capability) as? ModuleSourceInfo)?.module
            }
            if (notActualizedLeafModules.isEmpty()) return null
            return CreateMissedActualsFix(declaration, notActualizedLeafModules)
        }
    }
}

private val LOG = Logger.getInstance(CreateMissedActualsDialog::class.java)

private class CreateMissedActualsDialog(
    val project: Project,
    val editor: Editor?,
    val declaration: KtNamedDeclaration,
    val moduleWithExpect: Module,
    val notActualizedLeafModules: Collection<Module>,
    val simpleModuleNames: Map<Module, String>
) : DialogWrapper(project, true) {

    private val notActualizedModules = getNotActualizedModules()

    private var filePathTextField: JBTextField? = null
    private val filePathProperty = AtomicProperty(
        declaration.containingKtFile.let { file ->
            file.virtualFilePath.removePrefix(file.sourceRoot?.path.orEmpty())
        }
    )

    private val selectedModules = mutableListOf<Module>()
    private val selectedModulesListeners = mutableListOf<() -> Unit>()

    init {
        title = KotlinBundle.message("fix.create.missing.actual.declarations.title")
        init()
    }

    override fun doValidate(): ValidationInfo? {
        val filePath = getNewFilePathForModule(moduleWithExpect).nameWithoutExtension
        if (filePath.isBlank()) {
            return ValidationInfo(KotlinBundle.message("text.file.name.cannot.be.empty"), filePathTextField)
        }

        return null
    }

    override fun doOKAction() {
        super.doOKAction()
        generateActualsForSelectedModules(
            project,
            editor,
            declaration,
            filePathProperty.get(),
            selectedModules,
            simpleModuleNames
        )
    }

    override fun createCenterPanel(): JComponent = panel {
        val sortedModules = moduleWithExpect.sortChildrenByHMPP()

        row(KotlinBundle.message("label.text.package.name")) {
            textField()
                .text(declaration.containingKtFile.packageFqName.asString())
                .align(AlignX.FILL)
                .enabled(false)
        }

        row(KotlinBundle.message("label.text.file.name")) {
            textField()
                .align(AlignX.FILL)
                .bindText(filePathProperty)
                .also { filePathTextField = it.component }
        }

        row {
            panel {
                row { label(KotlinBundle.message("label.text.source.sets")) }
                separator()
                sortedModules.forEach { item ->
                    val selectionPredicate = ModuleCheckBoxPredicate(item.module)
                    row {
                        val checkbox = checkBox("   ".repeat(item.level))
                            .onChanged { cb ->
                                if (cb.isSelected) {
                                    selectedModules.add(item.module)
                                } else {
                                    selectedModules.remove(item.module)
                                }
                                selectedModulesListeners.forEach { listener -> listener() }
                            }
                        icon(AllIcons.Actions.ModuleDirectory)
                        label(simpleModuleNames.getOrDefault(item.module, item.module.name))
                        label("")
                            .bindText(filePathProperty.transform { File.separator + getNewFilePathForModule(item.module) })
                            .enabled(false)
                            .visibleIf(checkbox.selected)
                    }.enabledIf(selectionPredicate)
                }
            }
        }
    }.apply {
        withMinimumWidth(500)
        setMinimumWidthForAllRowLabels(90)
    }

    private data class ModuleWithLevel(val module: Module, val level: Int)

    private fun Module.sortChildrenByHMPP(): List<ModuleWithLevel> {
        fun Module.getChildren(): List<Module> {
            val implementing = implementingModules.toSet()
            return implementing.filter { module ->
                module.implementedModules.all { it !in implementing }
            }
        }

        fun sort(parent: Module, level: Int): List<ModuleWithLevel> {
            val children = parent.getChildren().sortedBy { it.name }
            if (children.isEmpty()) return emptyList()
            return children.flatMap { listOf(ModuleWithLevel(it, level)).plus(sort(it, level + 1)) }
        }

        //'distinct' filters duplicates in a ruby-like dependencies case
        return sort(this, 0).distinctBy { it.module.name }
    }

    private fun getNotActualizedModules(): List<Module> {
        val allModules = moduleWithExpect.implementingModules

        val allLeafModules = allModules.filter { module ->
            //`isMultiPlatform` is just for optimization
            !module.platform.isMultiPlatform() && module.implementingModules.isEmpty()
        }
        val actualizedLeafModules = allLeafModules.filter { module ->
            module !in notActualizedLeafModules
        }
        val allActualizedModules = actualizedLeafModules
            .flatMap { module -> module.implementedModules }
            .plus(actualizedLeafModules)

        return allModules.filter { module -> module !in allActualizedModules }
    }

    private fun getNewFilePathForModule(module: Module) =
        getNewFilePathForModule(filePathProperty.get(), module, simpleModuleNames)

    //Observer for UI changes
    private inner class ModuleCheckBoxPredicate(val module: Module) : ComponentPredicate() {
        override fun addListener(listener: (Boolean) -> Unit) {
            val onChange: () -> Unit = { listener(invoke()) }
            selectedModulesListeners.add(onChange)
        }

        override fun invoke(): Boolean {
            val shouldBeActualized = module in notActualizedModules
            val anyParentOrChildIsSelected = module.implementedModules.plus(module.implementingModules).any { it in selectedModules }
            return shouldBeActualized && !anyParentOrChildIsSelected
        }
    }
}

private fun getNewFilePathForModule(commonFilePath: String, module: Module, simpleModuleNames: Map<Module, String>): Path {
    val simpleName = simpleModuleNames[module].orEmpty()
    val suffixToRemove = listOf("Main", "Test").firstOrNull { simpleName.endsWith(it) }.orEmpty()
    val modulePlatformName = simpleName.removeSuffix(suffixToRemove).takeIf { it.isNotBlank() }
    return Path(
        commonFilePath.removePrefix(File.separator).removeSuffix(".kt") +
                modulePlatformName?.let { ".$it" }.orEmpty() + ".kt"
    )
}

private fun getOrCreateKotlinFileForSpecificPackage(
    fileName: String,
    targetDir: PsiDirectory,
    declarationPackage: FqName
): KtFile {
    var file = getOrCreateKotlinFile(fileName, targetDir, declarationPackage.asString())
    var fileIndex = 0

    //if file exists and has other declarations with a different package, we have to generate a new file
    fun KtFile.needOtherFile() = declarations.isNotEmpty() && packageDirective?.fqName != declarationPackage

    while (file.needOtherFile()) {
        fileIndex++
        val newName = with(fileName) {
            val (name, suffixAndExtension) = split(".", limit = 2)
            "$name$fileIndex.$suffixAndExtension"
        }
        file = getOrCreateKotlinFile(newName, targetDir, declarationPackage.asString())
    }
    return file
}

private fun Module.selectExistingSourceRoot(
    pureKotlinSourceFoldersHolder: PureKotlinSourceFoldersHolder
): VirtualFile? {
    val roots = findExistingNonGeneratedKotlinSourceRootFiles(pureKotlinSourceFoldersHolder).sortedBy { it.path }

    if (roots.size < 2) return roots.firstOrNull()

    val root = roots.firstOrNull {
        it.name.equals("kotlin", true)
    } ?: roots.first()
    LOG.warn("${this.name} contains more then one source roots. ${root.name} was selected.")
    return root
}

private fun generateActualsForSelectedModules(
    project: Project,
    editor: Editor?,
    declaration: KtNamedDeclaration,
    commonFilePath: String,
    selectedModules: Collection<Module>,
    simpleModuleNames: Map<Module, String>
) {
    val sourceFolderManager = SourceFolderManager.getInstance(project)
    val pureKotlinSourceFoldersHolder = PureKotlinSourceFoldersHolder()
    val moduleSourceRoots = selectedModules.associateWith { module ->
        module.selectExistingSourceRoot(pureKotlinSourceFoldersHolder) ?: run {
            val newRoot = module.findOrConfigureKotlinSourceRoots(pureKotlinSourceFoldersHolder)
                .also { if (it.size > 1) LOG.warn("In ${module.name} were configured more then one source roots. ${it.first().name} was selected.") }
                .firstOrNull()
            if (newRoot == null) {
                LOG.warn("Can't configure new source root for ${module.name}")
                return@associateWith null
            }

            val sourceRootType = module.kotlinSourceRootType
            if (sourceRootType == null) {
                LOG.warn("Can't configure new source root for ${module.name} because `kotlinSourceRootType == null`")
                return@associateWith null
            }

            sourceFolderManager.addSourceFolder(module, newRoot.url, sourceRootType)
            newRoot
        }
    }
    sourceFolderManager.rescanAndUpdateSourceFolders()

    runWriteAction {
        selectedModules.forEach { module ->
            val root = moduleSourceRoots[module] ?: return@forEach
            val moduleFile = getNewFilePathForModule(commonFilePath, module, simpleModuleNames)
            val dir: PsiDirectory = declaration.manager.findDirectory(
                root.findOrCreateDirectory(moduleFile.parent?.pathString.orEmpty())
            ) ?: return@forEach

            val file = getOrCreateKotlinFileForSpecificPackage(moduleFile.name, dir, declaration.containingKtFile.packageFqName)

            if (declaration is KtCallableDeclaration) {
                generateExpectOrActualInFile(
                    project,
                    editor,
                    declaration.containingKtFile,
                    file,
                    null,
                    declaration,
                    module
                ) block@{ project, checker, element ->
                    if (!checker.isCorrectAndHaveAccessibleModifiers(element, true)) return@block null
                    val descriptor = element.toDescriptor() as? CallableMemberDescriptor

                    descriptor?.let { generateCallable(project, false, element, descriptor, checker = checker) }
                }
            } else if (declaration is KtClassOrObject) {
                generateExpectOrActualInFile(
                    project,
                    editor,
                    declaration.containingKtFile,
                    file,
                    null,
                    declaration,
                    module
                ) block@{ project, checker, element ->
                    checker.findAndApplyExistingClasses(element.collectDeclarationsForAddActualModifier().toList())
                    if (!checker.isCorrectAndHaveAccessibleModifiers(element, true)) return@block null

                    generateClassOrObject(project, false, element, checker = checker)
                }
            }
        }
    }
}