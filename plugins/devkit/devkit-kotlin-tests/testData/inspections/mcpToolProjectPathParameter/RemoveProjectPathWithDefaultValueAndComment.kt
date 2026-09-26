import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool

class RemoveProjectPathWithDefaultValueAndComment {
  @McpTool
  fun doStuff(
    @McpDescription(description = "the query")
    query: String, // query, may contain comma
    <error descr="Do not declare 'projectPath'; the MCP framework injects it automatically">projectPath<caret></error>: String = "a,b",
    @McpDescription(description = "the mode")
    mode: String,
  ): String = ""
}
