import com.intellij.codeInspection.LocalInspectionTool;

public class MyWithDescriptionCustomConstantShortNameInspection extends LocalInspectionTool {
  private static final String SHORT_NAME = "customShortName";

  public String getShortName() {
    return SHORT_NAME;
  }
}
