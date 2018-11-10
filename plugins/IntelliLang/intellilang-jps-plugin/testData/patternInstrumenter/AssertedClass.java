import org.intellij.lang.annotations.Pattern;

public class AssertedClass {
  static {
    assert AssertedClass.class.getName().length() > 0;
  }

  @Pattern("\\d+")
  public String simpleReturn() {
    return "-";
  }
}