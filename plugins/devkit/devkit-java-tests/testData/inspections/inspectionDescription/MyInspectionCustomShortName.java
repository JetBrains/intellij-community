import com.intellij.codeInspection.InspectionProfileEntry;

public class <warning descr="Inspection does not have a description [getShortName()]">MyInspectionCustomShortName</warning> extends InspectionProfileEntry {

  public String getShortName() {
    return "NOT_EXISTING_CUSTOM_SHORT_NAME";
  }
}