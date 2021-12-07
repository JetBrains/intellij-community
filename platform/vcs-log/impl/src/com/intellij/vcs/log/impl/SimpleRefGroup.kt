// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.RefGroup
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsRefType
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
    fun buildGroups(groupedRefs: MultiMap<VcsRefType, VcsRef>,
                    compact: Boolean,
                    showTagNames: Boolean,
                    result: MutableList<RefGroup>) {
      if (groupedRefs.isEmpty) return
      if (compact) {
        if (result.isEmpty()) {
          val firstRef = groupedRefs.values().first()
          result.add(SimpleRefGroup(if (firstRef.type.isBranch || showTagNames) firstRef.name else "",
                                    groupedRefs.values().toMutableList()))
        }
        else {
          result.first().refs.addAll(groupedRefs.values())
        }
      }
      else {
        for ((refType, refs) in groupedRefs.entrySet()) {
          if (refType.isBranch) {
            result.addAll(refs.map { ref -> SimpleRefGroup(ref.name, arrayListOf(ref)) })
          }
          else {
            result.add(SimpleRefGroup(if (showTagNames) refs.first().name else "", refs.toMutableList()))
          }
        }
      }
    }
  }
}