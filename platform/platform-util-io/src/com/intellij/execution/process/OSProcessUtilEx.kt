// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("OSProcessUtilEx")
@file:ApiStatus.Experimental
package com.intellij.execution.process

import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT.PROCESS_QUERY_LIMITED_INFORMATION
import org.jetbrains.annotations.ApiStatus
import java.util.stream.Stream

/**
 * Use this method instead of [ProcessHandle.descendants] and [Process.descendants].
 *
 * However, if you can, prefer [OSProcessUtil.killProcessTree] because it is a bit more reliable.
 *
 * This method tries to mitigate the race condition on Windows
 * when code like `process.descendants().forEach(ProcessHandle::destroy)` kills unrelated processes.
 *
 * This method _may_ be deprecated soon because it looks like it should be fixed in JBR/OpenJDK instead.
 *
 * ----
 *
 * ## Is it about the case when some process dies and a new process is created with the same pid?
 *
 * No, it's another race condition.
 * When a process dies in Unix, all its children are reparented to init (PID 1).
 * Nothing like that happens on Windows. All children keep the parent pid unchanged when the parent dies.
 * Also, Windows does not provide an API for getting some process children. It only provides the way to get the whole process tree.
 *
 * So, [ProcessHandle.descendants] does the following inside:
 * * Get all pairs pid-ppid.
 * * Filters those pids whose ppid is [ProcessHandle.pid].
 *
 * Here's what can happen with the code that uses [ProcessHandle.descendants]:
 * * Some process with pid=100 creates a Very Important Process with pid=101.
 * * The process with pid=100 exits. The VIP still has ppid=100.
 * * Our code creates a process. Windows gives the process pid=100.
 * * Our code calls `process.descendants().forEach(ProcessHandle::destroy)`
 * * `process.descendants()` returns [ProcessHandle] with pid=101 and ppid=100.
 * * The code inside `forEach { it.destroy() }` kills the VIP.
 *
 * ## Is it really so scary? Doesn't it happen once in a blue moon?
 *
 * A rare situation reproduced a thousand times becomes not rare.
 * There was a build configuration with our CI where it appeared every 2-3 build.
 * The joke of the situation was that due to the bug, the code from tests used to kill internal processes of the CI,
 * knocking out the whole build agent for an hour.
 *
 * ## How reliable is this approach?
 *
 * Much more reliable than [ProcessHandle.descendants] on Windows, but still can be race prone.
 *
 * The ideal approach is to create Job objects (something like process groups in Unix) and to destroy jobs.
 * It's not possible with [Process]. Should become possible with [com.intellij.platform.eel.EelApi] later.
 *
 * This code has to use heuristics. We can always assume that a parent is always older than its child and filter out processes
 * not matching the rule. The time resolution is 100ns.
 */
@ApiStatus.Experimental
fun ProcessHandle.realDescendants(): Stream<ProcessHandle> =
  realDescendants(pid(), descendants())

/** Read the docs of [ProcessHandle.realDescendants] */
@ApiStatus.Experimental
fun Process.realDescendants(): Stream<ProcessHandle> =
  realDescendants(pid(), descendants())

@OptIn(LowLevelLocalMachineAccess::class)
private fun realDescendants(pid: Long, descendants: Stream<ProcessHandle>): Stream<ProcessHandle> =
  when (OS.CURRENT) {
    OS.Windows -> {
      val parentCreation =
        getProcessCreationTime(pid)
        ?: return Stream.empty()  // This process is dead.

      descendants.filter { desc ->
        val descCreation =
          getProcessCreationTime(desc.pid())
          ?: return@filter false  // The descendant is dead.

        descCreation >= parentCreation
      }
    }

    OS.macOS, OS.Linux, OS.FreeBSD, OS.Other -> {
      descendants
    }
  }

/** Returns null if the process is dead or unkillable. */
private fun getProcessCreationTime(pid: Long): Long? {
  val handle = Kernel32.INSTANCE.OpenProcess(
    PROCESS_QUERY_LIMITED_INFORMATION, false, pid.toInt()
  )
  if (handle == null) {
    // We could check Kernel32.INSTANCE.GetLastError() here, but we wouldn't change the logic flow anyway.
    // The most usual situation for this branch is code 87, which means that the process is already dead.
    return null
  }
  try {
    val creation = WinBase.FILETIME()
    val exit = WinBase.FILETIME()
    val kernel = WinBase.FILETIME()
    val user = WinBase.FILETIME()
    if (!Kernel32.INSTANCE.GetProcessTimes(handle, creation, exit, kernel, user)) {
      return null
    }
    return (creation.dwHighDateTime.toLong() shl 32) or (creation.dwLowDateTime.toLong() and 0xFFFFFFFFL) // 100ns ticks since 1601-01-01
  }
  finally {
    Kernel32.INSTANCE.CloseHandle(handle)
  }
}
