import com.intellij.codeInspection.LocalInspectionTool;

public class MyWithDescriptionCustomMethodInvokingShortNameInspection extends LocalInspectionTool {

  public String getShortName() {
    return getNameFromMethod();
  }

  private static String getNameFromMethod() {
    return "customShortName";
  }
}