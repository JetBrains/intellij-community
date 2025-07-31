// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin.artifacts

import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.util.io.createParentDirectories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.extractFile
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
import kotlin.io.path.copyTo
import kotlin.io.path.deleteIfExists
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.system.exitProcess

object TestKotlinArtifacts {
    private val communityRoot by lazy {
        BuildDependenciesCommunityRoot(Path.of(PathManager.getCommunityHomePath()))
    }

    private fun areFilesEquals(source: File, destination: File): Boolean {
        if (!destination.exists()) {
            return false
        }
        if (source.length() != destination.length()) {
            return false
        }
        return source.readBytes().contentEquals(destination.readBytes())
    }

    /**
     * Returns a directory which will be under KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX
     * to satisfy FromKotlinDistForIdeByNameFallbackBundledFirCompilerPluginProvider
     */
    internal val kotlinDistForIdeUnpacked: Path by lazy {
        val artifact = getKotlinDepsByLabel("@kotlin_test_deps_kotlin-dist-for-ide//file:kotlin-dist-for-ide.jar")

        val targetDirectory = KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX.toPath().resolve(artifact.nameWithoutExtension)
        runBlocking(Dispatchers.IO) {
            extractFile(artifact.toPath(), targetDirectory, communityRoot)
        }

        targetDirectory
    }

    @JvmStatic
    val kotlinDistForIdeUnpackedForIncrementalCompilation: Path by lazy {
        val artifact = getKotlinDepsByLabel("@kotlin_test_deps_kotlin-dist-for-ide-increment-compilation//file:kotlin-dist-for-ide-increment-compilation-2.2.0.jar")
        val targetDirectory = KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX.toPath().resolve(artifact.nameWithoutExtension)
        runBlocking(Dispatchers.IO) {
            extractFile(artifact.toPath(), targetDirectory, communityRoot)
        }

        targetDirectory
    }

    internal val jpsPluginClasspath: List<Path> by lazy {
        listOf(getKotlinDepsByLabel("@kotlin_test_deps_kotlin-jps-plugin-classpath//file:kotlin-jps-plugin-classpath.jar").toPath())
    }

    // https://bazel.build/rules/lib/builtins/Label.html
    data class BazelLabel(
        val repo: String,
        val packageName: String,
        val file: String,
    ) {
        companion object {
            private val regex = Regex("@([a-zA-Z0-9_-]+)//([a-z0-9_/-]+):([a-z0-9._-]+)")
            fun fromString(label: String): BazelLabel {
                val match = regex.matchEntire(label) ?: error("Bazel label must match '${regex.pattern}': $label")
                return BazelLabel(
                    repo = match.groupValues[1],
                    packageName = match.groupValues[2],
                    file = match.groupValues[3],
                )
            }
        }

        val asLabel: String
            get() = "@$repo//$packageName:$file"
    }

    private fun getFileFromBazelRuntime(label: BazelLabel): Path {
        val repoEntry = BazelTestUtil.bazelTestRepoMapping.getOrElse(label.repo) {
            error("Unable to determine dependency path '${label.asLabel}'")
        }
        val file = BazelTestUtil.bazelTestRunfilesPath.resolve(
            repoEntry.runfilesRelativePath + "/${label.packageName}/${label.file}"
        )
        if (!Files.isRegularFile(file)) {
            error("Unable to find test dependency '${label.asLabel}' at $file")
        }
        return file
    }

    private fun findUrl(label: BazelLabel): URI {
        return KotlinTestsDependenciesUtil.kotlinTestDependenciesHttpFiles.find { it.name == label.repo && it.downloadFilePath == label.file }?.let {
            URI(it.url)
        } ?: error("Unable to find URL for '${label.asLabel}'")
    }

    private fun downloadFile(label: BazelLabel): Path {
        val fileInCache = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, findUrl(label))
        val target = communityRoot.communityRoot.resolve("out/kotlin-from-sources-deps/${label.file}")
        if (!Files.exists(target.parent)) {
            target.createParentDirectories()
        }
        @Suppress("IO_FILE_USAGE")
        if (!areFilesEquals(fileInCache.toFile(), target.toFile())) {
            Files.copy(fileInCache, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }

    // @kotlin_test_deps_kotlin-stdlib//file:kotlin-stdlib.jar
    private fun getKotlinDepsByLabel(bazelLabel: String): File {
        val label = BazelLabel.fromString(bazelLabel)

        // Why it is different
        val file = if (BazelTestUtil.isUnderBazelTest) {
            getFileFromBazelRuntime(label)
        } else {
            downloadFile(label)
        }

        // some tests for code require that files should be under $COMMUNITY_HOME_PATH/out
        val target = File(PathManager.getCommunityHomePath())
            .resolve("out")
            .resolve("kotlin-from-sources-deps")
            .resolve(file.name)

        // we could have a file from some previous launch, but with different content
        // it is a valid scenario when file it is JAR without a version and url changed
        // we have to verify content
        @Suppress("IO_FILE_USAGE")
        if (target.exists() && areFilesEquals(file.toFile(), target)) {
            return target
        }
        val tempFile = Files.createTempFile(file.name, ".tmp")
        try {
            file.copyTo(tempFile, true)
            target.parentFile.mkdirs()
            // in the case of parallel access target will be overwritten by one of the threads
            tempFile.moveTo(target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            tempFile.deleteIfExists()
        }
        return target
    }

    @JvmStatic
    val kotlinAnnotationsJvm: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-annotations-jvm//file:kotlin-annotations-jvm.jar") }
    @JvmStatic
    val kotlinCompiler: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-compiler//file:kotlin-compiler.jar") }
    @JvmStatic
    val kotlinDaemon: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-daemon//file:kotlin-daemon.jar") }
    @JvmStatic
    val kotlinReflect: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-reflect//file:kotlin-reflect.jar") }
    @JvmStatic
    val kotlinReflectSources: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-reflect-sources//file:kotlin-reflect-sources.jar") }
    @JvmStatic
    val kotlinScriptRuntime: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-script-runtime//file:kotlin-script-runtime.jar") }
    @JvmStatic
    val kotlinScriptingCommon: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-scripting-common//file:kotlin-scripting-common.jar") }
    @JvmStatic
    val kotlinScriptingCompiler: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-scripting-compiler//file:kotlin-scripting-compiler.jar") }
    @JvmStatic
    val kotlinScriptingCompilerImpl: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-scripting-compiler-impl//file:kotlin-scripting-compiler-impl.jar") }
    @JvmStatic
    val kotlinScriptingJvm: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-scripting-jvm//file:kotlin-scripting-jvm.jar") }
    @JvmStatic
    val kotlinStdlib: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib//file:kotlin-stdlib.jar") }
    @JvmStatic
    val kotlinStdlib170: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-170//file:kotlin-stdlib-1.7.0.jar") }
    @JvmStatic
    val kotlinStdlib170Sources: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-170-sources//file:kotlin-stdlib-1.7.0-sources.jar") }
    @JvmStatic
    val kotlinStdlibCommon170Sources: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-common-170-sources//file:kotlin-stdlib-common-1.7.0-sources.jar") }

    @JvmStatic
    val kotlinStdlibCommon: File by lazy {
        val artifact = getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-all//file:kotlin-stdlib-all.jar")
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
    val kotlinStdlibCommonSources: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-common-sources//file:kotlin-stdlib-common-sources.jar") }
    @JvmStatic
    val kotlinStdlibJdk7: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-jdk7//file:kotlin-stdlib-jdk7.jar") }
    @JvmStatic
    val kotlinStdlibJdk7Sources: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-jdk7-sources//file:kotlin-stdlib-jdk7-sources.jar") }
    @JvmStatic
    val kotlinStdlibJdk8: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-jdk8//file:kotlin-stdlib-jdk8.jar") }
    @JvmStatic
    val kotlinStdlibJdk8Sources: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-jdk8-sources//file:kotlin-stdlib-jdk8-sources.jar") }
    @JvmStatic
    val kotlinStdlibJs: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-js//file:kotlin-stdlib-js.klib") }

    // The latest published kotlin-stdlib-js with both .knm and .kjsm roots
    @JvmStatic
    val kotlinStdlibJsLegacyJar: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-js-legacy//file:kotlin-stdlib-js-1.9.22.jar") }
    @JvmStatic
    val kotlinDomApiCompat: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-dom-api-compat//file:kotlin-dom-api-compat.klib") }
    @JvmStatic
    val kotlinStdlibWasmJs: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-wasm-js//file:kotlin-stdlib-wasm-js.klib") }
    @JvmStatic
    val kotlinStdlibWasmWasi: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-wasm-wasi//file:kotlin-stdlib-wasm-wasi.klib") }
    @JvmStatic
    val kotlinStdlibSources: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-sources//file:kotlin-stdlib-sources.jar") }
    @JvmStatic
    val kotlinStdlibLegacy1922: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-legacy//file:kotlin-stdlib-1.9.22.jar") }
    @JvmStatic
    val kotlinStdLibProjectWizardDefault: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-project-wizard-default//file:kotlin-stdlib-project-default.jar") }

    // In 1.x, `kotlin-stdlib-common` still contained `.kotlin_metadata` files. 2.x versions of the library contain `.knm` files, since it's
    // now a klib.
    @JvmStatic
    val kotlinStdlibCommonLegacy1922: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-stdlib-common//file:kotlin-stdlib-common.jar") }
    @JvmStatic
    val kotlinTest: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-test//file:kotlin-test.jar") }
    @JvmStatic
    val kotlinTestJs: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-test-js//file:kotlin-test-js.klib") }
    @JvmStatic
    val kotlinTestJunit: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_kotlin-test-junit//file:kotlin-test-junit.jar") }
    @JvmStatic
    val parcelizeRuntime: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_parcelize-compiler-plugin-for-ide//file:parcelize-compiler-plugin-for-ide.jar") }
    @JvmStatic
    val composeCompilerPluginForIde: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_compose-compiler-plugin-for-ide//file:compose-compiler-plugin-for-ide.jar") }

    @JvmStatic
    fun main(args: Array<String>) {
        println(kotlinStdlibLegacy1922.canonicalPath)
        exitProcess(0)
    }

    @JvmStatic
    val trove4j: File by lazy {
        @Suppress("IO_FILE_USAGE")
        PathManager.getJarForClass(TestKotlinArtifacts::class.java.classLoader.loadClass("gnu.trove.THashMap"))!!.toFile()
    }
    @JvmStatic
    val jetbrainsAnnotations: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_annotations//file:annotations.jar") }
    @JvmStatic
    val jsr305: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_jsr305//file:jsr305.jar") }
    @JvmStatic
    val junit3: File by lazy { getKotlinDepsByLabel("@kotlin_test_deps_junit//file:junit.jar") }
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
        val artifact = getKotlinDepsByLabel("@kotlin_test_deps_kotlin-compiler-testdata-for-ide//file:kotlin-compiler-testdata-for-ide.jar")
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
        val artifact = getKotlinDepsByLabel("@kotlin_test_deps_kotlin-jps-plugin-testdata-for-ide//file:kotlin-jps-plugin-testdata-for-ide.jar")
        val targetDir = Path.of(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlinc-jps-testdata")
        runBlocking {
            extractFile(artifact.toPath(), targetDir, communityRoot)
        }
        @Suppress("IO_FILE_USAGE")
        return@lazy targetDir.toFile()
    }

    @JvmStatic
    val jsIrRuntimeDir: File by lazy { return@lazy getKotlinDepsByLabel("@kotlin_test_deps_js-ir-runtime-for-ide//file:js-ir-runtime-for-ide.klib") }

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
