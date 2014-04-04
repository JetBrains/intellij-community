package org.jetbrains.protocolReader;

import org.jetbrains.jsonProtocol.JsonReaders;

import java.util.List;

public final class Enums {
  private Enums() {
  }

  public static void appendEnums(List<String> enumConstants, String enumName, boolean input, TextOutput out) {
    out.append("public enum ").append(enumName).openBlock();
    for (String constant : enumConstants) {
      out.append(JsonReaders.convertRawEnumName(constant));
      if (!input) {
        out.append("(\"").append(constant).append("\")");
        if (!enumConstants.get(enumConstants.size() - 1).equals(constant)) {
          out.comma();
        }
      }
      else {
        out.comma();
      }
    }

    if (input) {
      out.append("NO_ENUM_CONST");
    }

    if (input) {
      out.closeBlock();
    }
    else {
      out.semi().newLine();
    }
  }
}
