import com.intellij.mcpserver.annotations.McpDescription;
import com.intellij.mcpserver.annotations.McpTool;

class RemoveProjectPathMiddleParameter {
  @McpTool
  public String doStuff(@McpDescription(description = "the query") String query, String <error descr="Do not declare 'projectPath'; the MCP framework injects it automatically">projectPath<caret></error>, @McpDescription(description = "the mode") String mode) {
    return "";
  }
}
