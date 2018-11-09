import org.intellij.lang.annotations.Pattern

@SuppressWarnings("unused")
enum GroovyEnumConstructor {
  G1("-", "0"),
  G2("-", "1");

  GroovyEnumConstructor(String s1, @Pattern("\\d+") String s2) { }
}