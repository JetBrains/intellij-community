// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.testFramework.common.BazelTestUtil.getFileFromBazelRuntime
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.createParentDirectories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.bazelEnvironment.BazelLabel
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.extractFile
import org.jetbrains.jps.model.serialization.JpsMavenSettings
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.TargetSupportException
import org.jetbrains.tools.model.updater.KotlinTestsDependenciesUtil
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.reflect.KVisibility

@OptIn(ExperimentalPathApi::class)
object TestKotlinArtifacts {
    private const val KOTLIN_DEPS_REPO = "kotlin_test_deps"

    private val communityRoot by lazy {
        BuildDependenciesCommunityRoot(Path.of(PathManager.getCommunityHomePath()))
    }

    private val LOG = logger<TestKotlinArtifacts>()

    private fun areFilesEquals(source: Path, destination: Path): Boolean {
        if (!destination.exists()) {
            return false
        }
        if (source.fileSize() != destination.fileSize()) {
            return false
        }
        return source.readBytes().contentEquals(destination.readBytes())
    }

    /**
     * Returns a directory which will be under KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX
     * to satisfy FromKotlinDistForIdeByNameFallbackBundledFirCompilerPluginProvider
     */
    internal val kotlinDistForIdeUnpacked: Path by lazy {
        val artifact = getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-dist-for-ide.jar")

        val targetDirectory = KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX.toPath().resolve(artifact.nameWithoutExtension)
        runBlocking(Dispatchers.IO) {
            extractFile(artifact, targetDirectory, communityRoot)
        }

        targetDirectory
    }

    @JvmStatic
    val kotlinDistForIdeUnpackedForIncrementalCompilation: Path by lazy {
        val artifact = getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-dist-for-ide-increment-compilation.jar")
        val targetDirectory = KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX.toPath().resolve(artifact.nameWithoutExtension)
        runBlocking(Dispatchers.IO) {
            extractFile(artifact, targetDirectory, communityRoot)
        }

        targetDirectory
    }

    internal val jpsPluginClasspath: List<Path> by lazy {
        listOf(getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-jps-plugin-classpath.jar"))
    }

    private fun findDownloadFile(label: BazelLabel): KotlinTestsDependenciesUtil.DownloadFile {
        if (label.repo != KOTLIN_DEPS_REPO) {
            error("Only $KOTLIN_DEPS_REPO repo is supported, but got '${label.repo}' from: ${label.asLabel}")
        }
        return KotlinTestsDependenciesUtil.kotlinTestDependenciesHttpFiles.find { it.fileName == label.target }
            ?: error("Unable to find URL for '${label.asLabel}'")
    }

    private fun downloadFile(label: BazelLabel): Path {
        // in other modules KotlinTestsDependenciesUtil.downloadFile(label = label) may be used
        // but here is some more complicated logic
        val downloadFile = findDownloadFile(label)
        val labelUrl = URI(downloadFile.url)
        // Kotlin plugin team use special workflow for simultaneous development Kotlin compiler and IDEA plugin.
        // In this scenario maven libraries with complier artifacts are replaced on locally deployed jars in the Kotlin repo folder.
        // To support test in this scenario, we need special handling urls with a custom hardcoded version.
        // See docs about this process:
        // https://github.com/JetBrains/intellij-community/blob/master/plugins/kotlin/docs/cooperative-development/environment-setup.md
        val fileInCache = if (labelUrl.toString().contains("255-dev-255")) {
            val relativePath = labelUrl.path.substringAfter("/intellij-dependencies/")
            val file = KotlinTestsDependenciesUtil.kotlinCompilerSnapshotPath.resolve(relativePath)
            if (!Files.exists(file)) {
                error("File $file doesn't exist in cooperative repo ${KotlinTestsDependenciesUtil.kotlinCompilerSnapshotPath}. " +
                              "Please run 'Kotlin Coop: Publish Compiler JARs' run configuration in IntelliJ.")
            }
            file
        } else {
            val relativePath = labelUrl.path.substringAfter("/intellij-dependencies/")
            val fileInM2Folder = Path.of(JpsMavenSettings.getMavenRepositoryPath()).resolve(relativePath)
            if (Files.exists(fileInM2Folder)) {
                val onDiskSha256 = DigestUtil.sha256Hex(fileInM2Folder.readBytes())
                if (onDiskSha256 != downloadFile.sha256) {
                    error("SHA-256 checksum mismatch for '${label.asLabel}': expected '${downloadFile.sha256}', got '$onDiskSha256' at $fileInM2Folder")
                }
                return fileInM2Folder
            }
            val fileInCache = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, labelUrl)
            val onDiskSha256 = DigestUtil.sha256Hex(fileInCache.readBytes())
            if (onDiskSha256 != downloadFile.sha256) {
                error("SHA-256 checksum mismatch for '${label.asLabel}': expected '${downloadFile.sha256}', got '$onDiskSha256' at $fileInCache")
            }
            fileInCache
        }
        val target = communityRoot.communityRoot.resolve("out/kotlin-from-sources-deps/${label.target}")
        if (!Files.exists(target.parent)) {
            target.createParentDirectories()
        }
        if (!areFilesEquals(fileInCache, target)) {
            Files.copy(fileInCache, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }

    @JvmStatic
    fun loadDependency(
        label: String,
        type: OrderRootType
    ): OrderRoot {
        val libFile = getKotlinDepsByLabel(label)

        val manager = VirtualFileManager.getInstance()
        val url: String = VfsUtil.getUrlForLibraryRoot(libFile)
        val file = manager.refreshAndFindFileByUrl(url) ?: error("Cannot find $url")

        return OrderRoot(file, type)
    }

    // @kotlin_test_deps//:kotlin-stdlib.jar
    fun getKotlinDepsByLabel(bazelLabel: String): Path {
        val label = BazelLabel.fromString(bazelLabel)
        return getKotlinDepsByLabel(label = label)
    }

    private fun getKotlinDepsByLabel(label: BazelLabel): Path {
        // Bazel will download and provide all dependencies externally.
        // We should manually download dependencies when test are running not from Bazel.
        val dependency = if (BazelTestUtil.isUnderBazelTest) {
            getFileFromBazelRuntime(label).also {
                LOG.info("Found dependency in Bazel runtime ${label.asLabel} at '$it'")
            }
        } else {
            downloadFile(label).also {
                LOG.info("Found dependency download dependency ${label.asLabel} at '$it'")
            }
        }

        // some tests for code require that files should be under $COMMUNITY_HOME_PATH/out
        val target = Path.of(PathManager.getCommunityHomePath())
            .resolve("out")
            .resolve("kotlin-from-sources-deps")
            .resolve(label.repo)
            .resolve(label.packageName)
            .resolve(label.target)

        // we could have a file from some previous launch, but with different content
        // it is a valid scenario when the file is JAR without a version and url changed
        // we have to verify content
        if (target.exists() && areFilesEquals(dependency, target)) {
            return target
        }
        target.createParentDirectories()
        val tempFile = Files.createTempFile(target.parent, target.name, ".tmp")
        try {
            dependency.copyToRecursively(tempFile, overwrite = true, followLinks = true)
            // in the case of parallel access target will be overwritten by one of the threads
            tempFile.moveTo(target, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            tempFile.deleteIfExists()
        }
        LOG.info("Dependency ${label.asLabel} resolved to '$target'")
        return target
    }

    @JvmStatic
    val kotlinAnnotationsJvm: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-annotations-jvm.jar") }
    @JvmStatic
    val kotlinCompiler: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-compiler.jar") }
    @JvmStatic
    val kotlinDaemon: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-daemon.jar") }
    @JvmStatic
    val kotlinReflect: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-reflect.jar") }
    @JvmStatic
    val kotlinReflectSources: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-reflect-sources.jar") }
    @JvmStatic
    val kotlinScriptRuntime: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-script-runtime.jar") }
    @JvmStatic
    val kotlinScriptingCommon: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-scripting-common.jar") }
    @JvmStatic
    val kotlinScriptingCompiler: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-scripting-compiler.jar") }
    @JvmStatic
    val kotlinScriptingCompilerImpl: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-scripting-compiler-impl.jar") }
    @JvmStatic
    val kotlinScriptingJvm: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-scripting-jvm.jar") }
    @JvmStatic
    val kotlinStdlib: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib.jar") }
    @JvmStatic
    val kotlinStdlib170: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-1.7.0.jar") }
    @JvmStatic
    val kotlinStdlib170Sources: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-1.7.0-sources.jar") }
    @JvmStatic
    val kotlinStdlibCommon170Sources: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-common-1.7.0-sources.jar") }

    @JvmStatic
    val kotlinStdlibCommon: Path by lazy {
        val artifact = getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-all.jar")
        val ourDir = Path.of(PathManager.getCommunityHomePath()).resolve("out")
        val expandedDir = ourDir.resolve("kotlin-stdlib-all")
        val target = ourDir.resolve("kotlin-stdlib-common.klib")
        runBlocking {
            extractFile(artifact, expandedDir, communityRoot)
        }
        val unpackedCommonMain = expandedDir.resolve("commonMain")
        unpackedCommonMain.compressDirectoryToZip(target)
        return@lazy target
    }
    @JvmStatic
    val kotlinStdlibCommonSources: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-common-sources.jar") }
    @JvmStatic
    val kotlinStdlibJdk7: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-jdk7.jar") }
    @JvmStatic
    val kotlinStdlibJdk7Sources: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-jdk7-sources.jar") }
    @JvmStatic
    val kotlinStdlibJdk8: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-jdk8.jar") }
    @JvmStatic
    val kotlinStdlibJdk8Sources: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-jdk8-sources.jar") }
    @JvmStatic
    val kotlinStdlibJs: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-js.klib") }

    // The latest published kotlin-stdlib-js with both .knm and .kjsm roots
    @JvmStatic
    val kotlinStdlibJsLegacyJar: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-js-1.9.22.jar") }
    @JvmStatic
    val kotlinDomApiCompat: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-dom-api-compat.klib") }
    @JvmStatic
    val kotlinStdlibWasmJs: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-wasm-js.klib") }
    @JvmStatic
    val kotlinStdlibWasmWasi: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-wasm-wasi.klib") }
    @JvmStatic
    val kotlinStdlibSources: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-sources.jar") }
    @JvmStatic
    val kotlinStdlibLegacy1922: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-1.9.22.jar") }
    @JvmStatic
    val kotlinStdLibProjectWizardDefault: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-project-wizard-default.jar") }

    // In 1.x, `kotlin-stdlib-common` still contained `.kotlin_metadata` files. 2.x versions of the library contain `.knm` files, since it's
    // now a klib.
    @JvmStatic
    val kotlinStdlibCommonLegacy1922: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-common.jar") }
    @JvmStatic
    val kotlinTest: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-test.jar") }
    @JvmStatic
    val kotlinTestJs: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-test-js.klib") }
    @JvmStatic
    val kotlinTestJunit: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-test-junit.jar") }
    @JvmStatic
    val parcelizeRuntime: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:parcelize-compiler-plugin-for-ide.jar") }
    @JvmStatic
    val composeCompilerPluginForIde: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:compose-compiler-plugin-for-ide.jar") }

    @JvmStatic
    val kotlinStdlibJdk8_2_1_21: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-jdk8-2.1.21.jar") }
    @JvmStatic
    val kotlinStdlib_2_1_21: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-2.1.21.jar") }
    @JvmStatic
    val annotations13: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:annotations-13.0.jar") }
    @JvmStatic
    val kotlinxCoroutinesCore_1_10_2: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlinx-coroutines-core-1.10.2.jar") }
    @JvmStatic
    val kotlinxCoroutinesCoreJvm_1_10_2: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.10.2.jar") }

    @JvmStatic
    val kotlinJvmDebuggerTestData: Path by lazy { getKotlinDepsByLabel("@community//plugins/kotlin/jvm-debugger/test:testData") }
    @JvmStatic
    val kotlinIdeaTestData: Path by lazy { getKotlinDepsByLabel("@community//plugins/kotlin/idea/tests:testData") }

    @Suppress("NO_REFLECTION_IN_CLASS_PATH")
    @JvmStatic
    fun main(args: Array<String>) {
        // prints all available test artifacts for DEBUGGING ONLY
        for (member in TestKotlinArtifacts::class.members) {
            if (member.name == "kotlinJvmDebuggerTestData") continue
            if (member.visibility != KVisibility.PUBLIC) continue
            if (member.parameters.size != 1) continue
            if (member.returnType.classifier == Path::class) {
                try {
                    println("${member.name} = ${member.call(TestKotlinArtifacts)}")
                } catch (e: Exception) {
                    error("cannot call member '${member.name}': $e")
                }
            }
        }
        println("Done")
    }

    @JvmStatic
    val trove4j: Path by lazy {
        PathManager.getJarForClass(TestKotlinArtifacts::class.java.classLoader.loadClass("gnu.trove.THashMap"))!!
    }
    @JvmStatic
    val jetbrainsAnnotationsJava5: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:annotations-java5-24.0.0.jar") }
    @JvmStatic
    val jetbrainsAnnotations: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:annotations.jar") }
    @JvmStatic
    val jsr305: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:jsr305.jar") }
    @JvmStatic
    val junit3: Path by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:junit.jar") }
    @JvmStatic
    val kotlinxCoroutines: Path by lazy {
        PathManager.getJarForClass(TestKotlinArtifacts::class.java.classLoader.loadClass("kotlinx.coroutines.CoroutineScope"))!!
    }
    @JvmStatic
    val coroutineContext: Path by lazy {
        PathManager.getJarForClass(TestKotlinArtifacts::class.java.classLoader.loadClass("kotlin.coroutines.CoroutineContext"))!!
    }

    /**
     * @throws TargetSupportException on access from an inappropriate host.
     * See KT-36871, KTIJ-28066.
     */
    @JvmStatic
    val kotlinStdlibNative: Path by lazy { getNativeLib(library = "klib/common/stdlib") }

    @JvmStatic
    val compilerTestDataDir: Path by lazy {
        val artifact = getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-compiler-testdata-for-ide.jar")
        val targetDir = Path.of(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlinc-testdata-2")
        runBlocking {
            extractFile(artifact, targetDir, communityRoot)
        }
        return@lazy targetDir
    }

    @JvmStatic
    fun compilerTestData(compilerTestDataPath: String): String {
        return compilerTestDataDir.resolve(compilerTestDataPath).pathString
    }

    @JvmStatic
    val jpsPluginTestDataDir: Path by lazy {
        val artifact = getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-jps-plugin-testdata-for-ide.jar")
        val targetDir = Path.of(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlinc-jps-testdata")
        runBlocking {
            extractFile(artifact, targetDir, communityRoot)
        }
        return@lazy targetDir
    }

    @JvmStatic
    val jsIrRuntimeDir: Path by lazy { return@lazy getKotlinDepsByLabel("@kotlin_test_deps//:js-ir-runtime-for-ide.klib") }

    @JvmStatic
    fun jpsPluginTestData(jpsTestDataPath: String): Path {
        return jpsPluginTestDataDir.resolve(jpsTestDataPath)
    }

    @Throws(TargetSupportException::class)
    fun getNativeLib(
        version: String = KotlinNativeVersion.resolvedKotlinNativeVersion,
        platform: String = HostManager.platformName(),
        library: String
    ): Path {
        val prebuiltDir = getNativePrebuilt(version = version, platform = platform, communityRoot = communityRoot)
        val libFile = prebuiltDir.resolve(library)
        check(libFile.exists()) {
            "Library $library not found in prebuilt directory: ${prebuiltDir}. Available files:\n" +
                    prebuiltDir.walk().joinToString("\n") { it.toString() }
        }
        return libFile
    }

    private fun Path.compressDirectoryToZip(targetZipFile: Path) {
        targetZipFile.createParentDirectories()

        val sourceFolder = this

        ZipOutputStream(targetZipFile.outputStream().buffered()).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)
            sourceFolder
                .walk()
                .filter { file -> !file.isDirectory() }
                .forEach { file ->
                    val entry = ZipEntry(file.relativeTo(sourceFolder).invariantSeparatorsPathString)
                    zip.putNextEntry(entry)
                    file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }
}
