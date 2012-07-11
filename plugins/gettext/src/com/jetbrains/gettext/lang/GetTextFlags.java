/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.gettext.lang;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Svetlana.Zemlyanskaya
 */
public enum GetTextFlags {

  FUZZY("fuzzy", false),
  RANGE("range", false),
  C("c"),
  OBJC("objc"),
  SH("sh"),
  PYTHON("python"),
  LISP("lisp"),
  ELISP("elisp"),
  LIBREP("librep"),
  SCHEME("scheme"),
  SMALLTALK("smalltalk"),
  JAVA("java"),
  CSHARP("csharp"),
  AWK("awk"),
  YCP("ycp"),
  TCL("tcl"),
  PERL("perl-brace"),
  PHP("php"),
  GCC("gcc-internal"),
  GFC("gfc-internal"),
  QT("qt"),
  KDE("kde"),
  BOOST("boost"),
  PASCAL("object-pascal"),
  QT_PURAL("qt-plural");


  private String flagContent;
  private boolean isFormatFlag;

  GetTextFlags(String flagContent, boolean formatFlag) {
    this.flagContent = flagContent;
    isFormatFlag = formatFlag;
  }

  GetTextFlags(String flagContent) {
    this.flagContent = flagContent;
    this.isFormatFlag = true;
  }

  public static List<String> getAlFlags() {
    List<String> flags = new ArrayList<String>();
    for (GetTextFlags flag : GetTextFlags.values()) {
      if (flag.isFormatFlag) {
        flags.add(constructFormatFlag(flag.flagContent));
        flags.add(constructNoFormatFlag(flag.flagContent));
      } else {
        flags.add(flag.flagContent);
      }
    }
    return flags;
  }

  private static String constructFormatFlag(String formatContent) {
    return formatContent + "-format";
  }

  private static String constructNoFormatFlag(String formatContent) {
    return "no-" + constructFormatFlag(formatContent);
  }
}
