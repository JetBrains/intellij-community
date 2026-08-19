import com.intellij.mcpserver.annotations.McpDescription;
import com.intellij.mcpserver.annotations.McpTool;

class RemoveProjectPathMiddleParameter {
  @McpTool
  public String doStuff(@McpDescription(description = "the query") String query, @McpDescription(description = "the mode") String mode) {
    return "";
  }
}
