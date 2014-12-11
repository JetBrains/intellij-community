package com.siyeh.igtest.internationalization.unnecessary_unicode_escape;

class UnnecessaryUnicodeEscape {
  // <warning descr="Unicode escape sequence '\uuuuuu0061' can be replaced with 'a'">\uuuuuu0061</warning><warning descr="Unicode escape sequence '\u0062' can be replaced with 'b'">\u0062</warning>
  // control char & not representable char: \u0010 \u00e4

  char[] surrogates = new char[]{'\ud800','\udc00'};
}
@SuppressWarnings("UnnecessaryUnicodeEscape")
class Suppress {
  String s = "\u0062";
}