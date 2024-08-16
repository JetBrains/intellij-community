// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.*
import com.intellij.modcommand.*
import com.intellij.openapi.application.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.psi.*
import com.intellij.psi.PsiElementVisitor.EMPTY_VISITOR
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.findParentOfType
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.externalSystem.KotlinBuildSystemFacade
import org.jetbrains.kotlin.idea.base.externalSystem.KotlinBuildSystemSourceSet
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.isTestModule
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.isAndroidModule
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.core.overrideImplement.MemberGenerateMode
import org.jetbrains.kotlin.idea.core.overrideImplement.generateMember
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.createKotlinFile
import org.jetbrains.kotlin.idea.searching.kmp.findAllActualForExpect
import org.jetbrains.kotlin.idea.util.sourceRoot
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.tooling.core.withClosure
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

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
                if (parentDeclaration.hasOptionalExpectationAnnotation()) return

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
                    generateMember(
                        project = expectModule.project,
                        ktClassMember = null,
                        symbol = parentDeclaration.symbol as? KaCallableSymbol ?: return@analyze null,
                        targetClass = null,
                        copyDoc = false,
                        mode = MemberGenerateMode.ACTUAL
                    )
                }

                val fixes = allModulesCapableOfProvidingActuals.mapIndexedNotNull map@{ index, actualModule ->
                    CreateActualForExpectLocalQuickFix(
                        priority = index, moduleName = actualModule.name,
                        sourceSet = KotlinBuildSystemFacade.getInstance().findSourceSet(actualModule) ?: return@map null,
                        actualDeclaration = actualDeclaration?.createSmartPointer() ?: return@map null,
                    )
                }

                // We only care about the name of the target, not the common submodule of the MPP module
                val missingModulesWithActuals = missingActuals.joinToString { it.name.substringAfterLast('.') }

                holder.registerProblem(
                    expectModifier, KotlinBundle.message("no.actual.for.expect.declaration", missingModulesWithActuals),
                    *fixes.toTypedArray()
                )
            }
        }
    }
}

private class CreateActualForExpectLocalQuickFix(
    private val priority: Int,
    private val moduleName: String,
    private val sourceSet: KotlinBuildSystemSourceSet,
    private val actualDeclaration: SmartPsiElementPointer<KtDeclaration>,
) : LocalQuickFix {

    override fun startInWriteAction(): Boolean {
        return true
    }

    override fun getName(): String {
        /**
         * We're using a 'zero width space' character to influence the sorting of the QuickFix as they
         * are traditionally just sorted by their name.
         */
        val sortingWhiteSpace = buildString(priority) { repeat(priority) { append('\u200B') } }
        return KotlinBundle.message("create.actual.in.0", "$sortingWhiteSpace${sourceSet.name}")
    }

    override fun getFamilyName(): String {
        return KotlinBundle.message("create.actual")
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val module = ModuleManager.getInstance(project).findModuleByName(moduleName) ?: return
        val expectDeclaration = descriptor.psiElement.findParentOfType<KtDeclaration>() ?: return
        val expectPackageFqn = expectDeclaration.containingKtFile.packageFqName

        /**
         * Where is the 'expect' declaration located in regard to its source root?
         * For example, src/commonMain/kotlin/my/pkg/foo/Foo.kt
         *                            <my/pkg/foo>
         * This relative path can then be used to put the actual under the same relative path.
         * Note: The package name is used as convention.
         */
        val expectRelativePackagePath: String = run {
            val expectSourceFileDirectory = expectDeclaration.containingKtFile.containingDirectory?.virtualFile ?: return@run null
            val expectSourceRootDirectory = expectDeclaration.containingKtFile.sourceRoot ?: return@run null
            VfsUtil.getRelativePath(expectSourceFileDirectory, expectSourceRootDirectory)
        } /* Fallback: Construct a path from packageFqName */ ?: expectPackageFqn.pathSegments().joinToString(VfsUtil.VFS_SEPARATOR)


        /**
         * Let's get a directory in the [sourceSet] that would allow us to find or create
         * the corresponding KtFile. Create the source root in the IJ model if necessary.
         */
        val actualDirectory: PsiDirectory = run {
            val actualSourceRoot = sourceSet.findOrCreateMostSuitableKotlinSourceRoot() ?: return
            val modifiableModel = module.rootManager.modifiableModel
            val contentEntry = modifiableModel.contentEntries.find { contentEntry ->
                contentEntry.file?.let { contentEntryFile -> VfsUtil.isAncestor(contentEntryFile, actualSourceRoot, false) } ?: false
            } ?: return

            if (contentEntry.sourceFolders.none { it.file == actualSourceRoot }) {
                contentEntry.addSourceFolder(actualSourceRoot, module.isTestModule)
            }
            modifiableModel.commit()
            val actualDirectory = expectRelativePackagePath.split(VfsUtil.VFS_SEPARATOR)
                .fold(actualSourceRoot) { acc, segment -> acc.findOrCreateDirectory(segment) }
            PsiManager.getInstance(project).findDirectory(actualDirectory) ?: return
        }

        /**
         * Find an existing KtFile that also matches the package name or create a new file:
         * Note: We've located the [actualDirectory], and we know what the target file name shall be,
         * but this file might already exist, but might have a different package name (which would not match).
         * In this case we try to find another file which does not yet exist or has a matching package name.
         */
        val actualKtFile: KtFile = run {
            val fileName = getActualTargetFileName(module, sourceSet, expectDeclaration.containingKtFile)
            /*
            We attempt to find or create the file. If there exists af file with the current fileName, which
            cannot be re-used (e.g., because of a non-fitting package), then we try to find or create
            another file (by counting up foo1.kt, foo2.kt, foo3.kt ...)
             */
            val fileNameCandidates = listOf("$fileName.kt") + (1..16).map { "${fileName}$it.kt" }
            fileNameCandidates.firstNotNullOfOrNull findOrCreate@{ fileNameCandidate ->
                val existing = actualDirectory.findFile(fileNameCandidate)
                /* No file with this fileName exists: We can try to create it! */
                    ?: return@findOrCreate createKotlinFile(
                        fileNameCandidate, actualDirectory, expectPackageFqn.asString()
                    )

                /* Check: The existing file could still have a different package FQN: In this case, we cannot re-use it! */
                (existing as? KtFile)?.takeIf { file -> file.packageFqName == expectPackageFqn }
            }
        } ?: return

        /**
         * The above call to [findOrCreateKtFileForActualDeclaration] might have created a new source root and therefore triggered
         * dumb mode. We execute the action right away because we require smart mode for creating the 'actual' actual (pun intended)
         */
        DumbService.getInstance(project).completeJustSubmittedTasks()

        val added = actualKtFile.add(actualDeclaration.element ?: return).reformatted() as? KtNamedDeclaration ?: return
        val shortened = ShortenReferencesFacility.getInstance().shorten(added) as? KtDeclaration

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
            val target = shortened ?: added
            val blockExpression = target.childrenOfType<KtBlockExpression>().firstOrNull() ?: return@run target
            blockExpression.children.first() as? KtElement ?: return@run target
        }

        navigationTarget.navigate(true)

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