import com.intellij.codeInspection.LocalInspectionTool;

public class MyWithDescriptionCustomShortNameInspection extends LocalInspectionTool {

  public String getShortName() {
    return "customShortName";
  }
}