import com.intellij.mcpserver.annotations.McpTool;

class ProjectPathParameter {
  @McpTool
  public String doStuff(String <error descr="Do not declare 'projectPath'; the MCP framework injects it automatically">projectPath</error>) {
    return "";
  }
}
