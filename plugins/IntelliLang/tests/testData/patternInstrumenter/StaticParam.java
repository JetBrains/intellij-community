import org.intellij.lang.annotations.Pattern;

public class StaticParam {
  public static void staticParam(String s1, @Pattern("\\d+") String s2) { }
}