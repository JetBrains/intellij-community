// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations

import com.intellij.execution.process.LocalPtyOptions
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.ThrowsChecked
import com.intellij.platform.eel.convertToJVMProcess
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.spawnProcess
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Executes the [builder] command on [descriptor].
 *
 * ## The contract
 *
 * The caller selects the target and supplies [descriptor]. All command values must be valid in that target.
 * This function never selects another target, and it never falls back to the local machine.
 * A global path is an IDE-visible path that identifies its target. A native path is a path inside that target.
 *
 * The executable accepts an absolute global path on [descriptor], an absolute native path, or a program name.
 * A native executable path is interpreted inside [descriptor]. EEL searches for a program name in the target's PATH.
 * Executable strings without a remote global path are passed unchanged to EEL.
 * If supplied, the working directory must be an absolute global path on [descriptor].
 * The function rejects executable and working directory paths that identify another target.
 *
 * The caller must supply arguments and environment values in the target's native representation.
 * For compatibility, the function converts an absolute global argument path on [descriptor] to a native path.
 * If the conversion changes the argument, the function also reports a warning.
 * An absolute global argument path that identifies another remote target is rejected.
 *
 * A path that NIO identifies as local can also be a native path in the target, such as /tmp/1 for POSIX.
 * The function keeps such arguments unchanged because their strings do not identify a remote target.
 * It also keeps relative paths and other arguments unchanged.
 * Embedded paths, such as --file=<path>, have no guaranteed conversion. Environment values are not converted.
 * The caller must not rely on this compatibility conversion to find every path.
 *
 * ## A broken contract
 *
 * A command line with conflicting targets is a defect in the caller or its input, not a temporary failure of the environment.
 * Retrying the same command cannot make the targets agree. The command construction or its input must change.
 *
 * The function reports a broken contract with an unchecked [IllegalArgumentException].
 * The exception leaves the throws contract of [GeneralCommandLine.createProcess] on purpose.
 * Its message and stack trace identify the invalid command value and the caller that supplied it.
 * This allows callers to report a programming error instead of treating it as an ordinary launch failure.
 * The caller's exception handling determines whether it reaches the IDE error log.
 *
 * Do not convert this exception to a [java.io.IOException].
 * [GeneralCommandLine.createProcess] catches a [java.io.IOException] and wraps it in a
 * [com.intellij.execution.process.ProcessNotCreatedException].
 * That conversion would hide the caller defect behind an ordinary launch failure.
 * A [java.io.IOException] instead reports an execution failure, such as a missing executable, after the command passes these checks.
 *
 * @throws IllegalArgumentException if the working directory is not an absolute global path on [descriptor],
 * or a global executable or argument path identifies another remote target.
 */
@ThrowsChecked(EelExecApi.EnvironmentVariablesException::class)
@RequiresBackgroundThread
internal fun startProcessBlockingUsingEel(
  descriptor: EelDescriptor,
  builder: ProcessBuilder,
  pty: LocalPtyOptions?,
  isPassParentEnvironment: Boolean,
): Process {
  val args = builder.command()
  // A copy, because `builder` can hold a read-only list. The conversion below also must not change `builder`.
  val rest = args.subList(1, args.size).toMutableList()
  val workingDir = builder.directory()?.path?.let { dir ->
    val path = toPath(dir) ?: throw IllegalArgumentException("dir `$dir` is not a valid path")
    toEelPath(path, descriptor, "dir")
  }
  val exe = convertExecutable(args.first(), descriptor)

  // Give each argument the remote representation of a path (see IJPL-232192).
  if (descriptor !== LocalEelDescriptor) {
    for ((i, arg) in rest.withIndex()) {
      val path = toPath(arg) ?: continue
      if (!path.isAbsolute) continue
      val argDescriptor = path.getEelDescriptor()

      // The argument is a nio path on the same eel, so the remote process needs the eel path.
      // On WSL, `\\wsl$\debian\tmp\1` almost always means `/tmp/1` for a remote command.
      if (argDescriptor == descriptor) {
        val nativePath = path.asEelPath().toString()
        if (arg != nativePath) {
          Logger.getInstance(GeneralCommandLine::class.java)
            .warn("Argument '$arg' is not a native path; converting it to '$nativePath'")
          rest[i] = nativePath
        }
      }
      // A local descriptor does not prove a mismatch: /tmp/1 can be a native path in the remote target.
      else if (argDescriptor != LocalEelDescriptor) {
        throw IllegalArgumentException("argument `$arg` does not belong to $descriptor")
      }
    }
  }

  return runBlockingMaybeCancellable {
    val exec = descriptor.toEelApi().exec
    val env = (if (isPassParentEnvironment) exec.environmentVariables().eelIt().await() else emptyMap()) + builder.environment()
    exec.spawnProcess(exe).args(rest).workingDirectory(workingDir).env(env)
      .interactionOptions(pty?.run { EelExecApi.Pty(initialColumns, initialRows, !consoleMode) }).eelIt().convertToJVMProcess()
  }
}

private fun convertExecutable(exe: String, descriptor: EelDescriptor): String {
  val path = toPath(exe) ?: return exe
  if (!path.isAbsolute || path.getEelDescriptor() == LocalEelDescriptor) return exe
  return toEelPath(path, descriptor, "exec").toString()
}

/**
 * Converts [path] to an [EelPath] on [descriptor].
 * [role] names the part of the command line that [path] comes from. It is used in an error message only.
 *
 * @throws IllegalArgumentException if [path] is not an absolute path on [descriptor].
 * See the contract of [startProcessBlockingUsingEel].
 */
private fun toEelPath(path: Path, descriptor: EelDescriptor, role: String): EelPath {
  require(path.isAbsolute) { "$role `$path` must be an absolute path on $descriptor" }
  val eelPath = try {
    path.asEelPath()
  }
  catch (e: EelPathException) {
    throw IllegalArgumentException("$role `$path` is not a path on $descriptor", e)
  }
  require(eelPath.descriptor == descriptor) { "$role `$path` does not belong to $descriptor" }
  return eelPath
}

private fun toPath(@MultiRoutingFileSystemPath string: String): Path? = try {
  Path.of(string)
}
catch (_: InvalidPathException) {
  null
}
