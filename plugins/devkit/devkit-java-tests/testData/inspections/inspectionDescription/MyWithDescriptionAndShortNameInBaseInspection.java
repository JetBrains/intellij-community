import com.intellij.codeInspection.LocalInspectionTool;

class ALocalInspectionTool extends LocalInspectionTool {
  public String getShortName() {
    final Class aClass = getClass();
    final String name = aClass.getSimpleName();
    return com.intellij.codeInspection.InspectionProfileEntry.getShortName(name);
  }
}
public class <warning descr="Inspection does not have a description">MyWithDescriptionAndShortNameInBaseInspection</warning> extends ALocalInspectionTool {}