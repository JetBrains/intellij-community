// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

import com.intellij.openapi.util.io.FileUtilRt
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
  fun <V> createCanonicalPathLinkedMap(): MutableMap<Path, V> = Object2ObjectLinkedOpenCustomHashMap(PathHashStrategy)

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
  fun <V> createCanonicalFileMap(): MutableMap<File, V> = Object2ObjectOpenCustomHashMap(FileHashStrategy)

  @JvmStatic
  fun <V> createCanonicalPathMap(): MutableMap<Path, V> = Object2ObjectOpenCustomHashMap(PathHashStrategy)

  @JvmStatic
  fun <V> createCanonicalFileMap(expected: Int): MutableMap<File, V> {
    return Object2ObjectOpenCustomHashMap(expected, FileHashStrategy)
  }

  @JvmStatic
  fun <V> createCanonicalFileMap(map: Map<out File, V>): MutableMap<File, V> = Object2ObjectOpenCustomHashMap(map, FileHashStrategy)

  @JvmStatic
  fun createCanonicalFileSet(): MutableSet<File> = ObjectOpenCustomHashSet(FileHashStrategy)

  @JvmStatic
  fun createCanonicalFileSet(files: Collection<File>): MutableSet<File> = ObjectOpenCustomHashSet(files, FileHashStrategy)

  @JvmStatic
  fun createCanonicalPathSet(): MutableSet<Path> = ObjectOpenCustomHashSet(PathHashStrategy)

  @JvmStatic
  fun createCanonicalPathSet(files: Collection<Path>): MutableSet<Path> = ObjectOpenCustomHashSet(files, PathHashStrategy)

  @JvmStatic
  fun createCanonicalPathSet(size: Int): MutableSet<Path> = ObjectOpenCustomHashSet(size, PathHashStrategy)

  @JvmStatic
  fun createCanonicalFilePathSet(): MutableSet<String> = ObjectOpenCustomHashSet(FastUtilHashingStrategies.FILE_PATH_HASH_STRATEGY)

  @JvmStatic
  fun createCanonicalFileLinkedSet(): MutableSet<File> = ObjectLinkedOpenCustomHashSet(FileHashStrategy)
}

@Internal
object FileHashStrategy : Hash.Strategy<File>, Serializable {
  override fun hashCode(o: File?): Int = FileUtilRt.pathHashCode(o?.path)

  override fun equals(a: File?, b: File?): Boolean = FileUtilRt.pathsEqual(a?.path, b?.path)
}

@Internal
object PathHashStrategy : Hash.Strategy<Path>, Serializable {
  override fun hashCode(o: Path?): Int = FileUtilRt.pathHashCode(o?.toString())

  override fun equals(a: Path?, b: Path?): Boolean = FileUtilRt.pathsEqual(a?.toString(), b?.toString())
}
