// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin.artifacts

import com.intellij.openapi.application.PathManager
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.kotlin.idea.artifacts.KotlinNativeHostSupportDetector
import org.jetbrains.kotlin.idea.artifacts.KotlinNativePrebuiltDownloader.downloadFile
import org.jetbrains.kotlin.idea.artifacts.KotlinNativePrebuiltDownloader.unpackPrebuildArchive
import org.jetbrains.kotlin.idea.artifacts.KotlinNativeVersion
import org.jetbrains.kotlin.idea.artifacts.NATIVE_PREBUILT_DEV_CDN_URL
import org.jetbrains.kotlin.idea.artifacts.NATIVE_PREBUILT_RELEASE_CDN_URL
import org.jetbrains.kotlin.konan.file.unzipTo
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.TargetSupportException
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.resolve

object TestKotlinArtifacts {
    private val kotlinCLibrariesVersion by lazy {
        TestKotlinArtifacts::class.java.classLoader.getResourceAsStream("kotlincKotlinCompilerCliVersion.txt")?.reader()?.readText()
            ?: error("Test resource not found: kotlincKotlinCompilerCliVersion.txt")
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

    private fun getKotlinJar(artifactId: String): File {
        val target = File(PathManager.getCommunityHomePath())
            .resolve("out")
            .resolve("kotlin-from-sources-deps")
            .resolve("$artifactId-$kotlinCLibrariesVersion.jar")
        val artifact = downloadArtifact(
            "org.jetbrains.kotlin",
            artifactId,
            kotlinCLibrariesVersion
        )
        if (!areFilesEquals(artifact, target)) {
            artifact.copyTo(target, overwrite = true)
        }
        return target
    }

    private fun getKotlinKlib(artifactId: String): File {
        val target = File(PathManager.getCommunityHomePath())
            .resolve("out")
            .resolve("kotlin-from-sources-deps")
            .resolve("$artifactId-$kotlinCLibrariesVersion.klib")

        val artifact = downloadArtifact(
            "org.jetbrains.kotlin",
            artifactId,
            kotlinCLibrariesVersion,
            packaging = "klib"
        )
        if (!areFilesEquals(artifact, target)) {
            artifact.copyTo(target, overwrite = true)
        }
        return target
    }

    private fun getKotlinSourcesJar(artifactId: String, extraSuffix: String = ""): File {
        // Some tests hardcode jar names in their test data
        // (KotlinReferenceTypeHintsProviderTestGenerated).
        // That's why we need to strip the version from the jar name
        val target = File(PathManager.getCommunityHomePath())
            .resolve("out")
            .resolve("kotlin-from-sources-deps-renamed")
            .resolve("$artifactId${if (extraSuffix.isNotEmpty()) "-$extraSuffix" else ""}-sources.jar")
        val classifier = if (extraSuffix.isEmpty()) "sources" else "$extraSuffix-sources"
        val artifact = downloadArtifact(
            "org.jetbrains.kotlin",
            artifactId,
            kotlinCLibrariesVersion,
            classifier
        )
        if (!areFilesEquals(artifact, target)) {
            artifact.copyTo(target, overwrite = true)
        }
        return target
    }

    @JvmStatic val kotlinAnnotationsJvm: File by lazy { getKotlinJar("kotlin-annotations-jvm") }
    @JvmStatic val kotlinCompiler: File by lazy { getKotlinJar("kotlin-compiler" ) }
    @JvmStatic val kotlinDaemon: File by lazy { getKotlinJar("kotlin-daemon") }
    @JvmStatic val kotlinReflect: File by lazy { getKotlinJar("kotlin-reflect") }
    @JvmStatic val kotlinReflectSources: File by lazy { getKotlinSourcesJar("kotlin-reflect") }
    @JvmStatic val kotlinScriptRuntime: File by lazy { getKotlinJar("kotlin-script-runtime") }
    @JvmStatic val kotlinScriptingCommon: File by lazy { getKotlinJar("kotlin-scripting-common") }
    @JvmStatic val kotlinScriptingCompiler: File by lazy { getKotlinJar("kotlin-scripting-compiler") }
    @JvmStatic val kotlinScriptingCompilerImpl: File by lazy { getKotlinJar("kotlin-scripting-compiler-impl") }
    @JvmStatic val kotlinScriptingJvm: File by lazy { getKotlinJar("kotlin-scripting-jvm") }
    @JvmStatic val kotlinStdlib: File by lazy { getKotlinJar("kotlin-stdlib") }
    @JvmStatic val kotlinStdlibCommon: File by lazy {
        val baseDir = File(PathManager.getCommunityHomePath()).resolve("out")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }

        val artefactName = "kotlin-stdlib-common"
        val libFile = baseDir.resolve("$artefactName.klib")

        if (!libFile.exists()) {
            val archiveFile = baseDir.resolve(artefactName)
            val archiveFilePath = archiveFile.toPath()
            archiveFile.deleteRecursively()
            val stdlibAllJar = downloadArtifact("org.jetbrains.kotlin", "kotlin-stdlib", kotlinCLibrariesVersion, "all")
            stdlibAllJar.toPath().unzipTo(archiveFilePath)

            val unpackedCommonMain = archiveFilePath.resolve("commonMain").toFile()
            unpackedCommonMain.compressDirectoryToZip(libFile)
        }

        libFile
    }
    @JvmStatic val kotlinStdlibCommonSources: File by lazy { getKotlinSourcesJar("kotlin-stdlib", extraSuffix = "common") }
    @JvmStatic val kotlinStdlibJdk7: File by lazy { getKotlinJar("kotlin-stdlib-jdk7") }
    @JvmStatic val kotlinStdlibJdk7Sources: File by lazy { getKotlinSourcesJar("kotlin-stdlib-jdk7") }
    @JvmStatic val kotlinStdlibJdk8: File by lazy { getKotlinJar("kotlin-stdlib-jdk8") }
    @JvmStatic val kotlinStdlibJdk8Sources: File by lazy { getKotlinSourcesJar("kotlin-stdlib-jdk8") }
    @JvmStatic val kotlinStdlibJs: File by lazy { getKotlinKlib("kotlin-stdlib-js") }
    // The latest published kotlin-stdlib-js with both .knm and .kjsm roots
    @JvmStatic val kotlinStdlibJsLegacyJar: File by lazy {
        val version = "1.9.22"
        val target = File(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlin-from-sources-deps").resolve("kotlin-stdlib-js-$version.jar")
        val artifact = downloadArtifact("org.jetbrains.kotlin", "kotlin-stdlib-js", "$version")
        if (!areFilesEquals(artifact, target)) {
            artifact.copyTo(target, overwrite = true)
        }
        return@lazy target
    }
    @JvmStatic val kotlinDomApiCompat: File by lazy { getKotlinKlib("kotlin-dom-api-compat",) }
    @JvmStatic val kotlinStdlibWasmJs: File by lazy { getKotlinKlib("kotlin-stdlib-wasm-js") }
    @JvmStatic val kotlinStdlibWasmWasi: File by lazy { getKotlinKlib("kotlin-stdlib-wasm-wasi") }
    @JvmStatic val kotlinStdlibSources: File by lazy { getKotlinSourcesJar("kotlin-stdlib") }
    @JvmStatic val kotlinStdlibLegacy1922: File by lazy {
        val version = "1.9.22"
        val target = File(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlin-from-sources-deps").resolve("kotlin-stdlib-$version.jar")
        val artifact = downloadArtifact("org.jetbrains.kotlin", "kotlin-stdlib", "$version")
        if (!areFilesEquals(artifact, target)) {
            artifact.copyTo(target, overwrite = true)
        }
        return@lazy target
    }
    // In 1.x, `kotlin-stdlib-common` still contained `.kotlin_metadata` files. 2.x versions of the library contain `.knm` files, since it's
    // now a klib.
    @JvmStatic val kotlinStdlibCommonLegacy1922: File by lazy {
        val version = "1.9.22"
        val target = File(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlin-from-sources-deps").resolve("kotlin-stdlib-common-$version.jar")
        val artifact = downloadArtifact("org.jetbrains.kotlin", "kotlin-stdlib-common", "$version")
        if (!areFilesEquals(artifact, target)) {
            artifact.copyTo(target, overwrite = true)
        }
        return@lazy target
    }
    @JvmStatic val kotlinTest: File by lazy { getKotlinJar("kotlin-test") }
    @JvmStatic val kotlinTestJs: File by lazy { getKotlinKlib("kotlin-test-js") }
    @JvmStatic val kotlinTestJunit: File by lazy { getKotlinJar("kotlin-test-junit") }
    @JvmStatic val parcelizeRuntime: File by lazy { getKotlinJar("parcelize-compiler-plugin-for-ide") }

    @JvmStatic val trove4j: File by lazy {
        PathManager.getJarForClass(TestKotlinArtifacts::class.java.classLoader.loadClass("gnu.trove.THashMap"))!!.toFile()
    }
    @JvmStatic val jetbrainsAnnotations: File by lazy {
        val version = "26.0.2"
        val target = File(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlin-from-sources-deps").resolve("annotations-$version.jar")
        val artifact = downloadArtifact("org.jetbrains", "annotations", "$version", repository = KotlinArtifactRepository.MAVEN_CENTRAL)
        if (!areFilesEquals(artifact, target)) {
            artifact.copyTo(target, overwrite = true)
        }
        return@lazy target

    }
    @JvmStatic val jsr305: File by lazy {
        val version = "3.0.2"
        val target = File(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlin-from-sources-deps").resolve("jsr305-$version.jar")
        val artifact = downloadArtifact("com.google.code.findbugs", "jsr305", "$version", repository = KotlinArtifactRepository.MAVEN_CENTRAL)
        if (!areFilesEquals(artifact, target)) {
            artifact.copyTo(target, overwrite = true)
        }
        return@lazy target
    }
    @JvmStatic val junit3: File by lazy {
        val version = "3.8.2"
        val target = File(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlin-from-sources-deps").resolve("junit-$version.jar")
        val artifact = downloadArtifact("junit", "junit", "$version", repository = KotlinArtifactRepository.MAVEN_CENTRAL)
        if (!areFilesEquals(artifact, target)) {
            artifact.copyTo(target, overwrite = true)
        }
        return@lazy target
    }
    @JvmStatic val kotlinxCoroutines: File by lazy {
        PathManager.getJarForClass(TestKotlinArtifacts::class.java.classLoader.loadClass("kotlinx.coroutines.CoroutineScope"))!!.toFile()
    }
    @JvmStatic val coroutineContext: File by lazy {
        PathManager.getJarForClass(TestKotlinArtifacts::class.java.classLoader.loadClass("kotlin.coroutines.CoroutineContext"))!!.toFile()
    }

    /**
     * @throws org.jetbrains.kotlin.konan.target.TargetSupportException on access from an inappropriate host.
     * See KT-36871, KTIJ-28066.
     */
    @JvmStatic val kotlinStdlibNative: File by lazy { getNativeLib(library = "klib/common/stdlib") }

    @JvmStatic
    val compilerTestDataDir: File by lazy {
        val artifact = downloadArtifact("org.jetbrains.kotlin", "kotlin-compiler-testdata-for-ide", kotlinCLibrariesVersion)
        LazyZipUnpacker(File(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlinc-testdata-2")).lazyUnpack(artifact)
    }

    @JvmStatic
    fun compilerTestData(compilerTestDataPath: String): String {
        return compilerTestDataDir.resolve(compilerTestDataPath).canonicalPath
    }

    @JvmStatic
    val jpsPluginTestDataDir: File by lazy {
        val artifact = downloadArtifact("org.jetbrains.kotlin", "kotlin-jps-plugin-testdata-for-ide", kotlinCLibrariesVersion)
        LazyZipUnpacker(File(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlinc-jps-testdata")).lazyUnpack(artifact)
    }

    @JvmStatic
    val jsIrRuntimeDir: File by lazy {
        val target = File(PathManager.getCommunityHomePath()).resolve("out").resolve("js-ir-runtime-for-ide").resolve("js-ir-runtime-for-ide.jar")
        val version = TestKotlinArtifacts::class.java.classLoader.getResourceAsStream("kotlincKotlinJpsPluginTests.txt")?.reader()?.readText()
            ?: error("Test resource not found: kotlincKotlinJpsPluginTests.txt")
        val artifact = downloadArtifact("org.jetbrains.kotlin", "js-ir-runtime-for-ide", version, packaging = "klib")
        if (!areFilesEquals(artifact, target)) {
            artifact.copyTo(target, overwrite = true)
        }
        return@lazy target
    }

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
        val archiveName =  if (HostManager.hostIsMingw) "$prebuilt.zip" else "$prebuilt.tar.gz"
        val cdnUrl = if ("dev" in version) NATIVE_PREBUILT_DEV_CDN_URL else NATIVE_PREBUILT_RELEASE_CDN_URL
        val downloadUrl = "$cdnUrl/$version/$platform/$archiveName"
        val downloadOut = "${baseDir.absolutePath}/$archiveName"
        val libPath = "${baseDir.absolutePath}/$prebuilt/$prebuilt/$library"
        val libFile = File(libPath)

        if (!libFile.exists()) {
            val archiveFilePath = Paths.get(downloadOut)
            Files.deleteIfExists(archiveFilePath)
            downloadFile(downloadUrl, archiveFilePath)
            unpackPrebuildArchive(archiveFilePath, Paths.get("$baseDir/$prebuilt"))
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

enum class KotlinArtifactRepository(val url: String) {
    INTELLIJ_DEPENDENCIES("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/"),
    MAVEN_CENTRAL("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/");
}

@JvmOverloads
fun downloadArtifact(
    groupId: String,
    artifactId: String,
    version: String,
    classifier: String? = null,
    packaging: String = "jar",
    repository: KotlinArtifactRepository = KotlinArtifactRepository.INTELLIJ_DEPENDENCIES
): File {
    val url = BuildDependenciesDownloader.getUriForMavenArtifact(
        repository.url, groupId, artifactId, version, classifier, packaging)
    return BuildDependenciesDownloader.downloadFileToCacheLocation(
        BuildDependenciesCommunityRoot(Paths.get(PathManager.getCommunityHomePath())), url).toFile()
}
