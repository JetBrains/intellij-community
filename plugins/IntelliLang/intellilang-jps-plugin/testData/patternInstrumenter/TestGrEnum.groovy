import org.intellij.lang.annotations.Pattern

@SuppressWarnings("unused")
enum TestGrEnum {
  G1("-", "0"),
  G2("-", "1");

  TestGrEnum(String s1, @Pattern("\\d+") String s2) { }
}