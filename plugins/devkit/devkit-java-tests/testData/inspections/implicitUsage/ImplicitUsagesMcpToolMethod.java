import com.intellij.mcpserver.McpToolset;
import com.intellij.mcpserver.annotations.McpDescription;
import com.intellij.mcpserver.annotations.McpTool;

public class ImplicitUsagesMcpToolMethod implements McpToolset {

  public static void main() {} // suppress class unused

  @McpTool
  public void my_tool() {}

  @McpTool
  public String tool_with_return() { return null; }

  @McpTool
  public void tool_with_param(@McpDescription("The parameter") String param) {}


  // invalid ===========

  public void <warning descr="Method 'notAnnotated()' is never used">notAnnotated</warning>() {}
}
