package com.siyeh.igtest.style.confusing_octal_escape;

public class ConfusingOctalEscape {

  void foo() {
    System.out.println("<warning descr="Octal escape sequence '\123' immediately followed by a digit">\123</warning>4<warning descr="Octal escape sequence '\4' immediately followed by a digit">\4</warning>9");
  }

  public static final String foo =  "asdf<warning descr="Octal escape sequence '\012' immediately followed by a digit">\012</warning>34";
  public static final String boo =  "asdf<warning descr="Octal escape sequence '\01' immediately followed by a digit">\01</warning>834";

  public static String escapeLdapSearchValue(String value) {
    // see RFC 2254
    String escapedStr = value;
    escapedStr = escapedStr.replaceAll("\\\\", "\\\\5c");
    escapedStr = escapedStr.replaceAll("\\*", "\\\\2a");
    escapedStr = escapedStr.replaceAll("\\(", "\\\\28");
    escapedStr = escapedStr.replaceAll("\\)", "\\\\29");
    return escapedStr;
  }

  public String path() {
    return "X:\\\\1234567890\\\\1234567890\\\\com\\\\company\\\\system\\\\subsystem";
  }

  String twoDigitOctalEscape() {
    return "<warning descr="Octal escape sequence '\44' immediately followed by a digit">\44</warning>4\344";
  }
}