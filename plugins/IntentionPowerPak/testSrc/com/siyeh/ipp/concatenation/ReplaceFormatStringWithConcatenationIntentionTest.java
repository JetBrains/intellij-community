// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.concatenation;

import com.siyeh.ipp.IPPTestCase;

/**
 * @see ReplaceFormatStringWithConcatenationIntention
 * @author Bas Leijdekkers
 */
public class ReplaceFormatStringWithConcatenationIntentionTest extends IPPTestCase {

  public void testNotSupported() {
    doTestIntentionNotAvailable("class C {" +
                                "  String m() {" +
                                "    return /*_Replace 'String.format()' with concatenation*/String.format(\"%d\", 1);" +
                                "  }" +
                                "}");
  }

  public void testSimple() {
    doTest("class C {" +
           "  String m() {" +
           "    return /*_Replace 'String.format()' with concatenation*/String.format(\"%s\", 1);" +
           "  }" +
           "}",

           "class C {" +
           "  String m() {" +
           "    return String.valueOf(1);" +
           "  }" +
           "}");
  }

  public void testMultiple() {
    doTest("class C {" +
           "  String m(String expected, String actual) {" +
           "    return String./*_Replace 'String.format()' with concatenation*/format(\"Expected to get a '%s', got a '%s' instead\", expected, actual);" +
           "  }" +
           "}",

           "class C {" +
           "  String m(String expected, String actual) {" +
           "    return \"Expected to get a '\" + expected + \"', got a '\" + actual + \"' instead\";" +
           "  }" +
           "}");
  }

  public void testMultipleWithNothingInBetween() {
    doTest("class X {" +
           "  String m(String tempDataFolderPath, String fileName) {" +
           "    return String.format(\"%s/f%s%s\", /*_Replace 'String.format()' with concatenation*/tempDataFolderPath, Double.toString(Math.random()), fileName);" +
           "  }" +
           "}",

           "class X {" +
           "  String m(String tempDataFolderPath, String fileName) {" +
           "    return tempDataFolderPath + \"/f\" + Double.toString(Math.random()) + fileName;" +
           "  }" +
           "}");
  }

  public void testPercentAtTheEnd() {
    doTestIntentionNotAvailable("class X {" +
           "  String x() {" +
           "    String.format(/*_Replace 'String.format()' with concatenation*/\"nope%\", 1);" +
           "  }" +
           "}");
  }

  public void testNewline() {
    doTest("class Z {" +
           "  String m(boolean b) {" +
           "    return String.format/*_Replace 'String.format()' with concatenation*/(\"b:\\n %s\", b);" +
           "  }" +
           "}",

           "class Z {" +
           "  String m(boolean b) {" +
           "    return \"b:\\n \" + b;" +
           "  }" +
           "}");
  }

  public void testSingleCharacterSuffix() {
    doTest("class Idempotent {" +
           "  String x() {" +
           "    return String./*_Replace 'String.format()' with concatenation*/format(\"%s.\", \"foo\");" +
           "  }" +
           "}",

           "class Idempotent {" +
           "  String x() {" +
           "    return \"foo\" + \".\";" +
           "  }" +
           "}");
  }
}