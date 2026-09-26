import com.intellij.mcpserver.annotations.McpDescription;
import com.intellij.mcpserver.annotations.McpTool;

class RemoveProjectPathParameter {
  @McpTool
  public String doStuff(@McpDescription(description = "the query") String query, String <error descr="Do not declare 'projectPath'; the MCP framework injects it automatically">projectPath<caret></error>) {
    return "";
  }
}
