// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.modcommand.ModDeleteFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import java.util.concurrent.Callable

/** Runs [action] in a read action on a pooled thread, as [ModPlanRefactoring.plan] runs. */
internal fun <T> inBackground(action: () -> T): T {
  return PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(Callable { runReadActionBlocking(action) }))
}

internal fun deletedFiles(acceptedPlan: ModPlan.AcceptedPlan): List<VirtualFile> =
  acceptedPlan.execution.command.unpack().filterIsInstance<ModDeleteFile>().map { it.file() }
