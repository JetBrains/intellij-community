// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.structuralsearch.StructuralSearchTestFixtureKt.structuralSearchFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class XmlStructuralSearchTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<StructuralSearchTestFixture> ssrFixture =
    structuralSearchFixture(projectFixture, PlatformTestUtil.getCommunityPath() + "/platform/structuralsearch/testData/html/");
  
  @Test
  public void testHtmlSearch() throws Exception {
    StructuralSearchTestFixture ssr = ssrFixture.get();
    String content = ssr.loadFile("in1.html");
    String pattern = ssr.loadFile("pattern1.html");
    String pattern2 = ssr.loadFile("pattern1_2.html");
    
    assertEquals(1, ssr.findMatchesCount(content, pattern, HtmlFileType.INSTANCE), "Simple html find");
    assertEquals(9, ssr.findMatchesCount(content, pattern2, HtmlFileType.INSTANCE), "Simple html find");
  }

  @Test
  public void testHtmlSearch2() throws Exception {
    StructuralSearchTestFixture ssr = ssrFixture.get();
    String content = ssr.loadFile("in4.html");
    String pattern = """
      <tr><td>Name</td>

              <td>Address</td>

              <td>Phone</td>

          </tr>
      """;

    assertEquals(1, ssr.findMatchesCount(content, pattern, HtmlFileType.INSTANCE), "Simple html find");

    pattern = "<tr><td>Name</td><td>Address</td><td>Phone</td></tr>\n";
    assertEquals(1, ssr.findMatchesCount(content, pattern, HtmlFileType.INSTANCE), "Simple html find");

    pattern = "<td> </td>";
    assertEquals(3, ssr.findMatchesCount(content, pattern, HtmlFileType.INSTANCE), "Empty tag matches everything");

    pattern = "   <td>  name  </td>   ";
    assertEquals(1, ssr.findMatchesCount(content, pattern, HtmlFileType.INSTANCE), "Ignore surrounding whitespace");

    String in = "<img src=\"foobar.jpg\" alt=\"alt\" width=\"108\" height=\"71\" style=\"display:block\" >";
    assertEquals(1, ssr.findMatchesCount(in, "<img alt '_other+ >", HtmlFileType.INSTANCE));
    assertEquals(1, ssr.findMatchesCount(in, "<img '_alt:alt = \"'_value\" '_other+ >", HtmlFileType.INSTANCE));
    assertEquals(1, ssr.findMatchesCount(in, "<img '_name{2,100} = '_value >", HtmlFileType.INSTANCE));

    String in2 = "<input readonly>" +
                 "<input readonly>" +
                 "<input type=button>";
    assertEquals(1, ssr.findMatchesCount(in2, "<input '_name{0,0} = '_value{0,0} >", HtmlFileType.INSTANCE), "should find tags without any attributes without value");
    assertEquals(2, ssr.findMatchesCount(in2, "<input '_name{1,1}='_value{0,0} >", HtmlFileType.INSTANCE), "should find tags with any attributes without value");
  }

  @Test
  public void testHtmlSearchCaseInsensitive() {
    StructuralSearchTestFixture ssr = ssrFixture.get();
    String html = "<HTML><HEAD><TITLE>Hello Worlds</TITLE></HEAD><body><img src='test.gif'></body></HTML>";
    String pattern = "<title>'_a</title>";

    ssr.getOptions().setCaseSensitiveMatch(false);
    assertEquals(1, ssr.findMatchesCount(html, pattern, HtmlFileType.INSTANCE), "case insensitive search");

    String pattern2 = "<'t SRC=\"'_v\"/>";
    assertEquals(1, ssr.findMatchesCount(html, pattern2, HtmlFileType.INSTANCE), "case insensitive attribute");

    String pattern3 = "<'t '_a=\"TEST.gif\">";
    assertEquals(1, ssr.findMatchesCount(html, pattern3, HtmlFileType.INSTANCE), "case insensitive attribute value");
  }

  @Test
  public void testHtmlSearchCaseSensitive() {
    StructuralSearchTestFixture ssr = ssrFixture.get();
    String html = "<HTML><HEAD><TITLE>Hello Worlds</TITLE></HEAD><body><img src='test.gif'><body></HTML>";
    String pattern = "<title>'_a</title>";

    ssr.getOptions().setCaseSensitiveMatch(true);
    assertEquals(0, ssr.findMatchesCount(html, pattern, HtmlFileType.INSTANCE), "case sensitive search");
  }

  @Test
  public void testXmlSearch() {
    StructuralSearchTestFixture ssr = ssrFixture.get();
    String s1 = "<aaa><bbb class=\"11\"></bbb></aaa><bbb class=\"22\"></bbb>";
    String s2 = "<bbb></bbb>";
    String s2_2 = "<bbb/>";
    String s2_3 = "<'t:[ regex( aaa ) ] />";
    String s2_4 = "<'_ 't:[ regex( class ) ]=\"'_\" />";
    String s2_5 = "<'_ '_=\"'t:[ regex( 11 ) ]\" />";

    assertEquals(2, ssr.findMatchesCount(s1, s2, XmlFileType.INSTANCE), "Simple xml find");
    assertEquals(2, ssr.findMatchesCount(s1, s2_2, XmlFileType.INSTANCE), "Simple xml find with empty tag");
    assertEquals(1, ssr.findMatchesCount(s1, s2_3, XmlFileType.INSTANCE), "Simple xml find with typed var");

    assertEquals(2, ssr.findMatchesCount(s1, s2_4, HtmlFileType.INSTANCE), "Simple xml find with typed attr");
    assertEquals(1, ssr.findMatchesCount(s1, s2_5, HtmlFileType.INSTANCE), "Simple xml find with typed attr value");
    assertEquals(2, ssr.findMatchesCount(s1, "<'_ '_+ />", HtmlFileType.INSTANCE), "Simple xml find with attr without value");

    String s3 = """
      <a> content </a>
      <b> another content </b>
      <c>another <aaa>zzz</aaa>content </c>""";
    String s4 = "<'_tag>'Content+</'_tag>";
    assertEquals(6, ssr.findMatchesCount(s3, s4, HtmlFileType.INSTANCE), "Content match");
    assertEquals(6, ssr.findMatchesCount(s3, s4, XmlFileType.INSTANCE), "Content match");
  }

  @Test
  public void testNoUnexpectedException() {
    StructuralSearchTestFixture ssr = ssrFixture.get();
    String source = "<html><title>title</title></html>";

    try {
      ssr.findMatchesCount(source, "<'_tag>", XmlFileType.INSTANCE);
    } catch (MalformedPatternException e) {
      fail();
    }

    try {
      ssr.findMatchesCount(source, "<'_tag '_attr>", XmlFileType.INSTANCE);
    } catch (MalformedPatternException e) {
      fail();
    }
  }

  @Test
  public void testInvalidPatternWarnings() {
    StructuralSearchTestFixture ssr = ssrFixture.get();
    String source = "<x>z</x>";

    try {
      ssr.findMatchesCount(source, "<title>$A$$</title>", HtmlFileType.INSTANCE);
      fail();
    } catch (MalformedPatternException ignore) {}

    try {
      ssr.findMatchesCount(source, "<x>1<3</x>", XmlFileType.INSTANCE);
      fail();
    } catch (MalformedPatternException ignore) {}

    try {
      ssr.findMatchesCount(source, "<x'_tag>", XmlFileType.INSTANCE);
      fail();
    } catch (MalformedPatternException ignore) {}
  }

  @Test
  public void testWithinPredicate() {
    StructuralSearchTestFixture ssr = ssrFixture.get();
    String source = "<ul><li>one</li></ul><li>two</li><li>three</li>";
    String pattern1 = "[within( \"<ul>'content*</ul>\" )]<'li />";
    assertEquals(1, ssr.findMatchesCount(source, pattern1, XmlFileType.INSTANCE), "within predicate");
    String pattern1a = "[within( \"<ul>'_content*</ul>\" )]<'li />";
    assertEquals(1, ssr.findMatchesCount(source, pattern1a, XmlFileType.INSTANCE), "within predicate");

    String pattern2 = "[!within( \"<ul>'content*</ul>\" )]<'li />";
    assertEquals(3, ssr.findMatchesCount(source, pattern2, XmlFileType.INSTANCE), "not within predicate");
    String pattern2a = "[!within( \"<ul>'_content*</ul>\" )]<'li />";
    assertEquals(3, ssr.findMatchesCount(source, pattern2a, XmlFileType.INSTANCE), "not within predicate");
  }

  @Test
  public void testCssStyleTag() {
    String source = """
      <html>
      <style type="text/css">
          .stretchFormElement { width: auto; }
      </style>
      <img src="madonna.jpg" alt='Foligno Madonna, by Raphael' one="tro"/>
      </html>""";
    String pattern = "<'_a type=\"text/css\">'_content*</'_a>";
    assertEquals(1, ssrFixture.get().findMatchesCount(source, pattern, HtmlFileType.INSTANCE), "find tag with css content");
  }

  @Test
  public void testSearchIgnoreComments() {
    String source = """
      <user id="1">
        <first_name>Max</first_name> <!-- asdf -->
        <last_name>Headroom</last_name>
      </user>""";
    String pattern = "<first_name>$A$</first_name><last_name>$B$</last_name>";
    assertEquals(1, ssrFixture.get().findMatchesCount(source, pattern, XmlFileType.INSTANCE), "find tag ignoring comments");
  }

  @Test
  public void testComments() {
    StructuralSearchTestFixture ssr = ssrFixture.get();
    String in = "<blockquote>one two <!-- and I cannot emphasize this enough --> three</blockquote>";
    assertEquals(1, ssr.findMatchesCount(in, "<!-- '_x -->", HtmlFileType.INSTANCE), "find comment");
    assertEquals(1, ssr.findMatchesCount(in, "<!-- and I cannot emphasize this enough -->", HtmlFileType.INSTANCE), "find comment 2");
    assertEquals(1, ssr.findMatchesCount(in, "two", HtmlFileType.INSTANCE), "find partial text");
    assertEquals(1, ssr.findMatchesCount(in, "  one   two   three   ", HtmlFileType.INSTANCE), "find text ignoring comments & whitespace");
    assertEquals(1, ssr.findMatchesCount(in, "  one   two  <!-- and   I   cannot emphasize  this  enough --> three   ", HtmlFileType.INSTANCE),
                 "find text with comments, ignoring whitespace");
    assertEquals(1, ssr.findMatchesCount(in, "  one   two  <!-- and   I   cannot emphasize  this  enough --> '_x+   ", HtmlFileType.INSTANCE),
                 "find text & var with comments, ignoring whitespace");
    assertEquals(0, ssr.findMatchesCount(in, "  one   two  <!--   I can   emphasize  this  enough --> three   ", HtmlFileType.INSTANCE),
                 "should find nothing, comment doesn't match");
  }

  @Test
  public void testXmlSearch2() {
    String s1 = "<body><p class=\"11\"> AAA </p><p class=\"22\"></p> <p> ZZZ </p> <p/> <p/> <p/> </body>";
    String s2 = "<p '_a{0,0}=\"'_t:[ regex( 11 ) ]\"> '_content? </p>";

    assertEquals(5, ssrFixture.get().findMatchesCount(s1, s2, XmlFileType.INSTANCE));
  }

  @Test
  public void testErroneousPatterns() {
    StructuralSearchTestFixture ssr = ssrFixture.get();
    try {
      ssr.findMatchesCount("", "<H", HtmlFileType.INSTANCE);
      fail();
    } catch (MalformedPatternException ignore) {}

    ssr.findMatchesCount("", "<H>", HtmlFileType.INSTANCE); // although invalid HTML, should not report an error
    ssr.findMatchesCount("", "<A href>", XmlFileType.INSTANCE); // although missing attribute value, should not report an error
  }
}
