// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tracing.ide

import com.intellij.compiler.server.BuildProcessParametersProvider

internal class TracingBuildProcessParameterProvider : BuildProcessParametersProvider() {
  override fun getVMArguments(): List<String> {
    if (TracingService.getInstance().isTracingEnabled()) {
      val path = TracingService.createPath(TracingService.TraceKind.Jps)
      TracingService.getInstance().registerJpsTrace(path)
      return listOf("-DtracingFile=$path")
    }
    return emptyList()
  }
}