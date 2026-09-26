import com.intellij.mcpserver.annotations.McpDescription;
import com.intellij.mcpserver.annotations.McpTool;

class MissingParameterDescription {
  @McpTool
  @McpDescription(description = "reads a file")
  public String readFile(@McpDescription(description = "the path") String path,
                         int <error descr="@McpTool parameter 'limit' must have @McpDescription">limit</error>,
                         String projectPath) {
    return "";
  }
}
