// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.util

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change

internal fun interface ChangeDiffRequestProducerFactory {
  fun create(project: Project?, change: Change): DiffRequestProducer?
}
