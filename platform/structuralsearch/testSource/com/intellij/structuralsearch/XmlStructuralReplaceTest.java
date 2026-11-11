// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static com.intellij.structuralsearch.StructuralReplaceTestFixtureKt.structuralReplaceFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class XmlStructuralReplaceTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<StructuralReplaceTestFixture> ssrFixture =
    structuralReplaceFixture(projectFixture, PlatformTestUtil.getCommunityPath() + "/platform/structuralsearch/testData/");

  @BeforeEach
  void setUp() {
    ssrFixture.get().getOptions().getMatchOptions().setFileType(XmlFileType.INSTANCE);
  }

  @Test
  public void testReplaceXmlAndHtml() {
    StructuralReplaceTestFixture ssr = ssrFixture.get();
    String s1 = "<a/>";
    String s2 = "<a/>";
    String s3 = "<a><b/></a>";

    String expectedResult = "<a><b/></a>";
    assertEquals(expectedResult, ssr.replace(s1, s2, s3), "First tag replacement");

    String s4 = """
      <group id="EditorTabPopupMenu">
            <reference id="Compile"/>
            <reference id="RunContextPopupGroup"/>
            <reference id="ValidateXml"/>
            <separator/>
            <reference id="VersionControlsGroup"/>
            <separator/>
            <reference id="ExternalToolsGroup"/>
      </group>""";
    String s5 = "<reference id=\"'_Value\"/>";
    String s6 = "<reference ref=\"$Value$\"/>";

    expectedResult = """
      <group id="EditorTabPopupMenu">
            <reference ref="Compile"/>
            <reference ref="RunContextPopupGroup"/>
            <reference ref="ValidateXml"/>
            <separator/>
            <reference ref="VersionControlsGroup"/>
            <separator/>
            <reference ref="ExternalToolsGroup"/>
      </group>""";
    assertEquals(expectedResult, ssr.replace(s4, s5, s6), "Replace tag");

    String s7 = "<h4 class=\"a\">My title<aaa>ZZZZ</aaa> My title 3</h4>\n" +
                "<h4>My title 2</h4>";
    String s8 = "<h4 class=\"a\">'_Content*</h4>";
    String s9 = "<h5>$Content$</h5>";

    expectedResult = "<h5>My title <aaa>ZZZZ</aaa>  My title 3</h5>\n" +
                     "<h4>My title 2</h4>";
    assertEquals(expectedResult, ssr.replace(s7, s8, s9), "Replace tag saving content");

    expectedResult = "\n" +
                     "<h4>My title 2</h4>";
    assertEquals(expectedResult, ssr.replace(s7, s8, ""), "Delete tag");

    String what = "<'_H:h4 class=\"a\">'_Content*</'_H>";
    String by = "<$H$>$Content$</$H$>";
    expectedResult = "<h4>My title <aaa>ZZZZ</aaa>  My title 3</h4>\n" +
                     "<h4>My title 2</h4>";
    assertEquals(expectedResult, ssr.replace(s7, what, by), "Replace with variable");

    String in = "<b>Cry 'Havoc!', and <i>let slip the<br> dogs of war</i></b>";
    what = "<'_Tag:b >'_Content2*</'_Tag>";
    by = "<$Tag$ id=\"unique\">$Content2$</$Tag$>";
    expectedResult = "<b id=\"unique\">Cry 'Havoc!', and  <i>let slip the<br> dogs of war</i></b>";
    assertEquals(expectedResult, ssr.replace(in, what, by), "Replace complex content with variable");
  }

  @Test
  public void testHtmlReplacement1() throws IOException {
    doHtmlReplacementFromTestDataFiles("in1.html", "pattern1_2.html", "replacement1_2.html", "out1.html", "Large html replacement", false);
  }

  @Test
  public void testHtmlReplacement2() throws IOException {
    doHtmlReplacementFromTestDataFiles("in2.html", "pattern2.html", "replacement2.html", "out2.html", "Large html replacement 2",true);
  }

  @Test
  public void testHtmlReplacement3() throws IOException {
    doHtmlReplacementFromTestDataFiles("in3.html", "pattern3.html", "replacement3.html", "out3.html", "Html replacement 3",true);
  }

  @Test
  public void testHtmlAddAttribute() {
    StructuralReplaceTestFixture ssr = ssrFixture.get();
    String in = "<input class=\"other\" type=\"text\" ng-model=\"someModel\" placeholder=\"Some placeholder\" />";
    String what = "<input '_a* />";
    String by = "<input $a$ id=\"someId1\" />";
    String expected = "<input class=\"other\" type=\"text\" ng-model=\"someModel\" placeholder=\"Some placeholder\" id=\"someId1\" />";

    assertEquals(expected, ssr.replace(in, what, by));
  }

  @Test
  public void testRemoveAttribute() {
    StructuralReplaceTestFixture ssr = ssrFixture.get();
    String in = "<input class=\"other\" placeholder=\"Some placeholder\">";
    String what = "<input 'a:[regex( placeholder )]>";
    String by = "";
    String expected = "<input class=\"other\">";

    assertEquals(expected, ssr.replace(in, what, by));

    String in2 = "<img src=\"foobar.jpg\" alt=\"alt\" width=\"108\" height=\"71\" style=\"display:block\" >";
    String what2 = "<img alt '_other*>";
    String by2 = "<img $other$>";
    assertEquals("<img src=\"foobar.jpg\" width=\"108\" height=\"71\" style=\"display:block\">",
                 ssr.replace(in2, what2, by2));
  }

  @Test
  public void testRemoveTag() {
    StructuralReplaceTestFixture ssr = ssrFixture.get();
    String in = """
      <a>
        <b>liberation</b>
        <c>remuneration</c>
      </a>""";
    String what = "<'tag:[regex( c )]>'_text</'tag>";
    String by = "";
    String expected = """
      <a>
        <b>liberation</b>
      </a>""";

    assertEquals(expected, ssr.replace(in, what, by));
  }

  @Test
  public void testReplacementScriptRequiresScriptEngine(@TestDisposable Disposable disposable) {
    StructuralReplaceTestFixture ssr = ssrFixture.get();
    ExtensionTestUtil.maskExtensions(StructuralSearchScriptEngine.EP_NAME, Collections.emptyList(), disposable);

    final ReplacementVariableDefinition definition = new ReplacementVariableDefinition("result");
    definition.setScriptCodeConstraint("value.getText().toInteger() + 1");
    ssr.getOptions().addVariableDefinition(definition);
    String in = "<ul><li>2</li></ul>";
    String what = "<li>'value:[regex(  \\d+  )]</li>";
    String by = "$result$";

    try {
      ssr.replace(in, what, by);
      fail("Replacement script should require a script engine");
    }
    catch (MalformedPatternException e) {
      assertEquals(SSRBundle.message("replacement.variable.is.not.valid", "result",
                                     SSRBundle.message("error.groovy.script.engine.not.available")), e.getMessage());
    }
  }

  @Test
  public void testReplaceAttributeValue() {
    StructuralReplaceTestFixture ssr = ssrFixture.get();
    String in = "<input id=\"one\" class=\"no\">";
    String what = "<'_tag '_attr:[regex( id )]=\\''value\\'>";
    String by = "\"two\"";
    String expected = "<input id=\"two\" class=\"no\">";

    assertEquals(expected, ssr.replace(in, what, by));
  }

  private void doHtmlReplacementFromTestDataFiles(final String inFileName,
                                                  final String patternFileName,
                                                  final String replacementFileName,
                                                  final String outFileName,
                                                  final String message, boolean filepattern) throws IOException {
    StructuralReplaceTestFixture ssr = ssrFixture.get();
    ssr.getOptions().getMatchOptions().setFileType(HtmlFileType.INSTANCE);

    String content = ssr.loadFile(inFileName);
    String pattern = ssr.loadFile(patternFileName);
    String replacement = ssr.loadFile(replacementFileName);
    String expectedResult = ssr.loadFile(outFileName);

    assertEquals(expectedResult, ssr.replace(content, pattern, replacement, filepattern), message);

    ssr.getOptions().getMatchOptions().setFileType(XmlFileType.INSTANCE);
  }
}
