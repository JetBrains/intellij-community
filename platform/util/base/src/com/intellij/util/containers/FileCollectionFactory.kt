// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtilRt
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenCustomHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.File
import java.io.Serializable
import java.nio.file.Path

/**
 * Creates map or set with canonicalized path hash strategy.
 */
object FileCollectionFactory {
  /**
   * Create a linked map with canonicalized key hash strategy.
   */
  @JvmStatic
  fun <V> createCanonicalPathLinkedMap(): MutableMap<Path, V> = Object2ObjectLinkedOpenCustomHashMap(CanonicalPathHashStrategy)

  /**
   * Create a linked map with canonicalized key hash strategy.
   */
  @JvmStatic
  fun <V> createCanonicalFilePathLinkedMap(): MutableMap<String, V> {
    return Object2ObjectLinkedOpenCustomHashMap(object : Hash.Strategy<String?> {
      override fun hashCode(value: String?): Int = FileUtilRt.pathHashCode(value)

      override fun equals(val1: String?, val2: String?): Boolean = FileUtilRt.pathsEqual(val1, val2)
    })
  }

  @JvmStatic
  fun <V> createCanonicalFileMap(): MutableMap<File, V> = Object2ObjectOpenCustomHashMap(CanonicalFileHashStrategy)

  @JvmStatic
  fun <V> createCanonicalPathMap(): MutableMap<Path, V> = Object2ObjectOpenCustomHashMap(CanonicalPathHashStrategy)

  @JvmStatic
  fun <V> createCanonicalFileMap(expected: Int): MutableMap<File, V> {
    return Object2ObjectOpenCustomHashMap(expected, CanonicalFileHashStrategy)
  }

  @JvmStatic
  fun <V> createCanonicalFileMap(map: Map<out File, V>): MutableMap<File, V> = Object2ObjectOpenCustomHashMap(map, CanonicalFileHashStrategy)

  @JvmStatic
  fun createCanonicalFileSet(): MutableSet<File> = ObjectOpenCustomHashSet(CanonicalFileHashStrategy)

  @JvmStatic
  fun createCanonicalFileSet(files: Collection<File>): MutableSet<File> = ObjectOpenCustomHashSet(files, CanonicalFileHashStrategy)

  @JvmStatic
  fun createCanonicalPathSet(): MutableSet<Path> = ObjectOpenCustomHashSet(CanonicalPathHashStrategy)

  @JvmStatic
  fun createCanonicalLinkedPathSet(): MutableSet<Path> = ObjectLinkedOpenCustomHashSet(CanonicalPathHashStrategy)

  @JvmStatic
  fun createCaseSensitiveAwarePathSet(): MutableSet<Path> {
    // NIO is not implemented correctly on macOS - case-sensitive but APFS is case-insensitive by default
    if (SystemInfoRt.isMac && !SystemInfoRt.isFileSystemCaseSensitive) {
      return ObjectOpenCustomHashSet(CaseInsensitivePathHashStrategy)
    }
    else {
      return HashSet()
    }
  }

  @JvmStatic
  fun createCanonicalPathSet(files: Collection<Path>): MutableSet<Path> = ObjectOpenCustomHashSet(files, CanonicalPathHashStrategy)

  @JvmStatic
  fun createCanonicalPathSet(size: Int): MutableSet<Path> = ObjectOpenCustomHashSet(size, CanonicalPathHashStrategy)

  @JvmStatic
  fun createCanonicalFilePathSet(): MutableSet<String> = ObjectOpenCustomHashSet(FastUtilHashingStrategies.FILE_PATH_HASH_STRATEGY)

  @JvmStatic
  fun createCanonicalFileLinkedSet(): MutableSet<File> = ObjectLinkedOpenCustomHashSet(CanonicalFileHashStrategy)
}

@Internal
object CanonicalFileHashStrategy : Hash.Strategy<File>, Serializable {
  override fun hashCode(o: File?): Int = FileUtilRt.pathHashCode(o?.path)

  override fun equals(a: File?, b: File?): Boolean = FileUtilRt.pathsEqual(a?.path, b?.path)
}

@Internal
object CanonicalPathHashStrategy : Hash.Strategy<Path>, Serializable {
  override fun hashCode(o: Path?): Int = FileUtilRt.pathHashCode(o?.toString())

  override fun equals(a: Path?, b: Path?): Boolean = FileUtilRt.pathsEqual(a?.toString(), b?.toString())
}

private object CaseInsensitivePathHashStrategy : Hash.Strategy<Path>, Serializable {
  override fun hashCode(o: Path?): Int {
    val path = o?.toString()
    return if (path.isNullOrEmpty()) 0 else StringUtilRt.stringHashCodeInsensitive(path)
  }

  override fun equals(a: Path?, b: Path?): Boolean = a?.toString().equals(b?.toString(), ignoreCase = true)
}
