// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.platform.ide

import com.dynatrace.hash4j.hashing.HashStream64
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.idea.AppMode
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.walk

internal class PluginHasher(expectedSize: Int) {
  private val hashedPluginDescriptors = IdentityHashMap<IdeaPluginDescriptorImpl, Long>(expectedSize)

  fun addPluginFingerprint(plugin: IdeaPluginDescriptor, hasher: HashStream64) {
    hasher.putString(plugin.pluginId.idString)
    hasher.putString(plugin.version)
    if (plugin.version.contains("SNAPSHOT", ignoreCase = true)) {
      hasher.putLong(fingerprintFromFileContent(plugin as IdeaPluginDescriptorImpl))
    }
  }

  fun getDebugInfo(): String {
    val plugins = hashedPluginDescriptors.keys.sortedBy { it.pluginId }
    val builder = StringBuilder()
    for (plugin in plugins) {
      val id = plugin.pluginId.idString
      builder
        .append(id)
        .append(" - ")
        .append(java.lang.Long.toUnsignedString(hashedPluginDescriptors.get(plugin)!!, Character.MAX_RADIX))
        .append("\n")
    }
    return builder.toString()
  }

  @OptIn(ExperimentalPathApi::class)
  private fun fingerprintFromFileContent(descriptor: IdeaPluginDescriptorImpl): Long {
    val isDevMode = AppMode.isDevServer()
    val hash = hashedPluginDescriptors.computeIfAbsent(descriptor) {
      if (descriptor.useCoreClassLoader) {
        return@computeIfAbsent 0L
      }

      val files = descriptor.jarFiles!!
      val hasher = Hashing.komihash5_0().hashStream()
      hasher.putInt(files.size)
      for (file in files) {
        ProgressManager.checkCanceled()

        if (isDevMode) {
          val pathString = file.toString()
          if (pathString.endsWith(".jar")) {
            hashFile(file, hasher, pathString)
          }
          else {
            // classpath.index cannot be used as it is created not before but during IDE launch
            // .unmodified must be present
            try {
              hashFile(file.resolve(".unmodified"), hasher, pathString)
            }
            catch (e: NoSuchFileException) {
              thisLogger().warn(".unmodified doesn't exist, cannot compute module fingerprint", e)
            }
          }
        }
        else {
          // if the path is not a directory, only "this" file will be visited
          // if the path is a directory, all the regular files will be visited
          // note, that symlinks are not followed
          file.walk().sorted().forEach { f ->
            // /tmp/byteBuddyAgent12978532094762450051.jar:1261:2023-09-21T12:46:17.196727952Z <= this file is always different :(
            // see also https://youtrack.jetbrains.com/issue/IJPL-166
            val absolutePathString = f.toAbsolutePath().toString()
            if (!absolutePathString.startsWith("/tmp/byteBuddyAgent")) {
              hashFile(f, hasher, absolutePathString)
            }
          }
        }
      }
      hasher.asLong
    }
    return hash
  }

  private fun hashFile(file: Path, hasher: HashStream64, pathString: String) {
    val attributes = Files.readAttributes(file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    hasher.putString(pathString)
    hasher.putLong(attributes.size())
    hasher.putLong(attributes.lastModifiedTime().toMillis())
    //println(DateTimeFormatter.ISO_LOCAL_TIME.format(attributes.lastModifiedTime().toInstant().atZone(ZoneId.systemDefault())))
  }
}