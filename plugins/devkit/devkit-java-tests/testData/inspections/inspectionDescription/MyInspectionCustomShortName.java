import com.intellij.codeInspection.InspectionProfileEntry;

public class <error descr="Inspection does not have a description [getShortName()]">MyInspectionCustomShortName</error> extends InspectionProfileEntry {

  public String getShortName() {
    return "NOT_EXISTING_CUSTOM_SHORT_NAME";
  }
}