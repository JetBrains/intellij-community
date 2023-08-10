// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger

interface MultiVmDebugProcess {
  val mainVm: Vm?
  val activeOrMainVm: Vm?
  val collectVMs: List<Vm>
}
