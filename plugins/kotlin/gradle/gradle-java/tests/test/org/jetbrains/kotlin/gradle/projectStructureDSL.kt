// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.util.JpsPathUtil
import org.jetbrains.kotlin.config.ExternalSystemTestRunTask
import org.jetbrains.kotlin.idea.base.facet.externalSystemTestRunTasks
import org.jetbrains.kotlin.idea.base.facet.isHMPPEnabled
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.gradleJava.configuration.kotlinGradleProjectDataOrFail
import org.jetbrains.kotlin.idea.gradleTooling.KotlinImportingDiagnostic
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.utils.addToStdlib.filterIsInstanceWithChecker
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.io.File
import kotlin.test.fail

class MessageCollector {
    private val builder = StringBuilder()

    fun report(message: String) {
        builder.append(message).append("\n\n")
    }

    fun check() {
        val message = builder.toString()
        if (message.isNotEmpty()) {
            fail("\n\n" + message)
        }
    }
}

class ProjectInfo(
    project: Project,
    internal val projectPath: String,
    internal val exhaustiveModuleList: Boolean,
    internal val exhaustiveSourceSourceRootList: Boolean,
    internal val exhaustiveDependencyList: Boolean,
    internal val exhaustiveTestsList: Boolean
) {
    val messageCollector = MessageCollector()

    private val moduleManager = ModuleManager.getInstance(project)
    private val expectedModuleNames = HashSet<String>()
    private var allModulesAsserter: (ModuleInfo.() -> Unit)? = null
    private val moduleInfos = moduleManager.modules.associateWith { module -> ModuleInfo(module, this) }

    @Deprecated("Use .forEachModule instead. This method is error prone and has to be called before 'module(..)' in order to run")
    fun allModules(body: ModuleInfo.() -> Unit) {
        assert(allModulesAsserter == null)
        allModulesAsserter = body
    }

    fun forEachModule(body: ModuleInfo.() -> Unit) {
        moduleInfos.values.forEach { moduleInfo ->
            moduleInfo.run(body)
        }
    }

    fun module(name: String, isOptional: Boolean = false, body: ModuleInfo.() -> Unit = {}) {
        val module = moduleManager.findModuleByName(name)
        if (module == null) {
            if (!isOptional) {
                messageCollector.report("No module found: '$name' in ${moduleManager.modules.map { it.name }}")
            }
            return
        }

        val moduleInfo = moduleInfos.getValue(module)
        allModulesAsserter?.let { moduleInfo.it() }
        moduleInfo.run(body)
        expectedModuleNames += name
    }

    fun run(body: ProjectInfo.() -> Unit = {}) {
        body()

        if (exhaustiveModuleList) {
            val actualNames = moduleManager.modules.map { it.name }.sorted()
            val expectedNames = expectedModuleNames.sorted()
            if (actualNames != expectedNames) {
                messageCollector.report("Expected module list $expectedNames doesn't match the actual one: $actualNames")
            }
        }

        messageCollector.check()
    }
}

class ModuleInfo(val module: Module, val projectInfo: ProjectInfo) {
    private val rootModel = module.rootManager
    private val expectedDependencyNames = HashSet<String>()
    private val expectedDependencies = HashSet<OrderEntry>()
    private val expectedSourceRoots = HashSet<String>()
    private val expectedExternalSystemTestTasks = ArrayList<ExternalSystemTestRunTask>()
    private val assertions = mutableListOf<(ModuleInfo) -> Unit>()
    private var mustHaveSdk: Boolean = true

    private val sourceFolderByPath by lazy {
        rootModel.contentEntries.asSequence()
            .flatMap { it.sourceFolders.asSequence() }
            .mapNotNull {
                val path = it.file?.path ?: return@mapNotNull null
                FileUtil.getRelativePath(projectInfo.projectPath, path, '/')!! to it
            }
            .toMap()
    }

    fun report(text: String) {
        projectInfo.messageCollector.report("Module '${module.name}': $text")
    }

    private fun checkReport(subject: String, expected: Any?, actual: Any?) {
        if (expected != actual) {
            report(
                "$subject differs:\n" +
                        "expected $expected\n" +
                        "actual:  $actual"
            )
        }
    }

    fun externalSystemTestTask(taskName: String, projectId: String, targetName: String, platform: KotlinPlatform) {
        expectedExternalSystemTestTasks.add(ExternalSystemTestRunTask(taskName, projectId, targetName, platform.id))
    }

    fun languageVersion(expectedVersion: String) {
        val actualVersion = module.languageVersionSettings.languageVersion.versionString
        checkReport("Language version", expectedVersion, actualVersion)
    }

    fun isHMPP(expectedValue: Boolean) {
        checkReport("isHMPP", expectedValue, module.isHMPPEnabled)
    }

    fun targetPlatform(vararg platforms: TargetPlatform) {
        val expected = platforms.flatMap { it.componentPlatforms }.toSet()
        val actual = module.platform?.componentPlatforms

        if (actual == null) {
            report("Actual target platform is null")
            return
        }

        val notFound = expected.subtract(actual)
        if (notFound.isNotEmpty()) {
            report("These target platforms were not found: " + notFound.joinToString())
        }

        val unexpected = actual.subtract(expected)
        if (unexpected.isNotEmpty()) {
            report("Unexpected target platforms found: " + unexpected.joinToString())
        }
    }

    fun apiVersion(expectedVersion: String) {
        val actualVersion = module.languageVersionSettings.apiVersion.versionString
        checkReport("API version", expectedVersion, actualVersion)
    }

    fun platform(expectedPlatform: TargetPlatform) {
        val actualPlatform = module.platform
        checkReport("Platform", expectedPlatform, actualPlatform)
    }

    fun additionalArguments(arguments: String?) {
        val actualArguments = KotlinFacet.get(module)?.configuration?.settings?.compilerSettings?.additionalArguments
        checkReport("Additional arguments", arguments, actualArguments)
    }

    fun kotlinFacetSettingCreated() {
        val facet = KotlinFacet.get(module)?.configuration?.settings
        if (facet == null) report("KotlinFacetSettings does not exist")
    }

    fun noLibraryDependency(libraryNameRegex: Regex) {
        val libraryEntries = rootModel.orderEntries.filterIsInstance<LibraryOrderEntry>()
            .filter { it.libraryName?.matches(libraryNameRegex) == true }

        if (libraryEntries.isNotEmpty()) {
            report(
                "Expected no dependencies for $libraryNameRegex, but found:\n" +
                        libraryEntries.joinToString(prefix = "[", postfix = "]", separator = ",") { it.presentableName }
            )
        }
    }

    fun noLibraryDependency(@Language("regex") libraryNameRegex: String) {
        noLibraryDependency(Regex(libraryNameRegex))
    }

    fun libraryDependency(libraryName: String, scope: DependencyScope, isOptional: Boolean = false) {
        libraryDependency(Regex.fromLiteral(libraryName), scope, allowMultiple = false, isOptional)
    }

    fun libraryDependency(libraryName: Regex, scope: DependencyScope, allowMultiple: Boolean = false, isOptional: Boolean = false) {
        val libraryEntries = rootModel.orderEntries.filterIsInstance<LibraryOrderEntry>()
            .filter { it.libraryName?.matches(libraryName) == true }

        if (!allowMultiple && libraryEntries.size > 1) {
            report("Multiple root entries for library $libraryName")
        }

        if (!isOptional && libraryEntries.isEmpty()) {
            val candidate = rootModel.orderEntries
                .filterIsInstance<LibraryOrderEntry>()
                .sortedWith(Comparator { o1, o2 ->
                    val o1len = o1?.libraryName?.commonPrefixWith(libraryName.toString())?.length ?: 0
                    val o2len = o2?.libraryName?.commonPrefixWith(libraryName.toString())?.length ?: 0
                    o2len - o1len
                }).firstOrNull()

            val candidateName = candidate?.libraryName
            report("Expected library dependency $libraryName, found nothing. Most probably candidate: $candidateName")
        }

        libraryEntries.forEach { library ->
            checkLibrary(library, scope)
        }
    }

    fun libraryDependencyByUrl(classesUrl: String, scope: DependencyScope) {
        libraryDependencyByUrl(Regex.fromLiteral(classesUrl), scope)
    }

    fun libraryDependencyByUrl(classesUrl: Regex, scope: DependencyScope) {
        val libraryEntries = rootModel.orderEntries.filterIsInstance<LibraryOrderEntry>().filter { entry ->
            entry.library?.getUrls(OrderRootType.CLASSES)?.any { it.matches(classesUrl) } ?: false
        }

        if (libraryEntries.size > 1) {
            report("Multiple entries for library $classesUrl")
        }

        if (libraryEntries.isEmpty()) {
            report("No library dependency found for $classesUrl")
        }

        checkLibrary(libraryEntries.firstOrNull() ?: return, scope)
    }

    private fun checkLibrary(libraryEntry: LibraryOrderEntry, scope: DependencyScope) {
        checkDependencyScope(libraryEntry, scope)
        expectedDependencies += libraryEntry
        expectedDependencyNames += libraryEntry.debugText
    }

    fun moduleDependency(
        moduleName: String, scope: DependencyScope,
        productionOnTest: Boolean? = null, allowMultiple: Boolean = false, isOptional: Boolean = false
    ) {
        val moduleEntries = rootModel.orderEntries.asList()
            .filterIsInstanceWithChecker<ModuleOrderEntry> { it.moduleName == moduleName && it.scope == scope }

        // In normal conditions, 'allowMultiple' should always be 'false'. In reality, however, a lot of tests fails because of it.
        if (!allowMultiple && moduleEntries.size > 1) {
            val allEntries = rootModel.orderEntries.filterIsInstance<ModuleOrderEntry>().joinToString { it.debugText }
            report("Multiple order entries found for module $moduleName: $allEntries")
            return
        }

        if (moduleEntries.isEmpty() && !isOptional) {
            val allModules = rootModel.orderEntries.filterIsInstance<ModuleOrderEntry>().joinToString { it.debugText }
            report("Module dependency ${moduleName} (${scope.displayName}) not found. All module dependencies: $allModules")
        }

        moduleEntries.forEach { moduleEntry ->
            checkDependencyScope(moduleEntry, scope)
            checkProductionOnTest(moduleEntry, productionOnTest)
            expectedDependencies += moduleEntry
            expectedDependencyNames += moduleEntry.debugText
        }
    }

    private val ANY_PACKAGE_PREFIX = "any_package_prefix"

    fun sourceFolder(pathInProject: String, rootType: JpsModuleSourceRootType<*>, packagePrefix: String? = ANY_PACKAGE_PREFIX) {
        val sourceFolder = sourceFolderByPath[pathInProject]
        if (sourceFolder == null) {
            report("No source root found: '$pathInProject' among $sourceFolderByPath")
            return
        }

        if (packagePrefix != ANY_PACKAGE_PREFIX && sourceFolder.packagePrefix != packagePrefix) {
            report("Source root '$pathInProject': Expected package prefix $packagePrefix, got: ${sourceFolder.packagePrefix}")
        }

        expectedSourceRoots += pathInProject
        val actualRootType = sourceFolder.rootType
        if (actualRootType != rootType) {
            report("Source root '$pathInProject': Expected root type $rootType, got: $actualRootType")
            return
        }
    }

    fun inheritProjectOutput() {
        val isInherited = CompilerModuleExtension.getInstance(module)?.isCompilerOutputPathInherited ?: true
        if (!isInherited) {
            report("Project output is not inherited")
        }
    }

    fun outputPath(pathInProject: String, isProduction: Boolean) {
        val compilerModuleExtension = CompilerModuleExtension.getInstance(module)
        val url = if (isProduction) compilerModuleExtension?.compilerOutputUrl else compilerModuleExtension?.compilerOutputUrlForTests
        val actualPathInProject = url?.let {
            FileUtil.getRelativePath(
                projectInfo.projectPath,
                JpsPathUtil.urlToPath(
                    it
                ),
                '/'
            )
        }

        checkReport("Output path", pathInProject, actualPathInProject)
    }

    fun noSdk() {
        mustHaveSdk = false
    }

    inline fun <reified T : KotlinImportingDiagnostic> assertDiagnosticsCount(count: Int) {
        val moduleNode = GradleUtil.findGradleModuleData(module)
        val diagnostics = moduleNode!!.kotlinGradleProjectDataOrFail.kotlinImportingDiagnosticsContainer!!
        val typedDiagnostics = diagnostics.filterIsInstance<T>()
        if (typedDiagnostics.size != count) {
            projectInfo.messageCollector.report(
                "Expected number of ${T::class.java.simpleName} diagnostics $count doesn't match the actual one: ${typedDiagnostics.size}"
            )
        }
    }


    fun assertExhaustiveDependencyList() {
        assertions += {
            val expectedDependencyNames = expectedDependencyNames.sorted()
            val actualDependencyNames = rootModel
                .orderEntries.asList()
                .filterIsInstanceWithChecker<ExportableOrderEntry> { it is ModuleOrderEntry || it is LibraryOrderEntry }
                .map { it.debugText }
                .sorted()
                .distinct()
                // increasing readability of log outputs
                .sortedBy { if (it in expectedDependencyNames) 0 else 1 }

            checkReport("Dependency list", expectedDependencyNames, actualDependencyNames)
        }
    }

    fun assertExhaustiveModuleDependencyList() {
        assertions += {
            val expectedModuleDependencies = expectedDependencies.filterIsInstance<ModuleOrderEntry>()
                .map { it.debugText }.sorted().distinct()

            val actualModuleDependencies = rootModel.orderEntries.filterIsInstance<ModuleOrderEntry>()
                .map { it.debugText }.sorted().distinct()
                // increasing readability of log outputs
                .sortedBy { if (it in expectedModuleDependencies) 0 else 1 }

            if (actualModuleDependencies != expectedModuleDependencies) {
                report(
                    "Bad Module dependency list for ${module.name}\n" +
                            "Expected: $expectedModuleDependencies\n" +
                            "Actual:   $actualModuleDependencies"
                )
            }
        }
    }

    fun assertExhaustiveLibraryDependencyList() {
        assertions += {
            val expectedLibraryDependencies = expectedDependencies.filterIsInstance<LibraryOrderEntry>()
                .map { it.debugText }.sorted().distinct()

            val actualLibraryDependencies = rootModel.orderEntries.filterIsInstance<LibraryOrderEntry>()
                .map { it.debugText }.sorted().distinct()
                // increasing readability of log outputs
                .sortedBy { if (it in expectedLibraryDependencies) 0 else 1 }

            if (actualLibraryDependencies != expectedLibraryDependencies) {
                report(
                    "Bad Library dependency list for ${module.name}\n" +
                            "Expected: $expectedLibraryDependencies\n" +
                            "Actual:   $actualLibraryDependencies"
                )
            }
        }
    }

    fun assertExhaustiveTestsList() {
        assertions += {
            val actualTasks = module.externalSystemTestRunTasks()

            val containsAllTasks = actualTasks.containsAll(expectedExternalSystemTestTasks)
            val containsSameTasks = actualTasks == expectedExternalSystemTestTasks

            if (!containsAllTasks || !containsSameTasks) {
                report("Expected tests list $expectedExternalSystemTestTasks, got: $actualTasks")
            }
        }
    }

    fun assertExhaustiveSourceRootList() {
        assertions += {
            val actualSourceRoots = sourceFolderByPath.keys.sorted()
            val expectedSourceRoots = expectedSourceRoots.sorted()
            if (actualSourceRoots != expectedSourceRoots) {
                report("Expected source root list $expectedSourceRoots, got: $actualSourceRoots")
            }
        }
    }

    fun assertNoDependencyInBuildClasses() {
        val librariesOnly = OrderEnumerator.orderEntries(module).recursively().librariesOnly()
        val rootUrls = librariesOnly.classes().urls + librariesOnly.sources().urls
        val dependenciesInBuildDirectory = rootUrls
            .map { url -> PathUtil.getLocalPath(VfsUtilCore.urlToPath(url)) }
            .filter { path -> "/build/classes/" in path }

        if (dependenciesInBuildDirectory.isNotEmpty()) {
            report("References dependency in build directory:\n${dependenciesInBuildDirectory.joinToString("\n")}")
        }
    }

    fun run(body: ModuleInfo.() -> Unit = {}) {
        body()
        assertions.forEach { it.invoke(this) }
        assertions.clear()
        if (mustHaveSdk && rootModel.sdk == null) {
            report("No SDK defined")
        }
    }

    private fun checkDependencyScope(library: ExportableOrderEntry, expectedScope: DependencyScope) {
        checkReport("Dependency scope", expectedScope, library.scope)
    }

    private fun checkProductionOnTest(library: ExportableOrderEntry, productionOnTest: Boolean?) {
        if (productionOnTest == null) return
        val actualFlag = (library as? ModuleOrderEntry)?.isProductionOnTestDependency
        if (actualFlag == null) {
            report("Dependency '${library.presentableName}' has no 'productionOnTest' property")
        } else {
            if (actualFlag != productionOnTest) {
                report("Dependency '${library.presentableName}': expected productionOnTest '$productionOnTest', got '$actualFlag'")
            }
        }
    }

    init {
        if (projectInfo.exhaustiveDependencyList) {
            assertExhaustiveDependencyList()
        }
        if (projectInfo.exhaustiveTestsList) {
            assertExhaustiveTestsList()
        }
        if (projectInfo.exhaustiveSourceSourceRootList) {
            assertExhaustiveSourceRootList()
        }
    }
}

fun checkProjectStructure(
    project: Project,
    projectPath: String,
    exhaustiveModuleList: Boolean = false,
    exhaustiveSourceSourceRootList: Boolean = false,
    exhaustiveDependencyList: Boolean = false,
    exhaustiveTestsList: Boolean = false,
    body: ProjectInfo.() -> Unit = {}
) {
    ProjectInfo(
        project,
        projectPath,
        exhaustiveModuleList,
        exhaustiveSourceSourceRootList,
        exhaustiveDependencyList,
        exhaustiveTestsList
    ).run(body)
}

private val ExportableOrderEntry.debugText: String
    get() = "$presentableName (${scope.displayName})"

private fun VirtualFile.toIoFile(): File = VfsUtil.virtualToIoFile(this)
