// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.exists

private const val GRACEFUL_STOP_SECONDS = 10L
private const val FORCED_STOP_SECONDS = 5L

/**
 * Frees the disk taken by a test dir. Stops processes run from [dir] (other than this JVM and its ancestors) and their
 * descendants, makes read-only content writable, then deletes [dir] recursively. A missing [dir] is a no-op.
 *
 * Never throws, so a `finally` never hides the test failure. Stopped processes and a failure print `WARN deleteTestDir`
 * lines to stderr.
 */
@TestOnly
@ApiStatus.Internal
fun deleteTestDir(dir: Path) {
  try {
    if (!dir.exists(LinkOption.NOFOLLOW_LINKS)) return
    stopProcesses(dir, processesUnder(dir))
    makeWritable(dir)
    NioFiles.deleteRecursively(dir)
  }
  catch (t: Throwable) {
    rethrowControlFlowException(t)
    System.err.println("WARN deleteTestDir $dir: $t")
  }
}

/** The live processes (other than this JVM and its ancestors) that run an executable located under [dir]. */
private fun processesUnder(dir: Path): List<ProcessHandle> {
  if (!dir.exists(LinkOption.NOFOLLOW_LINKS)) return emptyList()
  val roots = listOf(dir.toAbsolutePath().normalize(), dir.toRealPath()).distinct()
  val self = ProcessHandle.current()
  val excluded = (sequenceOf(self) + generateSequence(self.parent().orElse(null)) { it.parent().orElse(null) })
    .map { it.pid() }
    .toSet()
  return ProcessHandle.allProcesses()
    .filter { it.pid() !in excluded }
    .filter { process ->
      val command = process.info().command().orElse(null) ?: return@filter false
      val executable = NioFiles.toPath(command)?.normalize() ?: return@filter false
      roots.any { executable.startsWith(it) }
    }
    .toList()
}

/** Stops [processes] and their descendants: a leak of the test, so each step prints one `WARN deleteTestDir` line. */
private fun stopProcesses(dir: Path, processes: List<ProcessHandle>) {
  if (processes.isEmpty()) return
  val all = (processes + processes.flatMap { it.descendants().toList() }).distinctBy { it.pid() }
  // an exiting process loses its command, so describe each one before the stop
  val descriptions = all.associate { it.pid() to "${it.pid()} ${it.info().command().orElse("?")}" }
  fun describe(stopped: List<ProcessHandle>) = stopped.joinToString { descriptions.getValue(it.pid()) }
  System.err.println("WARN deleteTestDir $dir: stopping leaked processes: ${describe(all)}")
  all.forEach { it.destroy() }
  val survivors = awaitExit(all, GRACEFUL_STOP_SECONDS)
  if (survivors.isEmpty()) return
  System.err.println("WARN deleteTestDir $dir: killing processes still alive: ${describe(survivors)}")
  survivors.forEach { it.destroyForcibly() }
  awaitExit(survivors, FORCED_STOP_SECONDS)
}

/**
 * Waits up to [seconds] in total and returns the processes that are still alive. An interrupt ends the wait and stays
 * set, so the caller still stops the survivors and deletes the dir.
 */
private fun awaitExit(processes: List<ProcessHandle>, seconds: Long): List<ProcessHandle> {
  val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
  for (process in processes) {
    val left = deadline - System.nanoTime()
    if (left <= 0 || Thread.currentThread().isInterrupted) break
    try {
      process.onExit().get(left, TimeUnit.NANOSECONDS)
    }
    catch (e: TimeoutException) {
      break
    }
    catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      break
    }
  }
  return processes.filter { it.isAlive }
}

/** POSIX: adds owner-write to each dir before visiting it. DOS: clears the read-only flag on dirs and files. */
private fun makeWritable(root: Path) {
  Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      // an NTFS junction is a directory to the walk: don't change the permissions of its target outside the test dir
      if (attrs.isOther) return FileVisitResult.SKIP_SUBTREE
      makeWritableEntry(dir, isDirectory = true)
      return FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      if (!attrs.isSymbolicLink) makeWritableEntry(file, isDirectory = false)
      return FileVisitResult.CONTINUE
    }

    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE

    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult = FileVisitResult.CONTINUE
  })
}

private fun makeWritableEntry(path: Path, isDirectory: Boolean) {
  try {
    val posix = Files.getFileAttributeView(path, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
    if (posix != null) {
      // POSIX deletes need write on the parent dir only
      if (isDirectory) {
        val permissions = posix.readAttributes().permissions()
        if (permissions.add(PosixFilePermission.OWNER_WRITE)) posix.setPermissions(permissions)
      }
      return
    }
    val dos = Files.getFileAttributeView(path, DosFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS) ?: return
    if (dos.readAttributes().isReadOnly) dos.setReadOnly(false)
  }
  catch (e: IOException) {
    // the delete reports what is left
  }
}
