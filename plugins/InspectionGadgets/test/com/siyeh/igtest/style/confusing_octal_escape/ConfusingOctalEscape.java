package com.siyeh.igtest.style.confusing_octal_escape;

public class ConfusingOctalEscape {

  void foo() {
    System.out.println("\1234\49");
  }

  public static final String foo =  "asdf\01234";
  public static final String boo =  "asdf\01834";

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
    return "\444\344";
  }
}