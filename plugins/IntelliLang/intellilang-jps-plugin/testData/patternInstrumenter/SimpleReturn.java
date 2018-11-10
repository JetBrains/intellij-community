import org.intellij.lang.annotations.Pattern;

public class SimpleReturn {
  @Pattern("\\d+")
  public String simpleReturn() {
    return "-";
  }
}