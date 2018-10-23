import org.intellij.lang.annotations.Pattern;

public enum TestEnum {
  V1("0"), V2("-", "0");

  TestEnum(@Pattern("\\d+") String s) { }

  TestEnum(String s1, @Pattern("\\d+") String s2) { }
}