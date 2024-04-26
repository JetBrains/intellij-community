// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.coverage

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.JavaCoverageEngine
import com.intellij.coverage.JavaCoverageEngineExtension
import com.intellij.coverage.analysis.JavaCoverageAnnotator
import com.intellij.coverage.analysis.JavaCoverageClassesEnumerator
import com.intellij.coverage.analysis.PackageAnnotator
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.rt.coverage.data.LineData
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.getKotlinSourceRootType
import org.jetbrains.kotlin.idea.run.KotlinRunConfiguration
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

class KotlinCoverageExtension : JavaCoverageEngineExtension() {
    override fun isApplicableTo(conf: RunConfigurationBase<*>?): Boolean = conf is KotlinRunConfiguration

    override fun suggestQualifiedName(sourceFile: PsiFile, classes: Array<out PsiClass>, names: MutableSet<String>): Boolean {
        if (sourceFile is KtFile) {
            val qNames = collectGeneratedClassQualifiedNames(sourceFile)
            if (!qNames.isNullOrEmpty()) {
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
            val project = element.project
            val bundle = CoverageDataManager.getInstance(project).activeSuites()
                .firstOrNull { it.getAnnotator(project).javaClass == coverageAnnotator.javaClass } ?: return null
            val searchScope = bundle.getSearchScope(project) ?: return null
            val vFile = PsiUtilCore.getVirtualFile(element) ?: return null
            if (!searchScope.contains(vFile)) return null
            return coverageAnnotator.getClassCoverageInfo(element.fqName?.asString())
        }
        if (element !is KtFile) {
            return null
        }
        LOG.debug("Retrieving coverage for " + element.name)

        val qualifiedNames = collectGeneratedClassQualifiedNames(element)
        return if (qualifiedNames.isNullOrEmpty()) null else totalCoverageForQualifiedNames(coverageAnnotator, qualifiedNames)
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
            if (runReadAction { fileIndex.isInLibraryClasses(srcFile.getVirtualFile()) || fileIndex.isInLibrarySource(srcFile.getVirtualFile()) }) {
                return false
            }
            val existingClassFiles = getClassesGeneratedFromFile(srcFile)
            existingClassFiles.mapTo(classFiles) { File(it.path) }
            return existingClassFiles.isNotEmpty()
        }
        return false
    }

    override fun generateBriefReport(
        editor: Editor?,
        file: PsiFile?,
        lineNumber: Int,
        startOffset: Int,
        endOffset: Int,
        lineData: LineData?
    ): String? {
        if (file !is KtFile || lineData == null) return super.generateBriefReport(editor, file, lineNumber, startOffset, endOffset, lineData)
        val range = TextRange.create(startOffset, endOffset)
        val conditions = getConditions(file, range)
        val switches = getSwitches(file, range)
        return JavaCoverageEngine.createBriefReport(lineData, conditions, switches)
    }

    override fun getModuleWithOutput(module: Module): Module? = findJvmModule(module)

    companion object {
        private val LOG = Logger.getInstance(KotlinCoverageExtension::class.java)

        private fun collectGeneratedClassQualifiedNames(file: KtFile): List<String>? =
            findOutputRoots(file)?.flatMap { collectGeneratedClassQualifiedNames(it, file) ?: emptyList() }

        fun collectGeneratedClassQualifiedNames(outputRoot: VirtualFile?, file: KtFile): List<String>? {
            val existingClassFiles = getClassesGeneratedFromFile(outputRoot, file)
            if (existingClassFiles.isEmpty()) {
                return null
            }
            LOG.debug("ClassFiles: [${existingClassFiles.joinToString { it.name }}]")
            return existingClassFiles.map {
                val relativePath = VfsUtilCore.getRelativePath(it, outputRoot!!)!!
                relativePath.removeSuffix(".class").replace("/", ".")
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


        private fun getClassesGeneratedFromFile(file: KtFile): List<VirtualFile> =
            findOutputRoots(file)?.flatMap { runReadAction { getClassesGeneratedFromFile(it, file) } } ?: emptyList()

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
            val module = runReadAction { ModuleUtilCore.findModuleForPsiElement(file) } ?: return null
            val fileIndex = ProjectRootManager.getInstance(file.project).fileIndex
            val inTests = runReadAction { fileIndex.getKotlinSourceRootType(file.virtualFile) } == TestSourceKotlinRootType
            val manager = CoverageDataManager.getInstance(file.project)
            JavaCoverageClassesEnumerator.getRoots(manager, module, inTests).let {
                if (it.isNotEmpty()) return it
            }
            return findJvmModule(module)?.let { candidate ->  JavaCoverageClassesEnumerator.getRoots(manager, candidate, inTests) }
        }

        private fun findJvmModule(module: Module): Module? {
            if (module.isMultiPlatformModule) {
                return module.implementingModules.firstOrNull { it.platform.isJvm() }
            }
            return null
        }

        private fun collectClassFilePrefixes(file: KtFile): Collection<String> {
            val result = file.children.filterIsInstance<KtClassOrObject>().mapNotNull { it.name }
            val packagePartFqName = JvmFileClassUtil.getFileClassInfoNoResolve(file).fileClassFqName
            return result.union(arrayListOf(packagePartFqName.shortName().asString()))
        }
    }
}
