import com.intellij.codeInspection.LocalInspectionTool;

public class ShortNameFromFieldReferenceInspection extends LocalInspectionTool {
  private final String mySimpleName = getClass().getSimpleName();
  public String getShortName() {
    return mySimpleName;
  }
}

public class My<caret>WithDescriptionFromFieldReferenceInspection extends ShortNameFromFieldReferenceInspection {
}