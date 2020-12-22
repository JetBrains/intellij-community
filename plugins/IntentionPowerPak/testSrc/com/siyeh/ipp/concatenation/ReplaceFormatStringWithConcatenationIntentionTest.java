// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
           "    //noinspection UnnecessaryCallToStringValueOf\n" +
           "    return String.format(\"%s/f%s%s\", /*_Replace 'String.format()' with concatenation*/tempDataFolderPath, Double.toString(Math.random()), fileName);" +
           "  }" +
           "}",

           "class X {" +
           "  String m(String tempDataFolderPath, String fileName) {" +
           "    //noinspection UnnecessaryCallToStringValueOf\n" +
           "    return tempDataFolderPath + \"/f\" + Double.toString(Math.random()) + fileName;" +
           "  }" +
           "}");
  }

  public void testPercentAtTheEnd() {
    doTestIntentionNotAvailable("class X {" +
           "  String x() {" +
           "    //noinspection ResultOfMethodCallIgnored\n" +
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

  public void testPsiStructure() {
    doTest("/** @noinspection ClassInitializerMayBeStatic*/" +
           "class PsiStructure{{" +
           "  String s = \"TEST_DATA_PATH\" + /*_Replace 'String.format()' with concatenation*/String.format(\"templates/MyTemplate.%s.html\", 1);" +
           "}}",

           "/** @noinspection ClassInitializerMayBeStatic*/" +
           "class PsiStructure{{" +
           "  String s = \"TEST_DATA_PATH\" + \"templates/MyTemplate.\" + 1 + \".html\";" +
           "}}");
  }

  public void testConditional() {
    doTest("class Info {\n" +
           "\n" +
           "    public String name;\n" +
           "    public boolean isSource;\n" +
           "\n" +
           "    public boolean isSource() {\n" +
           "        return isSource;\n" +
           "    }\n" +
           "\n" +
           "    public String getName() {\n" +
           "        return name;\n" +
           "    }\n" +
           "\n" +
           "    @Override\n" +
           "    public final String toString() {\n" +
           "        return String.format(\"%s%s port\", getName(),\n" +
           "                             isSource() ? \" source\" : /*_Replace 'String.format()' with concatenation*/\" target\");\n" +
           "    }\n" +
           "}",

           "class Info {\n" +
           "\n" +
           "    public String name;\n" +
           "    public boolean isSource;\n" +
           "\n" +
           "    public boolean isSource() {\n" +
           "        return isSource;\n" +
           "    }\n" +
           "\n" +
           "    public String getName() {\n" +
           "        return name;\n" +
           "    }\n" +
           "\n" +
           "    @Override\n" +
           "    public final String toString() {\n" +
           "        return getName() + (isSource() ? \" source\" : \" target\") + \" port\";\n" +
           "    }\n" +
           "}");
  }
}