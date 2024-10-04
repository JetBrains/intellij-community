// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.RefGroup
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsRefType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color

class SimpleRefGroup @JvmOverloads constructor(private val name: @Nls String,
                                               private val refs: MutableList<VcsRef>,
                                               private val isExpanded: Boolean = false) : RefGroup {
  override fun isExpanded(): Boolean = isExpanded
  override fun getName(): String = name
  override fun getRefs(): MutableList<VcsRef> = refs

  override fun getColors(): List<Color> {
    return refs.groupBy(VcsRef::getType).flatMap { (type, references) ->
      val color = type.backgroundColor
      if (references.size > 1) listOf(color, color) else listOf(color)
    }
  }

  companion object {
    @JvmStatic
    fun buildGroups(refGroups: List<RefGroup>,
                    refs: MultiMap<VcsRefType, VcsRef>,
                    compact: Boolean,
                    showTagNames: Boolean): List<RefGroup> {
      if (refs.isEmpty) return refGroups
      if (compact) {
        if (refGroups.isEmpty()) {
          val firstRef = refs.values().first()
          return listOf(SimpleRefGroup(if (firstRef.type.isBranch || showTagNames) firstRef.name else "",
                                       refs.values().toMutableList()))
        }
        val allRefs = refGroups.flatMap { it.refs } + refs.values()
        return listOf(SimpleRefGroup(refGroups.first().name, allRefs.toMutableList()))
      }
      val result = mutableListOf<RefGroup>()
      result.addAll(refGroups)
      for ((refType, refsOfType) in refs.entrySet()) {
        if (refType.isBranch) {
          result.addAll(refsOfType.map { ref -> SimpleRefGroup(ref.name, arrayListOf(ref)) })
        }
        else {
          result.add(SimpleRefGroup(if (showTagNames) refsOfType.first().name else "", refsOfType.toMutableList()))
        }
      }
      return result
    }
  }
}