import com.intellij.mcpserver.annotations.McpTool

class ProjectPathParameter {
  @McpTool
  fun doStuff(<error descr="Do not declare 'projectPath'; the MCP framework injects it automatically">projectPath</error>: String): String = ""
}
