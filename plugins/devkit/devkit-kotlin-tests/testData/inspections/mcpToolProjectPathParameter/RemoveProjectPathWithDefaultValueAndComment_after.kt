import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool

class RemoveProjectPathWithDefaultValueAndComment {
  @McpTool
  fun doStuff(
    @McpDescription(description = "the query")
    query: String, // query, may contain comma
    @McpDescription(description = "the mode")
    mode: String,
  ): String = ""
}
