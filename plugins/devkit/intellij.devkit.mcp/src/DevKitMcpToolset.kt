// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.idea.devkit.threadingModelHelper.AnalysisConfig
import org.jetbrains.idea.devkit.threadingModelHelper.ConstraintType
import org.jetbrains.idea.devkit.threadingModelHelper.ExecutionPath
import org.jetbrains.idea.devkit.threadingModelHelper.LOCK_REQUIREMENTS
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqAnalyzerParallelBFS
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqConsumer
import java.util.Collections
import kotlin.time.Duration.Companion.milliseconds

class DevKitMcpToolset : McpToolset {

  @McpTool
  @McpDescription("""
        |Analyzes the usage of Read/Write lock for the method under the caret.
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

    val list = Collections.synchronizedList(mutableListOf<LockRequirementUsage>())

    val result = withTimeoutOrNull(timeout.milliseconds) {
      LockReqAnalyzerParallelBFS().analyzeMethodStreaming(pointer, AnalysisConfig.forProject(project, LOCK_REQUIREMENTS), project, object : LockReqConsumer {
        override fun onPath(path: ExecutionPath) {
          val lockType = when (path.lockRequirement.constraintType) {
            ConstraintType.READ -> LockType.READ_ASSERTION
            ConstraintType.WRITE -> LockType.WRITE_ASSERTION
            ConstraintType.WRITE_INTENT -> LockType.WRITE_INTENT_ASSERTION
            ConstraintType.NO_READ -> LockType.NO_READ_ASSERTION
            ConstraintType.EDT, ConstraintType.BGT -> return // irrelevant
          }
          val locations = path.methodChain.map {
            SourceLocation(it.containingClassName ?: "<null>", it.methodName)
          }
          list.add(LockRequirementUsage(lockType, locations))
        }
      })
    }
    val timeout = result == null
    return LockRequirements(list, timeout)
  }

  enum class LockType {
     READ_ASSERTION, WRITE_ASSERTION, WRITE_INTENT_ASSERTION, NO_READ_ASSERTION
   }

  @Serializable
  data class SourceLocation(val className: String, val methodName: String)

  @Serializable
  data class LockRequirementUsage(val type: LockType, val callPath: List<SourceLocation>)

  @Serializable
  data class LockRequirements(
    val reqs: List<LockRequirementUsage>,
    val timedOut: Boolean,
  )
}