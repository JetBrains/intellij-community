// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.application.PathManager
import com.pty4j.PtyProcess
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Tells why JNA could not load its dispatch library.
 *
 * JNA catches the [UnsatisfiedLinkError] of `System.load` for each file in `jna.boot.library.path`.
 * It then reports only "Unable to locate JNA native support library", and the cause is lost.
 * The message holds the `jna.*` properties and the class loaders.
 * It also holds the files of each boot path directory and the error of one more `System.load`.
 * The exception has no stack trace, because it is a suppressed exception of the failure that it explains.
 */
internal class JnaLoadFailureDiagnostic(message: String) : RuntimeException(message, null, false, false)

/**
 * Adds a [JnaLoadFailureDiagnostic] to [error] as a suppressed exception, when the cause chain of [error] holds a failed JNA init.
 *
 * A failed init of `com.sun.jna.Native` is permanent for its class loader.
 * So the second `System.load` of the diagnostic cannot break a JNA that still works.
 * Any other failure gets no diagnostic.
 * The default computes the diagnostic once per process, because the second `System.load` must run only once.
 */
internal fun addJnaLoadFailureDiagnostic(error: Throwable, diagnostic: () -> JnaLoadFailureDiagnostic = { processDiagnostic }) {
  if (generateSequence(error) { it.cause }.take(16).none(::isJnaInitFailure)) {
    return
  }
  if (error.suppressed.none { it is JnaLoadFailureDiagnostic }) {
    error.addSuppressed(diagnostic())
  }
}

/**
 * The first failure of the static initializer of `com.sun.jna.Native` is an [UnsatisfiedLinkError] about the dispatch library.
 * A later use of the class gets a [NoClassDefFoundError] that names the class.
 */
private fun isJnaInitFailure(error: Throwable): Boolean {
  val message = error.message ?: return false
  return when (error) {
    is UnsatisfiedLinkError -> message.startsWith("Unable to locate JNA native support library") || message.contains("jnidispatch")
    is NoClassDefFoundError -> message.endsWith("class com.sun.jna.Native")
    else -> false
  }
}

private val processDiagnostic: JnaLoadFailureDiagnostic by lazy {
  val message = try {
    val text = describeJnaLoadFailure(property = System::getProperty, load = System::load) + describeClassLoaders()
    hideUserPaths(text, PathManager.getHomePath(), System.getProperty("user.home"))
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    "JNA load diagnostic failed: $e"
  }
  JnaLoadFailureDiagnostic(message)
}

private fun describeClassLoaders(): String {
  val jnaClassLoader = try {
    Class.forName("com.sun.jna.Native", false, PtyProcess::class.java.classLoader).classLoader.toString()
  }
  catch (e: ClassNotFoundException) {
    e.toString()
  }
  catch (e: LinkageError) {
    e.toString()
  }
  return "\n  class loader of com.sun.jna.Native: $jnaClassLoader" +
         "\n  class loader of the second System.load: ${JnaLoadFailureDiagnostic::class.java.classLoader}"
}

/**
 * Replaces [ideHome] with `$IDE_HOME` and [userHome] with `~`, so that a report does not show the user name.
 * Only a path prefix is replaced, and a root directory is never replaced.
 */
internal fun hideUserPaths(text: String, ideHome: String?, userHome: String?): String {
  var result = text
  for ((home, replacement) in listOf(ideHome to "\$IDE_HOME", userHome to "~")) {
    val prefix = home?.trimEnd('/', '\\') ?: continue
    if (prefix.length > 1) {
      result = result.replace(prefix + File.separatorChar, replacement + File.separatorChar)
    }
  }
  return result
}

/**
 * Describes the JNA dispatch library load: the `jna.*` properties, the files in each boot path directory, and the result
 * of [load] for the first dispatch library file.
 *
 * The function reads the properties at the time of the diagnostic, not at the time of the JNA init.
 * JNA sets `jnidispatch.path` before each `System.load`. So `jnidispatch.path=null` means that JNA found no file.
 * The file names and the existence check are the ones that JNA uses: [fileName] first, then the `.dylib` name on macOS.
 */
internal fun describeJnaLoadFailure(
  property: (String) -> String?,
  load: (String) -> Unit,
  fileName: String = System.mapLibraryName(property("jna.boot.library.name") ?: "jnidispatch").replace(".dylib", ".jnilib"),
): String {
  val result = StringBuilder("JNA load diagnostic:")
  for (name in listOf("jna.boot.library.path", "jna.boot.library.name", "jna.nosys", "jna.noclasspath", "jna.nounpack", "jnidispatch.path")) {
    result.append("\n  ").append(name).append('=').append(property(name))
  }

  val bootPath = property("jna.boot.library.path")
  if (bootPath.isNullOrEmpty()) {
    return result.toString()
  }

  var loadTried = false
  for (directoryName in bootPath.split(File.pathSeparatorChar).filter { it.isNotEmpty() }) {
    val directory = try {
      Path.of(directoryName)
    }
    catch (e: InvalidPathException) {
      result.append("\n  directory ").append(directoryName).append(": invalid path (").append(e.message).append(')')
      continue
    }

    result.append("\n  directory ").append(directory).append(": ")
    if (!Files.isDirectory(directory)) {
      result.append(if (Files.exists(directory)) "not a directory" else "does not exist")
      continue
    }
    try {
      result.append(directory.listDirectoryEntries().map { it.name }.sorted())
    }
    catch (e: IOException) {
      result.append("cannot be listed (").append(e).append(')')
      continue
    }

    for (candidate in listOf(fileName, fileName.replace(".jnilib", ".dylib")).distinct()) {
      val file = directory.resolve(candidate)
      if (!Files.exists(file)) {
        continue
      }

      result.append("\n  file ").append(file).append(": ")
      try {
        result.append("size=").append(Files.size(file))
      }
      catch (e: IOException) {
        result.append("size unknown (").append(e).append(')')
      }
      if (file.isSymbolicLink()) {
        result.append(", symbolic link")
      }
      if (!Files.isReadable(file)) {
        result.append(", not readable")
      }
      if (!loadTried) {
        loadTried = true
        result.append(", System.load: ")
        try {
          load(file.toString())
          result.append("loaded")
        }
        catch (e: UnsatisfiedLinkError) {
          result.append(e)
        }
        catch (e: SecurityException) {
          result.append(e)
        }
      }
    }
  }
  if (!loadTried) {
    result.append("\n  no ").append(fileName).append(" in the boot path")
  }
  return result.toString()
}
