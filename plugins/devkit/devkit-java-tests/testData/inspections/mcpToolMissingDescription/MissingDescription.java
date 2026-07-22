import com.intellij.mcpserver.annotations.McpTool;

class MissingDescription {
  @McpTool
  public String <error descr="@McpTool method 'search' must have @McpDescription">search</error>() {
    return "";
  }
}
