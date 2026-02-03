// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.vcs.log.RefGroup
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsRef

import org.jetbrains.annotations.ApiStatus
import java.awt.Color

@ApiStatus.Internal
class DetachedHeadRefGroup(
  private val refs: List<VcsRef>,
) : RefGroup {
  override fun getName(): String = VcsLogBundle.message("vcs.log.references.detached.head")
  override fun getRefs(): List<VcsRef> = refs
  override fun getColors(): List<Color> = emptyList()
}