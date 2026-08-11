import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool

class RemoveProjectPathParameter {
  @McpTool
  fun doStuff(@McpDescription(description = "the query") query: String, <error descr="Do not declare 'projectPath'; the MCP framework injects it automatically">projectPath<caret></error>: String): String = ""
}
