// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradle.configuration.klib

import com.intellij.util.containers.orNull
import org.jetbrains.kotlin.library.KLIB_MANIFEST_FILE_NAME
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.io.path.isRegularFile

internal fun interface KlibManifestProvider {
    fun getManifest(libraryPath: Path): Properties?

    companion object {
        fun default(): KlibManifestProvider {
            return CachedKlibManifestProvider(
                cache = mutableMapOf(),
                provider = CompositeKlibManifestProvider(
                    listOf(DirectoryKlibManifestProvider(), ZipKlibManifestProvider)
                )
            )
        }
    }
}

internal interface KlibManifestFileFinder {
    fun getManifestFile(libraryPath: Path): Path?
}

internal class CompositeKlibManifestProvider(
    private val providers: List<KlibManifestProvider>
) : KlibManifestProvider {
    override fun getManifest(libraryPath: Path): Properties? {
        return providers.asSequence()
            .map { it.getManifest(libraryPath) }
            .filterNotNull()
            .firstOrNull()
    }

}

internal class CachedKlibManifestProvider(
    private val cache: MutableMap<Path, Properties> = mutableMapOf(),
    private val provider: KlibManifestProvider
) : KlibManifestProvider {
    override fun getManifest(libraryPath: Path): Properties? {
        return cache.getOrPut(libraryPath) {
            provider.getManifest(libraryPath) ?: return null
        }
    }
}

internal class DirectoryKlibManifestProvider(
    private val manifestFileFinder: KlibManifestFileFinder = DefaultKlibManifestFileFinder,
) : KlibManifestProvider {

    override fun getManifest(libraryPath: Path): Properties? {
        val file = manifestFileFinder.getManifestFile(libraryPath) ?: return null
        val properties = Properties()
        try {
            Files.newInputStream(file).use { properties.load(it) }
            return properties
        } catch (_: IOException) {
            return null
        }
    }
}

internal object ZipKlibManifestProvider : KlibManifestProvider {
    private val manifestEntryRegex = Regex("""(.+/manifest|manifest)""")

    override fun getManifest(libraryPath: Path): Properties? {
      if (!libraryPath.isRegularFile()) return null
        try {
            val properties = Properties()
            val zipFile = ZipFile(libraryPath.toFile())
            zipFile.use {
                val manifestEntry = zipFile.findManifestEntry() ?: return null
                zipFile.getInputStream(manifestEntry).use { manifestStream ->
                    properties.load(manifestStream)
                }
            }
            return properties
        } catch (_: Exception) {
            return null
        }
    }

    private fun ZipFile.findManifestEntry(): ZipEntry? {
        return this.stream().filter { entry -> entry.name.matches(manifestEntryRegex) }.findFirst().orNull()
    }
}

private object DefaultKlibManifestFileFinder : KlibManifestFileFinder {
    override fun getManifestFile(libraryPath: Path): Path? {
        libraryPath.resolve(KLIB_MANIFEST_FILE_NAME).let { propertiesPath ->
            // KLIB layout without components
            if (Files.isRegularFile(propertiesPath)) return propertiesPath
        }

        if (!Files.isDirectory(libraryPath)) return null

        // look up through all components and find all manifest files
        val candidates = Files.newDirectoryStream(libraryPath).use { stream ->
            stream.mapNotNull { componentPath ->
                val candidate = componentPath.resolve(KLIB_MANIFEST_FILE_NAME)
                if (Files.isRegularFile(candidate)) candidate else null
            }
        }

        return when (candidates.size) {
            0 -> null
            1 -> candidates.single()
            else -> {
                // there are multiple components, let's take just the first one alphabetically
                candidates.minByOrNull { it.getName(it.nameCount - 2).toString() }!!
            }
        }
    }
}

