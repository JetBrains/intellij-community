// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.CoroutineDumpStripTest.JobTree.Companion.dump
import com.intellij.diagnostic.CoroutineDumpStripTest.JobTree.Companion.parseAsJobTree
import com.intellij.util.io.URLUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.StringWriter
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readLines
import kotlin.io.path.relativeTo
import kotlin.io.path.toPath

class CoroutineDumpStripTest {
  @TestFactory
  fun generateTests(): List<DynamicTest> {
    val resource = this.javaClass.classLoader.getResource("coroutine-dump-strip")!!
    val uri = resource.toURI()
    if (uri.scheme == URLUtil.JAR_PROTOCOL) {
      try {
        FileSystems.newFileSystem(uri, emptyMap<String, Any>())
      }
      catch (_: FileSystemAlreadyExistsException) {
      }
    }
    val dataPath: Path = uri.toPath()
    return dataPath.listDirectoryEntries().map {
      DynamicTest.dynamicTest(it.relativeTo(dataPath).toString()) {
        runScenario(it)
      }
    }
  }

  private fun runScenario(path: Path) {
    val scenarioData = path.readLines().filter { it.isNotBlank() }.map { it.replace("\t", "  ") }
    val jobTree = scenarioData.parseAsJobTree()

    val expected = jobTree.transformTraces { it.filter { !it.omitMark }.map { it.element } }
    val stripped = jobTree.transformTraces { stripCoroutineTrace(it.map { it.element }) }

    assertEquals(expected.dump(), stripped.dump())
  }

  private data class AnnotatedTraceElement(val element: StackTraceElement, val omitMark: Boolean)
  private data class JobTree<TraceElem>(val descriptor: String, val trace: List<TraceElem>, val children: List<JobTree<TraceElem>>) {
    fun <R> transformTraces(transform: (List<TraceElem>) -> List<R>): JobTree<R> =
      JobTree(descriptor, transform(trace), children.map { it.transformTraces(transform) }.toList())

    companion object {
      fun List<String>.parseAsJobTree(): JobTree<AnnotatedTraceElement> {
        if (isEmpty()) throw IllegalStateException("illegal job tree dump:\n${joinToString("\n")}")
        val descriptor = first().trim().removePrefix("- ")
        val trace = mutableListOf<AnnotatedTraceElement>()
        val children = mutableListOf<JobTree<AnnotatedTraceElement>>()
        var iter = 1
        while (iter < size) {
          val s = this[iter]
          if (!(s.startsWith("#") || s.trim().startsWith("at "))) break
          trace.add(s.parseAsStacktraceElement())
          iter++
        }
        val rootIndent = first().jobIndent()
        while (iter < size) {
          val childJobStart = iter
          var childJobEnd = iter + 1
          while (childJobEnd < size && !this[childJobEnd].startsWith("  ".repeat(rootIndent + 1) + "- "))
            childJobEnd++
          children.add(subList(childJobStart, childJobEnd).parseAsJobTree())
          iter = childJobEnd
        }
        return JobTree(descriptor, trace, children)
      }

      private fun String.jobIndent(): Int {
        return (length - trim().length) / 2
      }

      private fun String.parseAsStacktraceElement(): AnnotatedTraceElement {
        // "[#][indent]at kotlinx.coroutines.flow.FlowKt__ChannelsKt.emitAllImpl$FlowKt__ChannelsKt(Channels.kt:51)"
        val omitMark = startsWith("#")
        val frameDesc = removePrefix("#").trimStart().removePrefix("at ")
        val bracketPos = frameDesc.indexOf("(")
        if (bracketPos == -1) throw UnsupportedOperationException("improve parsing please :)")
        val tokens = frameDesc.substring(0, bracketPos).split('.')
        assert(tokens.size > 1)
        return AnnotatedTraceElement(
          StackTraceElement(tokens.subList(0, tokens.size - 1).joinToString("."),
                            tokens.last(),
                            "",
                            -1),
          omitMark
        )
      }

      fun JobTree<StackTraceElement>.dump(): String {
        fun StringWriter.indent(level: Int) {
          write("  ".repeat(level))
        }

        fun JobTree<StackTraceElement>.dump(writer: StringWriter, level: Int = 0) {
          writer.indent(level)
          writer.write("- $descriptor\n")
          for (elem in trace) {
            writer.indent(level + 1)
            writer.write(elem.toString())
            writer.write("\n")
          }
          for (task in children) {
            task.dump(writer, level + 1)
          }
        }

        return StringWriter().let {
          this.dump(it, 0)
          it.toString()
        }
      }
    }
  }
}