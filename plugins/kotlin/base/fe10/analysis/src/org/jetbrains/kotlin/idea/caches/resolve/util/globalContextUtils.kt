// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve.util

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.context.GlobalContextImpl
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.useCompositeAnalysis
import org.jetbrains.kotlin.idea.base.projectStructure.libraryToSourceAnalysis.useLibraryToSourceAnalysis
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus
import org.jetbrains.kotlin.storage.ExceptionTracker
import org.jetbrains.kotlin.storage.LockBasedStorageManager

private fun GlobalContextImpl.contextWithCompositeExceptionTracker(debugName: String): GlobalContextImpl {
    val newExceptionTracker = CompositeExceptionTracker(this.exceptionTracker)
    return GlobalContextImpl(
        storageManager.replaceExceptionHandling(debugName, newExceptionTracker),
        newExceptionTracker
    )
}

private fun GlobalContextImpl.contextWithNewLockAndCompositeExceptionTracker(debugName: String): GlobalContextImpl {
    val newExceptionTracker = CompositeExceptionTracker(this.exceptionTracker)
    return GlobalContextImpl(
        LockBasedStorageManager.createWithExceptionHandling(debugName, newExceptionTracker, {
            ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()
        }, { throw ProcessCanceledException(it) }),
        newExceptionTracker
    )
}

internal fun GlobalContextImpl.contextWithCompositeExceptionTracker(project: Project, debugName: String): GlobalContextImpl =
    if (project.useCompositeAnalysis || project.useLibraryToSourceAnalysis) {
        this.contextWithCompositeExceptionTracker(debugName)
    } else {
        this.contextWithNewLockAndCompositeExceptionTracker(debugName)
    }

private class CompositeExceptionTracker(val delegate: ExceptionTracker) : ExceptionTracker() {
    override fun getModificationCount(): Long {
        return super.getModificationCount() + delegate.modificationCount
    }
}
