public class MyInspectionWithGetShortNameInspection extends com.intellij.codeInspection.LocalInspectionTool {
  @Override
  public String getShortName() {
    return "someSpecificShortName";
  }
}