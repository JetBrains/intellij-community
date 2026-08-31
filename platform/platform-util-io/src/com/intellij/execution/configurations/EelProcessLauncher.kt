// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations

import com.intellij.execution.process.LocalPtyOptions
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.ThrowsChecked
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
 * Each part of the command line must belong to one eel, and that eel must be [descriptor].
 * The caller selects the eel, so the caller must also resolve each path against it.
 * This function never guesses a path, and it never falls back to the local machine.
 *
 * The first argument of [builder] must be an absolute path to an executable file on [descriptor].
 * A binary name alone is not accepted. The caller must resolve the name against [descriptor] first.
 * The working directory must also be a path on [descriptor].
 *
 * An argument that holds the local representation of a path on [descriptor] gets the [descriptor] representation.
 * The function keeps a local path that [descriptor] can also use, such as `/tmp/1` for a Posix eel.
 * The function keeps each argument that is not an absolute path.
 *
 * ## A broken contract
 *
 * A command line that mixes 2 eels is a defect in the caller. It is not a state of the environment.
 * A caller cannot recover from it, because no retry and no other input make the paths agree.
 * Only a person can repair the caller.
 *
 * Therefore the function reports a broken contract with an unchecked [IllegalArgumentException].
 * The exception leaves the `throws` contract of [GeneralCommandLine.createProcess] on purpose.
 * It reaches the IDE error log, and the log names the caller that must change.
 *
 * Do not convert this exception to a [java.io.IOException].
 * [GeneralCommandLine.createProcess] catches a [java.io.IOException] and reports it as a
 * [com.intellij.execution.process.ProcessNotCreatedException].
 * The caller then treats the defect as an ordinary start failure, and the true cause stays hidden.
 * A [java.io.IOException] has a different meaning here. It says that [descriptor] refused a correct
 * command line, for example because the executable file is absent.
 *
 * @throws IllegalArgumentException if the executable file is not an absolute path on [descriptor],
 * if the working directory is not a path on [descriptor],
 * or if an argument is an absolute path that [descriptor] cannot use.
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
  val exe = toEelPath(args.first(), descriptor, "exec")
  // A copy, because `builder` can hold a read-only list. The conversion below also must not change `builder`.
  val rest = args.subList(1, args.size).toMutableList()
  val workingDir = builder.directory()?.path?.let { toEelPath(it, descriptor, "dir") }

  // Give each argument the remote representation of a path (see IJPL-232192).
  if (descriptor !== LocalEelDescriptor) {
    for ((i, arg) in rest.withIndex()) {
      val path = toPath(arg) ?: continue
      if (!path.isAbsolute) continue
      val argDescriptor = path.getEelDescriptor()

      // The argument is a nio path on the same eel, so the remote process needs the eel path.
      // On WSL, `\\wsl$\debian\tmp\1` almost always means `/tmp/1` for a remote command.
      if (argDescriptor == descriptor) {
        rest[i] = path.asEelPath().toString()
      }
      else if (argDescriptor != LocalEelDescriptor) {
        // This is another eel, hence error
        // We can't check local eel as local eels have no prefix, and "/a/b" is perectly valid argument for remote eel
        throw IllegalArgumentException("argument `$arg` does not belong to $descriptor")
      }
    }
  }

  return runBlockingMaybeCancellable {
    val exec = descriptor.toEelApi().exec
    val env = (if (isPassParentEnvironment) exec.environmentVariables().eelIt().await() else emptyMap()) + builder.environment()
    exec.spawnProcess(exe).args(rest).workingDirectory(workingDir).env(env)
      .interactionOptions(pty?.run { EelExecApi.Pty(initialColumns, initialRows, !consoleMode) }).eelIt().convertToJavaProcess()
  }
}

/**
 * Converts [arg] to an [EelPath] on [descriptor].
 * [role] names the part of the command line that [arg] comes from. It is used in an error message only.
 *
 * @throws IllegalArgumentException if [arg] is not an absolute path on [descriptor].
 * See the contract of [startProcessBlockingUsingEel].
 */
private fun toEelPath(arg: String, descriptor: EelDescriptor, role: String): EelPath {
  val path = toPath(arg) ?: throw IllegalArgumentException("$role `$arg` is not a valid path")
  require(path.isAbsolute) { "$role `$arg` must be an absolute path on $descriptor" }
  val eelPath = try {
    path.asEelPath()
  }
  catch (e: EelPathException) {
    throw IllegalArgumentException("$role `$arg` is not a path on $descriptor", e)
  }
  require(eelPath.descriptor == descriptor) { "$role `$arg` does not belong to $descriptor" }
  return eelPath
}

private fun toPath(string: String): Path? = try {
  Path.of(string)
}
catch (_: InvalidPathException) {
  null
}
