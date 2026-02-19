import org.intellij.lang.annotations.Pattern;

public enum EnumConstructor {
  V1("0"), V2("-", "0");

  EnumConstructor(@Pattern("\\d+") String s) { }

  EnumConstructor(String s1, @Pattern("\\d+") String s2) { }
}