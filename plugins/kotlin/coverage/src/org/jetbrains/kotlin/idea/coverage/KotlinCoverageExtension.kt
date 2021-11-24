// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.coverage

import com.intellij.coverage.*
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.core.isInTestSourceContentKotlinAware
import org.jetbrains.kotlin.idea.run.KotlinRunConfiguration
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

class KotlinCoverageExtension : JavaCoverageEngineExtension() {
    override fun isApplicableTo(conf: RunConfigurationBase<*>?): Boolean = conf is KotlinRunConfiguration

    override fun suggestQualifiedName(sourceFile: PsiFile, classes: Array<out PsiClass>, names: MutableSet<String>): Boolean {
        if (sourceFile is KtFile) {
            val qNames = collectGeneratedClassQualifiedNames(findOutputRoots(sourceFile), sourceFile)
            if (qNames != null) {
                names.addAll(qNames)
                return true
            }
        }
        return false
    }

    override fun getSummaryCoverageInfo(
        coverageAnnotator: JavaCoverageAnnotator,
        element: PsiNamedElement
    ): PackageAnnotator.ClassCoverageInfo? {
        if (element is KtClassOrObject) {
            val searchScope = CoverageDataManager.getInstance(element.project)
                ?.currentSuitesBundle
                ?.getSearchScope(element.project) ?: return null
            val vFile = PsiUtilCore.getVirtualFile(element) ?: return null
            if (!searchScope.contains(vFile)) return null
            return coverageAnnotator.getClassCoverageInfo(element.fqName?.asString())
        }
        if (element !is KtFile) {
            return null
        }
        LOG.info("Retrieving coverage for " + element.name)

        val qualifiedNames = collectGeneratedClassQualifiedNames(findOutputRoots(element), element)
        return if (qualifiedNames == null || qualifiedNames.isEmpty()) null else totalCoverageForQualifiedNames(coverageAnnotator, qualifiedNames)
    }

    override fun keepCoverageInfoForClassWithoutSource(bundle: CoverageSuitesBundle, classFile: File): Boolean {
        // TODO check scope and source roots
        return true  // keep everything, sort it out later
    }

    override fun collectOutputFiles(
        srcFile: PsiFile,
        output: VirtualFile?,
        testoutput: VirtualFile?,
        suite: CoverageSuitesBundle,
        classFiles: MutableSet<File>
    ): Boolean {
        if (srcFile is KtFile) {
            val fileIndex = ProjectRootManager.getInstance(srcFile.getProject()).fileIndex
            if (fileIndex.isInLibraryClasses(srcFile.getVirtualFile()) ||
                fileIndex.isInLibrarySource(srcFile.getVirtualFile())
            ) {
                return false
            }

            return runReadAction {
                val outputRoots = findOutputRoots(srcFile) ?: return@runReadAction false
                val existingClassFiles = getClassesGeneratedFromFile(outputRoots, srcFile)
                existingClassFiles.mapTo(classFiles) { File(it.path) }
                true
            }
        }
        return false
    }

    companion object {
        private val LOG = Logger.getInstance(KotlinCoverageExtension::class.java)

        fun collectGeneratedClassQualifiedNames(outputRoots: Array<VirtualFile>?, file: KtFile): List<String>? =
            outputRoots?.flatMap { collectGeneratedClassQualifiedNames(it, file) ?: emptyList() }

        fun collectGeneratedClassQualifiedNames(outputRoot: VirtualFile?, file: KtFile): List<String>? {
            val existingClassFiles = getClassesGeneratedFromFile(outputRoot, file)
            if (existingClassFiles.isEmpty()) {
                return null
            }
            LOG.debug("ClassFiles: [${existingClassFiles.joinToString { it.name }}]")
            return existingClassFiles.map {
                val relativePath = VfsUtilCore.getRelativePath(it, outputRoot!!)!!
                StringUtil.trimEnd(relativePath, ".class").replace("/", ".")
            }
        }

        private fun totalCoverageForQualifiedNames(
            coverageAnnotator: JavaCoverageAnnotator,
            qualifiedNames: List<String>
        ): PackageAnnotator.ClassCoverageInfo {
            val result = PackageAnnotator.ClassCoverageInfo()
            result.totalClassCount = 0
            qualifiedNames.forEach {
                val classInfo = coverageAnnotator.getClassCoverageInfo(it)
                if (classInfo != null) {
                    result.totalClassCount += classInfo.totalClassCount
                    result.coveredClassCount += classInfo.coveredClassCount
                    result.totalMethodCount += classInfo.totalMethodCount
                    result.coveredMethodCount += classInfo.coveredMethodCount
                    result.totalLineCount += classInfo.totalLineCount
                    result.fullyCoveredLineCount += classInfo.fullyCoveredLineCount
                    result.partiallyCoveredLineCount += classInfo.partiallyCoveredLineCount
                } else {
                    LOG.debug("Found no coverage for $it")
                }
            }
            return result
        }


        private fun getClassesGeneratedFromFile(outputRoots: Array<VirtualFile>, file: KtFile): List<VirtualFile> =
            outputRoots.flatMap { getClassesGeneratedFromFile(it, file) }

        private fun getClassesGeneratedFromFile(outputRoot: VirtualFile?, file: KtFile): List<VirtualFile> {
            val relativePath = file.packageFqName.asString().replace('.', '/')
            val packageOutputDir = outputRoot?.findFileByRelativePath(relativePath) ?: return listOf()

            val prefixes = collectClassFilePrefixes(file)
            LOG.debug("ClassFile prefixes: [${prefixes.joinToString(", ")}]")
            return packageOutputDir.children.filter { packageFile ->
                prefixes.any {
                    (packageFile.name.startsWith("$it$") && FileUtilRt.getExtension(packageFile.name) == "class") ||
                            packageFile.name == "$it.class"
                }
            }
        }

        private fun findOutputRoots(file: KtFile): Array<VirtualFile>? {
            val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return null
            val fileIndex = ProjectRootManager.getInstance(file.project).fileIndex
            val inTests = fileIndex.isInTestSourceContentKotlinAware(file.virtualFile)
            return JavaCoverageClassesEnumerator.getRoots(CoverageDataManager.getInstance(file.project), module, inTests)
        }

        private fun collectClassFilePrefixes(file: KtFile): Collection<String> {
            val result = file.children.filterIsInstance<KtClassOrObject>().mapNotNull { it.name }
            val packagePartFqName = JvmFileClassUtil.getFileClassInfoNoResolve(file).fileClassFqName
            return result.union(arrayListOf(packagePartFqName.shortName().asString()))
        }
    }
}
