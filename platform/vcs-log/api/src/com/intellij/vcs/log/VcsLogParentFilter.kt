// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log

interface VcsLogParentFilter : VcsLogFilter {

  val minParents: Int
  val maxParents: Int

  override fun getKey(): VcsLogFilterCollection.FilterKey<*> = VcsLogFilterCollection.PARENT_FILTER
}