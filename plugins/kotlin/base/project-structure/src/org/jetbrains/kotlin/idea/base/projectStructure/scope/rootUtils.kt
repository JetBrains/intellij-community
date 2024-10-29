// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.scope

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.search.impl.VirtualFileEnumeration
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap

/**
 * Creates a map from objects [T] to their index in the given [LinkedHashSet]. This allows retrieving the index where the element had been
 * at in the original linked hash set, for example to compare the precedence between two objects.
 */
internal fun <T : Any> LinkedHashSet<T>.toObject2IndexMap(): Object2IntMap<T> {
    var i = 1
    val map = Object2IntOpenHashMap<T>(this.size)
    for (element in this) {
        map.put(element, i++)
    }
    return map
}

internal fun computeFileEnumerationUnderRoots(roots: Collection<VirtualFile>): VirtualFileEnumeration {
    val ids = IntOpenHashSet()

    for (file in roots) {
        if (file is VirtualFileWithId) {
            val children = VirtualFileManager.getInstance().listAllChildIds(file.id)
            ids.addAll(IntList.of(*children))
        }
    }

    return IntSetVirtualFileEnumeration(ids)
}

private class IntSetVirtualFileEnumeration(private val ids: IntSet) : VirtualFileEnumeration {
    override fun contains(fileId: Int): Boolean = ids.contains(fileId)

    override fun asArray(): IntArray = ids.toIntArray()

    override fun equals(other: Any?): Boolean = this === other || other is IntSetVirtualFileEnumeration && ids == other.ids

    override fun hashCode(): Int = ids.hashCode()

    override fun toString(): String = asArray().contentToString()
}
