import com.intellij.openapi.components.State;

@State(name = "MyStateName")
public class ExportableIdImplicitUsage {

  @State(name = "MyPresentableName", presentableName = Void.class)
  public static class ExportableWithPresentableName {
  }
}