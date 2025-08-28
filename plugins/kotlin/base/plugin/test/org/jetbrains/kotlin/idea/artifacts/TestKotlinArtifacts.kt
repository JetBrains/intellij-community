// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin.artifacts

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.testFramework.common.BazelTestUtil.getFileFromBazelRuntime
import com.intellij.testFramework.common.bazel.BazelLabel
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.createParentDirectories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.extractFile
import org.jetbrains.jps.model.serialization.JpsMavenSettings
import org.jetbrains.kotlin.idea.artifacts.KotlinNativeHostSupportDetector
import org.jetbrains.kotlin.idea.artifacts.KotlinNativePrebuiltDownloader.downloadFile
import org.jetbrains.kotlin.idea.artifacts.KotlinNativePrebuiltDownloader.unpackPrebuildArchive
import org.jetbrains.kotlin.idea.artifacts.KotlinNativeVersion
import org.jetbrains.kotlin.idea.artifacts.NATIVE_PREBUILT_DEV_CDN_URL
import org.jetbrains.kotlin.idea.artifacts.NATIVE_PREBUILT_RELEASE_CDN_URL
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.TargetSupportException
import org.jetbrains.tools.model.updater.KotlinTestsDependenciesUtil
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*
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
            extractFile(artifact.toPath(), targetDirectory, communityRoot)
        }

        targetDirectory
    }

    @JvmStatic
    val kotlinDistForIdeUnpackedForIncrementalCompilation: Path by lazy {
        val artifact = getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-dist-for-ide-increment-compilation-2.2.0.jar")
        val targetDirectory = KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX.toPath().resolve(artifact.nameWithoutExtension)
        runBlocking(Dispatchers.IO) {
            extractFile(artifact.toPath(), targetDirectory, communityRoot)
        }

        targetDirectory
    }

    internal val jpsPluginClasspath: List<Path> by lazy {
        listOf(getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-jps-plugin-classpath.jar").toPath())
    }

    private fun findDownloadFile(label: BazelLabel): KotlinTestsDependenciesUtil.DownloadFile {
        if (label.repo != KOTLIN_DEPS_REPO) {
            error("Only $KOTLIN_DEPS_REPO repo is supported, but got '${label.repo}' from: ${label.asLabel}")
        }
        return KotlinTestsDependenciesUtil.kotlinTestDependenciesHttpFiles.find { it.fileName == label.target }
            ?: error("Unable to find URL for '${label.asLabel}'")
    }

    private val cooperativeRepoRoot = PathManager.getHomeDir().parent.resolve("build/repo")

    private fun downloadFile(label: BazelLabel): Path {
        val downloadFile = findDownloadFile(label)
        val labelUrl = URI(downloadFile.url)
        // Kotlin plugin team use special workflow for simultaneous development Kotlin compiler and IDEA plugin.
        // In this scenario maven libraries with complier artifacts are replaced on locally deployed jars in the Kotlin repo folder.
        // To support test in this scenario, we need special handling urls with a custom hardcoded version.
        // See docs about this process:
        // https://github.com/JetBrains/intellij-community/blob/master/plugins/kotlin/docs/cooperative-development/environment-setup.md
        val fileInCache = if (labelUrl.toString().contains("255-dev-255")) {
            val relativePath = labelUrl.path.substringAfter("/intellij-dependencies/")
            val file = cooperativeRepoRoot.resolve(relativePath)
            if (!Files.exists(file)) {
                error("File $file doesn't exist in cooperative repo $cooperativeRepoRoot. " +
                              "Please run 'Kotlin Coop: Publish compiler-for-ide JARs' run configuration in IntelliJ.")
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

    // @kotlin_test_deps//:kotlin-stdlib.jar
    fun getKotlinDepsByLabel(bazelLabel: String): File {
        val label = BazelLabel.fromString(bazelLabel)
        return getKotlinDepsByLabel(label = label)
    }

    private fun getKotlinDepsByLabel(label: BazelLabel): File {
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
            .resolve(label.target)

        // we could have a file from some previous launch, but with different content
        // it is a valid scenario when the file is JAR without a version and url changed
        // we have to verify content
        if (target.exists() && areFilesEquals(dependency, target)) {
            @Suppress("IO_FILE_USAGE")
            return target.toFile()
        }
        target.createParentDirectories()
        val tempFile = Files.createTempFile(target.parent, target.name, ".tmp")
        try {
            dependency.copyTo(tempFile, overwrite = true)
            // in the case of parallel access target will be overwritten by one of the threads
            tempFile.moveTo(target, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            tempFile.deleteIfExists()
        }
        LOG.info("Dependency ${label.asLabel} resolved to '$target'")
        @Suppress("IO_FILE_USAGE")
        return target.toFile()
    }

    @JvmStatic
    val kotlinAnnotationsJvm: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-annotations-jvm.jar") }
    @JvmStatic
    val kotlinCompiler: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-compiler.jar") }
    @JvmStatic
    val kotlinDaemon: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-daemon.jar") }
    @JvmStatic
    val kotlinReflect: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-reflect.jar") }
    @JvmStatic
    val kotlinReflectSources: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-reflect-sources.jar") }
    @JvmStatic
    val kotlinScriptRuntime: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-script-runtime.jar") }
    @JvmStatic
    val kotlinScriptingCommon: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-scripting-common.jar") }
    @JvmStatic
    val kotlinScriptingCompiler: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-scripting-compiler.jar") }
    @JvmStatic
    val kotlinScriptingCompilerImpl: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-scripting-compiler-impl.jar") }
    @JvmStatic
    val kotlinScriptingJvm: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-scripting-jvm.jar") }
    @JvmStatic
    val kotlinStdlib: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib.jar") }
    @JvmStatic
    val kotlinStdlib170: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-1.7.0.jar") }
    @JvmStatic
    val kotlinStdlib170Sources: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-1.7.0-sources.jar") }
    @JvmStatic
    val kotlinStdlibCommon170Sources: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-common-1.7.0-sources.jar") }

    @JvmStatic
    val kotlinStdlibCommon: File by lazy {
        val artifact = getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-all.jar")
        val ourDir = Path.of(PathManager.getCommunityHomePath()).resolve("out")
        val expandedDir = ourDir.resolve("kotlin-stdlib-all")
        val target = ourDir.resolve("kotlin-stdlib-common.klib")
        runBlocking {
            extractFile(artifact.toPath(), expandedDir, communityRoot)
        }
        val unpackedCommonMain = expandedDir.resolve("commonMain")
        @Suppress("IO_FILE_USAGE")
        unpackedCommonMain.toFile().compressDirectoryToZip(target.toFile())
        @Suppress("IO_FILE_USAGE")
        return@lazy target.toFile()
    }
    @JvmStatic
    val kotlinStdlibCommonSources: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-common-sources.jar") }
    @JvmStatic
    val kotlinStdlibJdk7: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-jdk7.jar") }
    @JvmStatic
    val kotlinStdlibJdk7Sources: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-jdk7-sources.jar") }
    @JvmStatic
    val kotlinStdlibJdk8: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-jdk8.jar") }
    @JvmStatic
    val kotlinStdlibJdk8Sources: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-jdk8-sources.jar") }
    @JvmStatic
    val kotlinStdlibJs: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-js.klib") }

    // The latest published kotlin-stdlib-js with both .knm and .kjsm roots
    @JvmStatic
    val kotlinStdlibJsLegacyJar: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-js-1.9.22.jar") }
    @JvmStatic
    val kotlinDomApiCompat: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-dom-api-compat.klib") }
    @JvmStatic
    val kotlinStdlibWasmJs: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-wasm-js.klib") }
    @JvmStatic
    val kotlinStdlibWasmWasi: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-wasm-wasi.klib") }
    @JvmStatic
    val kotlinStdlibSources: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-sources.jar") }
    @JvmStatic
    val kotlinStdlibLegacy1922: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-1.9.22.jar") }
    @JvmStatic
    val kotlinStdLibProjectWizardDefault: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-project-wizard-default.jar") }

    // In 1.x, `kotlin-stdlib-common` still contained `.kotlin_metadata` files. 2.x versions of the library contain `.knm` files, since it's
    // now a klib.
    @JvmStatic
    val kotlinStdlibCommonLegacy1922: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-stdlib-common.jar") }
    @JvmStatic
    val kotlinTest: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-test.jar") }
    @JvmStatic
    val kotlinTestJs: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-test-js.klib") }
    @JvmStatic
    val kotlinTestJunit: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-test-junit.jar") }
    @JvmStatic
    val parcelizeRuntime: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:parcelize-compiler-plugin-for-ide.jar") }
    @JvmStatic
    val composeCompilerPluginForIde: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:compose-compiler-plugin-for-ide.jar") }

    @JvmStatic
    val kotlinJvmDebuggerTestData: File by lazy { getKotlinDepsByLabel("@community//plugins/kotlin/jvm-debugger/test:testData") }

    @Suppress("NO_REFLECTION_IN_CLASS_PATH")
    @JvmStatic
    fun main(args: Array<String>) {
        // prints all available test artifacts for DEBUGGING ONLY
        for (member in TestKotlinArtifacts::class.members) {
            if (member.name == "kotlinJvmDebuggerTestData") continue
            if (member.visibility != KVisibility.PUBLIC) continue
            if (member.parameters.size != 1) continue
            if (member.returnType.classifier == File::class || member.returnType.classifier == Path::class) {
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
    val trove4j: File by lazy {
        @Suppress("IO_FILE_USAGE")
        PathManager.getJarForClass(TestKotlinArtifacts::class.java.classLoader.loadClass("gnu.trove.THashMap"))!!.toFile()
    }
    @JvmStatic
    val jetbrainsAnnotations: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:annotations.jar") }
    @JvmStatic
    val jsr305: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:jsr305.jar") }
    @JvmStatic
    val junit3: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps//:junit.jar") }
    @JvmStatic
    val kotlinxCoroutines: File by lazy {
        @Suppress("IO_FILE_USAGE")
        PathManager.getJarForClass(TestKotlinArtifacts::class.java.classLoader.loadClass("kotlinx.coroutines.CoroutineScope"))!!.toFile()
    }
    @JvmStatic
    val coroutineContext: File by lazy {
        @Suppress("IO_FILE_USAGE")
        PathManager.getJarForClass(TestKotlinArtifacts::class.java.classLoader.loadClass("kotlin.coroutines.CoroutineContext"))!!.toFile()
    }

    /**
     * @throws TargetSupportException on access from an inappropriate host.
     * See KT-36871, KTIJ-28066.
     */
    @JvmStatic
    val kotlinStdlibNative: File by lazy { getNativeLib(library = "klib/common/stdlib") }

    @JvmStatic
    val compilerTestDataDir: File by lazy {
        val artifact = getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-compiler-testdata-for-ide.jar")
        val targetDir = Path.of(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlinc-testdata-2")
        runBlocking {
            extractFile(artifact.toPath(), targetDir, communityRoot)
        }
        @Suppress("IO_FILE_USAGE")
        return@lazy targetDir.toFile()
    }

    @JvmStatic
    fun compilerTestData(compilerTestDataPath: String): String {
        return compilerTestDataDir.resolve(compilerTestDataPath).canonicalPath
    }

    @JvmStatic
    val jpsPluginTestDataDir: File by lazy {
        val artifact = getKotlinDepsByLabel("@kotlin_test_deps//:kotlin-jps-plugin-testdata-for-ide.jar")
        val targetDir = Path.of(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlinc-jps-testdata")
        runBlocking {
            extractFile(artifact.toPath(), targetDir, communityRoot)
        }
        @Suppress("IO_FILE_USAGE")
        return@lazy targetDir.toFile()
    }

    @JvmStatic
    val jsIrRuntimeDir: File by lazy { return@lazy getKotlinDepsByLabel("@kotlin_test_deps//:js-ir-runtime-for-ide.klib") }

    @JvmStatic
    fun jpsPluginTestData(jpsTestDataPath: String): String {
        return jpsPluginTestDataDir.resolve(jpsTestDataPath).canonicalPath
    }

    @Throws(TargetSupportException::class)
    fun getNativeLib(
        version: String = KotlinNativeVersion.resolvedKotlinNativeVersion,
        platform: String = HostManager.platformName(),
        library: String
    ): File {
        if (!KotlinNativeHostSupportDetector.isNativeHostSupported() && platform == HostManager.platformName())
            throw TargetSupportException("kotlin-native-prebuilt can't be downloaded as it doesn't exist for the host: ${platform}")

        val baseDir = File(PathManager.getCommunityHomePath()).resolve("out")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        val prebuilt = "kotlin-native-prebuilt-$platform-$version"
        val archiveName = if (HostManager.hostIsMingw) "$prebuilt.zip" else "$prebuilt.tar.gz"
        val cdnUrl = if ("dev" in version) NATIVE_PREBUILT_DEV_CDN_URL else NATIVE_PREBUILT_RELEASE_CDN_URL
        val downloadUrl = "$cdnUrl/$version/$platform/$archiveName"
        val downloadOut = "${baseDir.absolutePath}/$archiveName"
        val libPath = "${baseDir.absolutePath}/$prebuilt/$prebuilt/$library"
        val libFile = File(libPath)

        if (!libFile.exists()) {
            val archiveFilePath = Path.of(downloadOut)
            Files.deleteIfExists(archiveFilePath)
            downloadFile(downloadUrl, archiveFilePath)
            unpackPrebuildArchive(archiveFilePath, Path.of("$baseDir/$prebuilt"))
            Files.deleteIfExists(archiveFilePath)
        }
        return if (libFile.exists()) libFile else
            throw IOException("Library doesn't exist: $libPath")
    }

    private fun File.compressDirectoryToZip(targetZipFile: File) {
        targetZipFile.parentFile.mkdirs()
        targetZipFile.createNewFile()

        val sourceFolder = this

        ZipOutputStream(targetZipFile.outputStream().buffered()).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)
            sourceFolder
                .walkTopDown()
                .filter { file -> !file.isDirectory }
                .forEach { file ->
                    val suffix = if (file.isDirectory) "/" else ""
                    val entry = ZipEntry(file.relativeTo(sourceFolder).invariantSeparatorsPath + suffix)
                    zip.putNextEntry(entry)
                    if (!file.isDirectory) {
                        file.inputStream().buffered().use { it.copyTo(zip) }
                    }
                    zip.closeEntry()
                }
            zip.flush()
        }
    }
}
