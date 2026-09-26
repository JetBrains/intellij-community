import org.intellij.lang.annotations.Pattern;

public class StaticNestedClass {
  public void createNested(String s1, String s2) {
    new Nested(s1, s2);
  }

  private static class Nested {
    Nested(String s1, @Pattern("\\d+") String s2) { }
  }
}