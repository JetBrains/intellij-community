import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool

class MissingParameterDescription {
  @McpTool
  @McpDescription(description = "reads a file")
  fun readFile(@McpDescription(description = "the path") path: String,
               <error descr="@McpTool parameter 'limit' must have @McpDescription">limit</error>: Int,
               projectPath: String): String = ""
}
