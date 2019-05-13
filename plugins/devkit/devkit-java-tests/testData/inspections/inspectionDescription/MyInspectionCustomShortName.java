import com.intellij.codeInspection.InspectionProfileEntry;

public class MyInspectionCustomShortName extends InspectionProfileEntry {

  public String getShortName() {
    return <warning descr="Inspection does not have a description">"NOT_EXISTING_CUSTOM_SHORT_NAME"</warning>;
  }
}