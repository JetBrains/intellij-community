import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool

class RemoveProjectPathParameter {
  @McpTool
  fun doStuff(@McpDescription(description = "the query") query: String): String = ""
}
