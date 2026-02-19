import com.intellij.codeInspection.LocalInspectionTool;

@SuppressWarnings("InspectionDescriptionNotFoundInspection") // not relevant for this scenario
class ALocalInspectionTool extends LocalInspectionTool {
  public String getShortName() {
    final Class aClass = getClass();
    final String name = aClass.getSimpleName();
    return com.intellij.codeInspection.InspectionProfileEntry.getShortName(name);
  }
}
public class <error descr="Inspection does not have a description [getShortName()]">MyWithDescriptionAndShortNameInBaseInspection</error> extends ALocalInspectionTool {}