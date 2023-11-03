// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental

package com.intellij.platform.util.progress

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@Internal // clients are not supposed to put reporter into context
fun ProgressReporter.asContextElement(): CoroutineContext.Element = ProgressReporterElement.Step(this)
val CoroutineContext.progressReporter: ProgressReporter? get() = (this[ProgressReporterElement] as? ProgressReporterElement.Step)?.reporter
val CoroutineScope.progressReporter: ProgressReporter? get() = coroutineContext.progressReporter

@Internal // clients are not supposed to put reporter into context
fun RawProgressReporter.asContextElement(): CoroutineContext.Element = ProgressReporterElement.Raw(this)
val CoroutineContext.rawProgressReporter: RawProgressReporter? get() = (this[ProgressReporterElement] as? ProgressReporterElement.Raw)?.reporter
val CoroutineScope.rawProgressReporter: RawProgressReporter? get() = coroutineContext.rawProgressReporter

private sealed class ProgressReporterElement : AbstractCoroutineContextElement(ProgressReporterElement) {
  companion object : CoroutineContext.Key<ProgressReporterElement>
  class Step(val reporter: ProgressReporter) : ProgressReporterElement()
  class Raw(val reporter: RawProgressReporter) : ProgressReporterElement()
}
