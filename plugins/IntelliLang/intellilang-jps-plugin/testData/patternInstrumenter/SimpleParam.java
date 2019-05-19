import org.intellij.lang.annotations.Pattern;

public class SimpleParam {
  public void simpleParam(String s1, @Pattern("\\d+") String s2) { }
}