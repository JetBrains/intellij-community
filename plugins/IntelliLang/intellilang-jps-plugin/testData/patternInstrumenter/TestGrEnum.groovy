import org.intellij.lang.annotations.Pattern

@SuppressWarnings("unused")
enum TestGrEnum {
  G1("0", "1", "2"),
  G2("3", "4", "5");

  TestGrEnum(@Pattern("\\d+") String s1, @Pattern("\\d+") String s2, @Pattern("\\d+") String s3) { }
}