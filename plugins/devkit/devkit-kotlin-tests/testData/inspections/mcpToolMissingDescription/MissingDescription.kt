import com.intellij.mcpserver.annotations.McpTool

class MissingDescription {
  @McpTool
  fun <error descr="@McpTool method 'search' must have @McpDescription">search</error>(): String = ""
}
