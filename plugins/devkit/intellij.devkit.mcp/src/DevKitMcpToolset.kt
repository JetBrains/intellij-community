// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.mcp

import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.parentOfType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.jetbrains.idea.devkit.threadingModelHelper.*
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

class DevKitMcpToolset : McpToolset {

  @McpTool
  @McpDescription("""
        |Analyzes the usage of the Read/Write lock for the method under the caret.
        |Also analyzes call paths to some depth.
        |Use this tool to identify possible usages of Read/Write lock requirements.
        |Returns a list of lock requirements with the call path to them.
        |Important: the information is neither complete nor reliable: this is merely a heuristic. Each returned call path may not be reachable,
        |and there could be undetected call paths.
        |Note: Only analyzes files within the project directory.
        |Note: Lines and Columns are 1-based.
    """)
  @Suppress("unused", "FunctionName")
  suspend fun find_lock_requirements_usages(
    @McpDescription(Constants.RELATIVE_PATH_IN_PROJECT_DESCRIPTION)
    filePath: String,
    @McpDescription("Line where cursor is located")
    line: Int,
    @McpDescription("Column where cursor is located")
    column: Int,
    @McpDescription(Constants.TIMEOUT_MILLISECONDS_DESCRIPTION)
    timeout: Int = Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE,
  ): LockRequirements {
    val (reqs, timeout) = runSourceRequirementSearch(filePath, line, column, timeout, LOCK_REQUIREMENTS) {
      when (it) {
        ConstraintType.READ -> LockType.READ_ASSERTION
        ConstraintType.WRITE -> LockType.WRITE_ASSERTION
        ConstraintType.WRITE_INTENT -> LockType.WRITE_INTENT_ASSERTION
        ConstraintType.NO_READ -> LockType.NO_READ_ASSERTION
        ConstraintType.EDT, ConstraintType.BGT -> error("Should not appear")
      }
    }
    return LockRequirements(reqs.map { LockRequirementUsage(it.first, it.second) }, timeout)
  }

  @McpTool
  @McpDescription("""
        |Analyzes the usage of threading constraints (i.e., whether the method needs to run on the UI thread or on the background thread) for the method under the caret.
        |Also analyzes call paths to some depth.
        |Use this tool to identify possible usages of threading requirements.
        |Returns a list of threading requirements with the call path to them.
        |Important: the information is neither complete nor reliable: this is merely a heuristic. Each returned call path may not be reachable,
        |and there could be undetected call paths.
        |Note: Only analyzes files within the project directory.
        |Note: Lines and Columns are 1-based.
    """)
  @Suppress("unused", "FunctionName")
  suspend fun find_threading_requirements_usages(
    @McpDescription(Constants.RELATIVE_PATH_IN_PROJECT_DESCRIPTION)
    filePath: String,
    @McpDescription("Line where cursor is located")
    line: Int,
    @McpDescription("Column where cursor is located")
    column: Int,
    @McpDescription(Constants.TIMEOUT_MILLISECONDS_DESCRIPTION)
    timeout: Int = Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE,
  ): ThreadingRequirements {
    val (reqs, timeout) = runSourceRequirementSearch(filePath, line, column, timeout, THREAD_REQUIREMENTS) {
      when (it) {
        ConstraintType.EDT -> ThreadType.UI_THREAD
        ConstraintType.BGT -> ThreadType.BACKGROUND_THREAD
        ConstraintType.READ, ConstraintType.WRITE, ConstraintType.WRITE_INTENT, ConstraintType.NO_READ -> error("should not appear")
      }
    }
    return ThreadingRequirements(reqs.map { ThreadingRequirementUsage(it.first, it.second) }, timeout)
  }

  private suspend fun <T> runSourceRequirementSearch(
    filePath: String,
    line: Int,
    column: Int,
    timeout: Int,
    requirementSet: EnumSet<ConstraintType>,
    mapper: (ConstraintType) -> T,
  ): Pair<List<Pair<T, List<SourceLocation>>>, Boolean> {
    val project = currentCoroutineContext().project
    val file = VirtualFileManager.getInstance().findFileByNioPath(project.resolveInProject(filePath)) ?: throw McpExpectedError("Virtual file not found")
    val document = FileDocumentManager.getInstance().getDocument(file)
    val pointer = readAction {
      val psiTree = PsiManager.getInstance(project).findFile(file) ?: throw McpExpectedError("PsiFile not found")
      val offset = document?.getLineStartOffset(line - 1)?.plus(column - 1) ?: throw McpExpectedError("Invalid line/column")
      val element = psiTree.findElementAt(offset) ?: throw McpExpectedError("Element not found at the specified position")
      val method = element.parentOfType<PsiMethod>() ?: throw McpExpectedError("No method found at the specified position")
      SmartPointerManager.createPointer(method)
    }

    val list = Collections.synchronizedList(mutableListOf<Pair<T, List<SourceLocation>>>())

    val result = withTimeoutOrNull(timeout.milliseconds) {
      LockReqAnalyzerParallelBFS().analyzeMethodStreaming(pointer, AnalysisConfig.forProject(project, requirementSet), project, object : LockReqConsumer {
        override fun onPath(path: ExecutionPath) {
          val lockType = mapper(path.lockRequirement.constraintType)
          val locations = path.methodChain.map {
            SourceLocation(it.containingClassName ?: "<null>", it.methodName)
          }
          list.add(lockType to locations)
        }
      })
    }
    val timeout = result == null
    return list to timeout
  }

  enum class LockType {
     READ_ASSERTION, WRITE_ASSERTION, WRITE_INTENT_ASSERTION, NO_READ_ASSERTION
  }

  enum class ThreadType {
    UI_THREAD, BACKGROUND_THREAD
  }

  @Serializable
  data class SourceLocation(val className: String, val methodName: String)

  @Serializable
  data class LockRequirementUsage(val type: LockType, val callPath: List<SourceLocation>)

  @Serializable
  data class ThreadingRequirementUsage(val type: ThreadType, val callPath: List<SourceLocation>)

  @Serializable
  data class LockRequirements(
    val reqs: List<LockRequirementUsage>,
    val timedOut: Boolean,
  )

  @Serializable
  data class ThreadingRequirements(
    val reqs: List<ThreadingRequirementUsage>,
    val timedOut: Boolean,
  )
}