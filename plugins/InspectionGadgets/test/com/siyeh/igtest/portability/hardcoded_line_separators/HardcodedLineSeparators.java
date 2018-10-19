package com.siyeh.igtest.portability.hardcoded_line_separators;

public class HardcodedLineSeparators {
  final String newlineString = "<warning descr="Hardcoded line separator '\n'">\n</warning>";
  final String returnString = "<warning descr="Hardcoded line separator '\r'">\r</warning>";

  final char newLineChar = '<warning descr="Hardcoded line separator '\12'">\12</warning>';
  final char returnChar = '<warning descr="Hardcoded line separator '\15'">\15</warning>';
}