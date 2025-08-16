import org.intellij.lang.annotations.Pattern;

public class ConstructorParam {
  public ConstructorParam(String s1, @Pattern("\\d+") String s2) { }
}