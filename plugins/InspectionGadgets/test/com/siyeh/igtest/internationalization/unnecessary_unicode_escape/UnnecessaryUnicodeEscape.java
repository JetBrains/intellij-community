package com.siyeh.igtest.internationalization.unnecessary_unicode_escape;

class UnnecessaryUnicodeEscape {
  // <warning descr="Unicode escape sequence '\uuuuuu0061' can be replaced with 'a'">\uuuuuu0061</warning><warning descr="Unicode escape sequence '\u0062' can be replaced with 'b'">\u0062</warning>
  // control char & not representable char: \u0010 \u00e4

  char[] surrogates = new char[]{'\ud800','\udc00'};

  String t = "<warning descr="Unicode escape sequence '\u0020' can be replaced with ' '">\u0020</warning>";
  String u = "\u200B\u200E\u00A0\u200F";

  String str1 = "<warning descr="Unicode escape sequence '\u0061' can be replaced with 'a'">\u0061</warning>";
  String str2 = "\\u0061"; // Backslash followed by the characters "u0061"
  String str3 = "\\<warning descr="Unicode escape sequence '\u0061' can be replaced with 'a'">\u0061</warning>"; // Backslash followed by escape sequence
  String str4 = <error descr="Illegal line end in string literal">"\u004"; // Too short to be a Unicode escape sequence</error><EOLError descr="';' expected"></EOLError>
  String str5 = <error descr="Illegal escape character in string literal">"\u004g"</error>; // Invalid hex character

  // <warning descr="Unicode escape sequence '\u0009' can be replaced with a tab character">\u0009</warning>
  // <warning descr="Unicode escape sequence '\u000A' can be replaced with a line feed character">\u000A</warning>
}
@SuppressWarnings("UnnecessaryUnicodeEscape")
class Suppress {
  String s = "\u0062";
}