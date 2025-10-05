// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiElementVisitor.EMPTY_VISITOR
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.findParentOfType
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.ui.JBEmptyBorder
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.externalSystem.KotlinBuildSystemFacade
import org.jetbrains.kotlin.idea.base.externalSystem.KotlinBuildSystemSourceSet
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.isAndroidModule
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.core.createKotlinFile
import org.jetbrains.kotlin.idea.core.overrideImplement.MemberGenerateMode
import org.jetbrains.kotlin.idea.core.overrideImplement.generateClassWithMembers
import org.jetbrains.kotlin.idea.core.overrideImplement.generateMember
import org.jetbrains.kotlin.idea.searching.kmp.findAllActualForExpect
import org.jetbrains.kotlin.idea.util.sourceRoot
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.tooling.core.withClosure
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * Inspects expect declarations and reports missing actual declarations.
 * - Regular expect declarations: highlighted as ERROR/WARNING 
 * - Optional expect declarations (with @OptionalExpectation): highlighted as INFORMATION
 */
class KotlinNoActualForExpectInspection : AbstractKotlinInspection() {

    private fun Module.getLeaves(): Set<Module> {
        val implementingModules = implementingModules
        if (implementingModules.isEmpty()) return setOf(this)
        return implementingModules.flatMapTo(mutableSetOf()) { it.getLeaves() }
    }

    private fun Module.hasActualInParentOrSelf(allModulesWithActual: Set<Module>): Boolean {
        return this in allModulesWithActual || implementedModules.any { it in allModulesWithActual }
    }

    private fun KtDeclaration.hasOptionalExpectationAnnotation(): Boolean {
        if (annotationEntries.isEmpty()) return false
        return analyze(this) {
            symbol.annotations.any { annotation ->
                annotation.classId == StandardClassIds.Annotations.OptionalExpectation
            }
        }
    }

    private val actualsSearchScopeKey = Key.create<GlobalSearchScope>("actualsSearchScope")
    private fun LocalInspectionToolSession.getActualsSearchScope(module: Module): GlobalSearchScope {
        return getOrCreateUserData(actualsSearchScopeKey) {
            GlobalSearchScope.union((module.implementingModules + module).map { it.moduleScope })
        }
    }

    private val moduleLeavesKey = Key.create<Set<Module>>("moduleLeaves")
    private fun LocalInspectionToolSession.getLeaves(module: Module): Set<Module> {
        return getOrCreateUserData(moduleLeavesKey) {
            module.getLeaves()
        }
    }

    @OptIn(KaExperimentalApi::class)
    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession
    ): PsiElementVisitor {
        val module = holder.file.module ?: return EMPTY_VISITOR
        if (!module.isMultiPlatformModule) return EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitModifierList(list: KtModifierList) {
                val expectModule = list.module ?: return
                val expectModifier = list.getModifier(KtTokens.EXPECT_KEYWORD) ?: return
                val parentDeclaration = list.findParentOfType<KtDeclaration>() ?: return
                val isOptionalExpectation = parentDeclaration.hasOptionalExpectationAnnotation()

                val leaves = session.getLeaves(expectModule)
                val foundActuals = parentDeclaration.findAllActualForExpect(session.getActualsSearchScope(expectModule))
                    .mapNotNullTo(mutableSetOf()) { it.element?.module }.toSet()

                val missingActuals = leaves.filter { module ->
                    !module.hasActualInParentOrSelf(foundActuals)
                }
                if (missingActuals.isEmpty()) return

                /*
                Will return all modules that could potentially fix the issue by providing the actual.
                In the example above, it will return `nativeMain`, `appleMain`, `iosMain`, `iosX64Main` iosArm64Main`
                 */
                val allModulesCapableOfProvidingActuals = missingActuals
                    .withClosure<Module> { leafModule -> leafModule.implementedModules - expectModule }
                    .reversed() /* reversing the list to ensure that modules "closest to the 'expect'" come first. */

                val actualDeclaration = analyze(parentDeclaration) {
                    val declarationSymbol = parentDeclaration.symbol
                    val project = expectModule.project
                    when (declarationSymbol) {
                        is KaCallableSymbol -> generateMember(
                            project = project,
                            ktClassMember = null,
                            symbol = declarationSymbol,
                            targetClass = null,
                            copyDoc = false,
                            mode = MemberGenerateMode.ACTUAL
                        )

                        is KaClassSymbol -> generateClassWithMembers(
                            project = project,
                            ktClassMember = null,
                            symbol = declarationSymbol,
                            targetClass = null,
                            mode = MemberGenerateMode.ACTUAL
                        )

                        else -> null
                    }
                }

                val actualDeclarations = allModulesCapableOfProvidingActuals.mapNotNull { actualModule ->
                    val sourceSet = KotlinBuildSystemFacade.getInstance().findSourceSet(actualModule) ?: return@mapNotNull null
                    val actualDeclaration = actualDeclaration ?: return@mapNotNull null
                    ActualDeclaration(actualModule, actualDeclaration, sourceSet)
                }

                // We only care about the name of the target, not the common submodule of the MPP module
                val missingModulesWithActuals = missingActuals.joinToString { it.name.substringAfterLast('.') }

                val fixes = if (!actualDeclarations.isEmpty()) {
                    arrayOf(CreateActualForExpectLocalQuickFix(actualDeclarations))
                } else {
                    emptyArray()
                }
                
                // Use different highlight types: INFO for optional expectations, default (WARNING) for regular expectations
                val highlightType = if (isOptionalExpectation) ProblemHighlightType.INFORMATION else ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                val message = if (isOptionalExpectation) {
                    KotlinBundle.message("fix.create.missing.actual.declarations")
                } else {
                    KotlinBundle.message("no.actual.for.expect.declaration", missingModulesWithActuals)
                }
                
                holder.registerProblem(
                    expectModifier,
                    message,
                    highlightType,
                    *fixes
                )
            }
        }
    }
}

data class ActualDeclaration(val module: Module, val declarationFromText: KtDeclaration, val sourceSet: KotlinBuildSystemSourceSet)

internal class CreateActualForExpectLocalQuickFix(
    val actualDeclarations: List<ActualDeclaration>
) : LocalQuickFix {

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun availableInBatchMode(): Boolean = false

    // NOTE: Works for IJ (not for Fleet, because Fleet has different logic for preview)
    // Quick-fix requires a WriteLock to create a new file or find an existing one, so the preview can't be shown by running fix,
    // because `generatePreview` is called outside the write action.
    // Instead, a custom preview is displayed.
    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
        val (_, declaration, sourceSet) = actualDeclarations.first()
        val declarationText = declaration.copied().reformatted().text

        return IntentionPreviewInfo.CustomDiff(
            KotlinFileType.INSTANCE,
            sourceSet.name,
            /* origText = */ "",
            declarationText,
            /* lineNumbers = */ true
        )
    }

    override fun getName(): String {
        if (actualDeclarations.size == 1) {
            return KotlinBundle.message("create.actual.in.0", actualDeclarations.first().module.name)
        } else {
            return KotlinBundle.message("create.actuals")
        }
    }

    override fun getFamilyName(): String {
        return KotlinBundle.message("create.actual")
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        if (actualDeclarations.size == 1) {
            runWriteAction { doCreateActualFix(project, descriptor, actualDeclarations.first()) }?.let {
                navigateToActual(it, requestFocus = true)
            }
        } else {

            val expectedModule = descriptor.psiElement.module ?: return

            val modulesFromDeclarations = actualDeclarations.map { it.module }

            data class ModuleWithLevel(val module: Module, val level: Int)

            fun buildLeveledList(currentModule: Module, level: Int): List<ModuleWithLevel> {
                val implementing = currentModule.implementingModules.toSet()
                val children = implementing.filter { module -> module.implementedModules.all { it !in implementing } }.sortedBy { it.name }
                if (children.isEmpty()) return emptyList()
                return children.flatMap { listOf(ModuleWithLevel(it, level)).plus(buildLeveledList(it, level + 1)) }
            }

            val sortedModules = buildLeveledList(expectedModule, 0)
                .distinctBy { it.module.name }
                .filter {
                    it.module in modulesFromDeclarations
                }

            val popup = JBPopupFactory.getInstance().createPopupChooserBuilder(sortedModules)
                .setTitle(KotlinBundle.message("choose.actual.module"))
                .setItemsChosenCallback { modules ->
                    val actualDeclarations = WriteCommandAction.writeCommandAction(project).withName(familyName)
                        .compute(ThrowableComputable<List<KtDeclaration>, Throwable> {
                            val selectedPlatforms = modules.map { it.module.platform }.toMutableSet()
                            selectedPlatforms.removeIf { currentPlatform -> selectedPlatforms.any { it != currentPlatform && it.componentPlatforms.containsAll(currentPlatform.componentPlatforms ) } }
                            modules.mapNotNull { moduleWithLevel -> if (moduleWithLevel.module.platform in selectedPlatforms) doCreateActualFix(project, descriptor, actualDeclarations.find { it.module == moduleWithLevel.module }!!) else null }
                        })
                    val last = actualDeclarations.lastOrNull()
                    for (declaration in actualDeclarations) {
                        navigateToActual(declaration, declaration == last)
                    }
                }
                .setRenderer(object : DefaultListCellRenderer() {
                    override fun getListCellRendererComponent(
                        list: JList<*>?,
                        value: Any?,
                        index: Int,
                        isSelected: Boolean,
                        cellHasFocus: Boolean
                    ): Component {
                        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                        if (value is ModuleWithLevel) {
                            val (_, _, sourceSet) = actualDeclarations.find { it.module == value.module }!!
                            (component as JLabel).text = "   ".repeat(value.level) + sourceSet.name
                            component.border = JBEmptyBorder(5)
                        }
                        return component
                    }
                })
                .createPopup()
            popup.showCenteredInCurrentWindow(project)
        }
    }

    private fun doCreateActualFix(project: Project, descriptor: ProblemDescriptor, actualDeclaration: ActualDeclaration): KtDeclaration? {
        val expectDeclaration = descriptor.psiElement.findParentOfType<KtDeclaration>() ?: return null
        val expectRelativePackagePath = expectDeclaration.relativePackagePath
        val actualDirectory = findOrCreateActualDeclarationDirectory(project, actualDeclaration, expectRelativePackagePath) ?: return null
        val actualKtFile = findOrCreateActualDeclarationFile(actualDeclaration, expectDeclaration, actualDirectory) ?: return null

        /**
         * The above call to [findOrCreateActualDeclarationFile] might have created a new source root and therefore triggered
         * dumb mode. We execute the action right away because we require smart mode for creating the 'actual' actual (pun intended)
         */
        DumbService.getInstance(project).completeJustSubmittedTasks()

        val added = actualKtFile.add(actualDeclaration.declarationFromText).reformatted() as? KtNamedDeclaration ?: return null
        return ShortenReferencesFacility.getInstance().shorten(added) as? KtDeclaration ?: added
    }

    private fun navigateToActual(target: KtDeclaration, requestFocus: Boolean) {
        /**
         * Find the navigation target:
         *
         * For example in:
         * ```
         * actual fun bar() {
         *     T0D0("Not yet implemented")
         * }
         *
         * ```
         *
         * it would be convenient if we navigate to the 'T0D0' and select the expression, so that
         * writing the implementation can be started right away!
         */
        val navigationTarget = run {
            val blockExpression = target.childrenOfType<KtBlockExpression>().firstOrNull() ?: return@run target
            blockExpression.children.firstOrNull() as? KtElement ?: return@run target
        }

        navigationTarget.navigate(requestFocus)
    }

    /**
     * Where is the 'expect' declaration located in regard to its source root?
     * For example, src/commonMain/kotlin/my/pkg/foo/Foo.kt
     *                            <my/pkg/foo>
     * This relative path can then be used to put the actual under the same relative path.
     * Note: The package name is used as convention.
     */
    private val KtDeclaration.relativePackagePath: String
        get() {
            val expectDeclaration = this
            val ktFile = expectDeclaration.containingKtFile

            val expectRelativePackagePath: String? = run {
                val expectSourceFileDirectory = ktFile.containingDirectory?.virtualFile ?: return@run null
                val expectSourceRootDirectory = ktFile.sourceRoot ?: return@run null
                VfsUtil.getRelativePath(expectSourceFileDirectory, expectSourceRootDirectory)
            }

            return expectRelativePackagePath
            /* Fallback: Construct a path from packageFqName */
                ?: ktFile.packageFqName.pathSegments().joinToString(VfsUtil.VFS_SEPARATOR)
        }

    /**
     * Get a directory in the [sourceSet] that would allow us to find or create the corresponding KtFile.
     * Create the source root in the IJ model if necessary.
     */
    @RequiresWriteLock
    private fun findOrCreateActualDeclarationDirectory(project: Project, actualDeclaration: ActualDeclaration, expectRelativePackagePath: String): PsiDirectory? {
        val actualSourceRoot = actualDeclaration.sourceSet.findOrCreateMostSuitableKotlinSourceRoot() ?: return null

        val actualDirectory = expectRelativePackagePath
            .split(VfsUtil.VFS_SEPARATOR)
            .fold(actualSourceRoot) { acc, segment -> acc.findOrCreateDirectory(segment) }

        return PsiManager.getInstance(project).findDirectory(actualDirectory)
    }

    /**
     * Find an existing KtFile that also matches the package name or create a new file:
     * Note: We've located the [actualDirectory], and we know what the target file name shall be,
     * but this file might already exist, but might have a different package name (which would not match).
     * In this case we try to find another file which does not yet exist or has a matching package name.
     */
    private fun findOrCreateActualDeclarationFile(
        actualDeclaration: ActualDeclaration,
        expectDeclaration: KtDeclaration,
        actualDirectory: PsiDirectory
    ): KtFile? {
        val expectKtFile = expectDeclaration.containingKtFile
        val fileName = getActualTargetFileName(actualDeclaration.module, actualDeclaration.sourceSet, expectKtFile)

        /*
        We attempt to find or create the file. If there exists af file with the current fileName, which
        cannot be re-used (e.g., because of a non-fitting package), then we try to find or create
        another file (by counting up foo1.kt, foo2.kt, foo3.kt ...)
         */
        val fileNameCandidates = listOf("$fileName.kt") + (1..16).map { "${fileName}$it.kt" }
        return fileNameCandidates.firstNotNullOfOrNull { fileNameCandidate ->
            val existing = actualDirectory.findFile(fileNameCandidate)
            /* No file with this fileName exists: We can try to create it! */
                ?: return@firstNotNullOfOrNull createKotlinFile(
                    fileNameCandidate, actualDirectory, expectKtFile.packageFqName.asString()
                )

            /* Check: The existing file could still have a different package FQN: In this case, we cannot re-use it! */
            (existing as? KtFile)?.takeIf { file -> file.packageFqName == expectKtFile.packageFqName }
        }
    }
}

@RequiresWriteLock
private fun KotlinBuildSystemSourceSet.findOrCreateMostSuitableKotlinSourceRoot(): VirtualFile? {
    val sourceDirectoryPath = sourceDirectories
        /*
        Prioritize source directories which contain the name of the source set:
        for example, a 'jvmMain' source set should prioritize '.../src/jvmMain/kotlin'
         */
        .find { path -> path.contains(Path(name)) }
        ?: sourceDirectories.firstOrNull()
        ?: return null

    return VfsUtil.createDirectoryIfMissing(sourceDirectoryPath.absolutePathString())
}


/**
 * Having a Source File with the same name in the same package in several Source Sets might be a problem.
 * e.g. `Foo.kt` in commonMain and  `Foo.kt` in jvmMain could produce a broken .jar file.
 * Therefore, the file gets mangled by adding a classifier from the module as in  `Foo.jvm.kt`
 */
private fun getActualTargetFileName(
    module: Module,
    sourceSet: KotlinBuildSystemSourceSet,
    expect: PsiFile
): String {
    /* File name matches: Let's mangle by adding a module string like '.jvm', '.android', '.ios' */
    val moduleClassifier = if (module.isAndroidModule()) "android"
    else sourceSet.name.removeSuffix("Main").removeSuffix("Test")
    return "${expect.virtualFile.nameWithoutExtension}.$moduleClassifier"
}