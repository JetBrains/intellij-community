// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testServices

import org.junit.runner.Description
import java.time.Instant

/**
 * Tracks if some Gradle Daemons expired/died during the test or between
 * tests
 */
object GradleDaemonWatchdogService : KotlinBeforeAfterTestRuleWithDescription {
    private val GRADLE_DAEMON_PROCESS_NAME = "GradleDaemon"
    private var atLeastOneTestStarted: Boolean = false
    private var atLeastOneTestFinished: Boolean = false

    override fun before(description: Description) {
        performDaemonsHealthcheck("Test ${description.displayName} starting")
        atLeastOneTestStarted = true
    }

    override fun after(description: Description) {
        performDaemonsHealthcheck("Test ${description.displayName} finished")
        atLeastOneTestFinished = true
    }

    private fun performDaemonsHealthcheck(testName: String) {
        val diff = JavaProcessesTracker.takeProcessesDiff(testName)

        when {
            // This is the very first test in the whole VM, no Gradle Daemons expected to be spawned
            !atLeastOneTestStarted -> return

            atLeastOneTestStarted && !atLeastOneTestFinished -> {
                // The very first test finished, it's OK to have a new Gradle Daemon spawned
                // (this one is expected to persist throughtout the whole test run)
                val gradleDaemonsChanges = diff.onlyGradleDaemons()

                if (gradleDaemonsChanges.size != 1 || gradleDaemonsChanges.entries.single().value != ProcessesSnapshotsDiff.Diff.SPAWNED) {
                    alertAboutDaemonsMisbehaving("Expected to have exactly one Gradle Daemon spawned after first test", diff)
                }
            }

            else -> {
                // any subsequent test runs (either starting test or finishing test, doesn't matter)
                // no changes in GradleDaemons are expected
                val gradleDaemonsChanges = diff.onlyGradleDaemons()

                if (gradleDaemonsChanges.isNotEmpty()) {
                    alertAboutDaemonsMisbehaving("Expected to have no changes in running Gradle Daemons", diff)
                }
            }
        }
    }

    private fun ProcessesSnapshotsDiff.onlyGradleDaemons(): Map<Process, ProcessesSnapshotsDiff.Diff> =
        processesToDiffs.filter { (process, diff) ->
            process.name.contains(GRADLE_DAEMON_PROCESS_NAME)
        }

    private fun alertAboutDaemonsMisbehaving(message: String, diff: ProcessesSnapshotsDiff) {
        KotlinMppTestsAlerter.getInstance().alert(
            """
            |$message
            |
            |Actual diff:
            |${diff.render()}
            |
            |Full last snapshot:
            |${diff.firstSnapshot.render()}
            |
            |Full current snapshot:
            |${diff.secondSnapshot.render()}
            """.trimMargin()
        )
    }
}

private object JavaProcessesTracker {
    private var lastSnapshot: ProcessesSnapshot = ProcessesSnapshot("Initialized", Instant.now(), emptyMap())

    fun takeProcessesDiff(eventTag: String): ProcessesSnapshotsDiff {
        val currentSnapshot = takeDaemonsSnapshot(eventTag)
        val diff = computeSnapshotsDiff(lastSnapshot, currentSnapshot)
        lastSnapshot = currentSnapshot
        return diff
    }

    private fun takeDaemonsSnapshot(eventTag: String): ProcessesSnapshot {
        val jpsOutputLines = runShellCommandAndGrabOutput("jps -V")
        val processesById = mutableMapOf<Int, Process>()
        for (line in jpsOutputLines) {
            val process = parseProcess(line) ?: continue
            if (process.name != "Jps") processesById[process.pid] = process
        }

        return ProcessesSnapshot(eventTag, Instant.now(), processesById)
    }

    private fun computeSnapshotsDiff(first: ProcessesSnapshot, second: ProcessesSnapshot): ProcessesSnapshotsDiff {
        val newProcesses =
            (second.processesById.keys - first.processesById.keys)
                .associate { second.processesById[it]!! to ProcessesSnapshotsDiff.Diff.SPAWNED }
        val killedProcesses =
            (first.processesById.keys - second.processesById.keys)
                .associate { first.processesById[it]!! to ProcessesSnapshotsDiff.Diff.KILLED }

        return ProcessesSnapshotsDiff(first, second, newProcesses + killedProcesses)
    }

    private fun runShellCommandAndGrabOutput(command: String): List<String> {
        val process = Runtime.getRuntime().exec(command)
        return process.inputStream.reader().readLines()
    }

    private fun parseProcess(line: String): Process? {
        val words = line.split(" ")
        val pid = words.getOrNull(0)?.toIntOrNull() ?: return null
        val processName = words.getOrNull(1) ?: "<UNKNOWN>"
        return Process(pid, processName)
    }
}

private data class Process(val pid: Int, val name: String) {
    override fun toString(): String = "$pid $name"
}

private data class ProcessesSnapshot(val eventTag: String, val timestamp: Instant, val processesById: Map<Int, Process>) {
    fun render(): String {
        val dateFormatted = timestamp.toString().replace("T", " ").replace("Z", "")
        return "# [$dateFormatted] $eventTag\n" +
                "${processesById.entries.joinToString(separator = "\n")}"
    }
}

private data class ProcessesSnapshotsDiff(
    val firstSnapshot: ProcessesSnapshot,
    val secondSnapshot: ProcessesSnapshot,
    val processesToDiffs: Map<Process, Diff>
) {
    enum class Diff(val symbol: String) {
        SPAWNED("+"),
        KILLED("-")
    }

    fun render(): String {
        if (processesToDiffs.isEmpty()) return "<no change>"
        return "Diff ${firstSnapshot.eventTag} -> ${secondSnapshot.eventTag}\n" +
                processesToDiffs.entries.joinToString(separator = "\n") { (process, diff) ->
                    diff.symbol + " " + process.pid + " " + process.name
                }
    }
}
