import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool

class WithDescription {
  @McpTool
  @McpDescription(description = "searches something")
  fun search(): String = ""
}
