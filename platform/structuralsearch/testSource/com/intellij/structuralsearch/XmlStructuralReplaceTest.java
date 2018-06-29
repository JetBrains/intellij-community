// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class XmlStructuralReplaceTest extends StructuralReplaceTestCase {

  public void setUp() throws Exception {
    super.setUp();
    options.getMatchOptions().setFileType(StdFileTypes.XML);
  }

  public void testReplaceXmlAndHtml() {
    String s1 = "<a/>";
    String s2 = "<a/>";
    String s3 = "<a><b/></a>";

    String expectedResult = "<a><b/></a>";
    assertEquals("First tag replacement", expectedResult, replace(s1, s2, s3));

    String s4 = "<group id=\"EditorTabPopupMenu\">\n" +
                "      <reference id=\"Compile\"/>\n" +
                "      <reference id=\"RunContextPopupGroup\"/>\n" +
                "      <reference id=\"ValidateXml\"/>\n" +
                "      <separator/>\n" +
                "      <reference id=\"VersionControlsGroup\"/>\n" +
                "      <separator/>\n" +
                "      <reference id=\"ExternalToolsGroup\"/>\n" +
                "</group>";
    String s5 = "<reference id=\"'_Value\"/>";
    String s6 = "<reference ref=\"$Value$\"/>";

    expectedResult = "<group id=\"EditorTabPopupMenu\">\n" +
                     "      <reference ref=\"Compile\"/>\n" +
                     "      <reference ref=\"RunContextPopupGroup\"/>\n" +
                     "      <reference ref=\"ValidateXml\"/>\n" +
                     "      <separator/>\n" +
                     "      <reference ref=\"VersionControlsGroup\"/>\n" +
                     "      <separator/>\n" +
                     "      <reference ref=\"ExternalToolsGroup\"/>\n" +
                     "</group>";
    assertEquals("Replace tag", expectedResult, replace(s4, s5, s6));

    String s7 = "<h4 class=\"a\">My title<aaa>ZZZZ</aaa> My title 3</h4>\n" +
                "<h4>My title 2</h4>";
    String s8 = "<h4 class=\"a\">'_Content*</h4>";
    String s9 = "<h5>$Content$</h5>";

    expectedResult = "<h5>My title <aaa>ZZZZ</aaa>  My title 3</h5>\n" +
                     "<h4>My title 2</h4>";
    assertEquals("Replace tag saving content", expectedResult, replace(s7, s8, s9));

    expectedResult = "\n" +
                     "<h4>My title 2</h4>";
    assertEquals("Delete tag", expectedResult, replace(s7, s8, ""));

    String what = "<'_H:h4 class=\"a\">'_Content*</'_H>";
    String by = "<$H$>$Content$</$H$>";
    expectedResult = "<h4>My title <aaa>ZZZZ</aaa>  My title 3</h4>\n" +
                     "<h4>My title 2</h4>";
    assertEquals("Replace with variable", expectedResult, replace(s7, what, by));

    String in = "<b>Cry 'Havoc!', and <i>let slip the<br> dogs of war</i></b>";
    what = "<'_Tag:b >'_Content2*</'_Tag>";
    by = "<$Tag$ id=\"unique\">$Content2$</$Tag$>";
    expectedResult = "<b id=\"unique\">Cry 'Havoc!', and  <i>let slip the<br> dogs of war</i></b>";
    assertEquals("Replace complex content with variable", expectedResult, replace(in, what, by));
  }

  public void testHtmlReplacement1() throws IOException {
    doHtmlReplacementFromTestDataFiles("in1.html", "pattern1_2.html", "replacement1_2.html", "out1.html", "Large html replacement", false);
  }

  public void testHtmlReplacement2() throws IOException {
    doHtmlReplacementFromTestDataFiles("in2.html", "pattern2.html", "replacement2.html", "out2.html", "Large html replacement 2",true);
  }

  public void testHtmlReplacement3() throws IOException {
    doHtmlReplacementFromTestDataFiles("in3.html", "pattern3.html", "replacement3.html", "out3.html", "Html replacement 3",true);
  }

  public void testHtmlAddAttribute() {
    String in = "<input class=\"other\" type=\"text\" ng-model=\"someModel\" placeholder=\"Some placeholder\" />";
    String what = "<input '_a* />";
    String by = "<input $a$ id=\"someId1\" />";
    String expected = "<input class=\"other\" type=\"text\" ng-model=\"someModel\" placeholder=\"Some placeholder\" id=\"someId1\" />";

    assertEquals(expected, replace(in, what, by));
  }

  public void testRemoveAttribute() {
    String in = "<input class=\"other\" placeholder=\"Some placeholder\">";
    String what = "<input 'a:[regex( placeholder )]>";
    String by = "";
    String expected = "<input class=\"other\">";

    assertEquals(expected, replace(in, what, by));

    String in2 = "<img src=\"foobar.jpg\" alt=\"alt\" width=\"108\" height=\"71\" style=\"display:block\" >";
    String what2 = "<img alt '_other*>";
    String by2 = "<img $other$>";
    assertEquals("<img src=\"foobar.jpg\" width=\"108\" height=\"71\" style=\"display:block\">",
                 replace(in2, what2, by2));
  }

  public void testRemoveTag() {
    String in = "<a>\n" +
                "  <b>liberation</b>\n" +
                "  <c>remuneration</c>\n" +
                "</a>";
    String what = "<'tag:[regex( c )]>'_text</'tag>";
    String by = "";
    String expected = "<a>\n" +
                      "  <b>liberation</b>\n" +
                      "</a>";

    assertEquals(expected, replace(in, what, by));
  }

  public void testReplaceAttributeValue() {
    String in = "<input id=\"one\" class=\"no\">";
    String what = "<'_tag '_attr:[regex( id )]=\\''value\\'>";
    String by = "\"two\"";
    String expected = "<input id=\"two\" class=\"no\">";

    assertEquals(expected, replace(in, what, by));
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/platform/structuralsearch/testData/";
  }

  private void doHtmlReplacementFromTestDataFiles(final String inFileName,
                                                  final String patternFileName,
                                                  final String replacementFileName,
                                                  final String outFileName,
                                                  final String message, boolean filepattern) throws IOException {
    options.getMatchOptions().setFileType(StdFileTypes.HTML);

    String content = loadFile(inFileName);
    String pattern = loadFile(patternFileName);
    String replacement = loadFile(replacementFileName);
    String expectedResult = loadFile(outFileName);

    assertEquals(message, expectedResult, replace(content, pattern, replacement, filepattern));

    options.getMatchOptions().setFileType(StdFileTypes.XML);
  }
}
