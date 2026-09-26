import org.intellij.lang.annotations.Pattern;

public class DoubleParam {
  public void doubleParam(double d, @Pattern("\\d+") String s) { }
}