// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

public class XmlStructuralSearchTest extends StructuralSearchTestCase {
  
  public void testHtmlSearch() throws Exception {
    String content = loadFile("in1.html");
    String pattern = loadFile("pattern1.html");
    String pattern2 = loadFile("pattern1_2.html");
    
    assertEquals("Simple html find", 1, findMatchesCount(content, pattern, StdFileTypes.HTML));
    assertEquals("Simple html find", 9, findMatchesCount(content, pattern2, StdFileTypes.HTML));
  }

  public void testHtmlSearch2() throws Exception {
    String content = loadFile("in4.html");
    String pattern = "<tr><td>Name</td>\n" +
                     "\n" +
                     "        <td>Address</td>\n" +
                     "\n" +
                     "        <td>Phone</td>\n" +
                     "\n" +
                     "    </tr>\n";

    assertEquals("Simple html find", 1, findMatchesCount(content, pattern, StdFileTypes.HTML));

    pattern = "<tr><td>Name</td><td>Address</td><td>Phone</td></tr>\n";
    assertEquals("Simple html find", 1, findMatchesCount(content, pattern, StdFileTypes.HTML));

    pattern = "<td> </td>";
    assertEquals("Empty tag matches everything", 3, findMatchesCount(content, pattern, StdFileTypes.HTML));

    pattern = "   <td>  name  </td>   ";
    assertEquals("Ignore surrounding whitespace", 1, findMatchesCount(content, pattern, StdFileTypes.HTML));

    String in = "<img src=\"foobar.jpg\" alt=\"alt\" width=\"108\" height=\"71\" style=\"display:block\" >";
    assertEquals(1, findMatchesCount(in, "<img alt '_other+ >", StdFileTypes.HTML));
    assertEquals(1, findMatchesCount(in, "<img '_alt:alt = \"'_value\" '_other+ >", StdFileTypes.HTML));
    assertEquals(1, findMatchesCount(in, "<img '_name{2,100} = '_value >", StdFileTypes.HTML));

    String in2 = "<input readonly>" +
                 "<input readonly>" +
                 "<input type=button>";
    assertEquals("should find tags without any attributes without value", 1, findMatchesCount(in2, "<input '_name{0,0} = '_value{0,0} >", StdFileTypes.HTML));
    assertEquals("should find tags with any attributes without value", 2, findMatchesCount(in2, "<input '_name{1,1}='_value{0,0} >", StdFileTypes.HTML));
  }

  public void testHtmlSearchCaseInsensitive() {
    String html = "<HTML><HEAD><TITLE>Hello Worlds</TITLE></HEAD><body><img src='test.gif'></body></HTML>";
    String pattern = "<title>'_a</title>";

    options.setCaseSensitiveMatch(false);
    assertEquals("case insensitive search", 1, findMatchesCount(html, pattern, StdFileTypes.HTML));

    String pattern2 = "<'t SRC=\"'_v\"/>";
    assertEquals("case insensitive attribute", 1, findMatchesCount(html, pattern2, StdFileTypes.HTML));

    String pattern3 = "<'t '_a=\"TEST.gif\">";
    assertEquals("case insensitive attribute value", 1, findMatchesCount(html, pattern3, StdFileTypes.HTML));
  }

  public void testHtmlSearchCaseSensitive() {
    String html = "<HTML><HEAD><TITLE>Hello Worlds</TITLE></HEAD><body><img src='test.gif'><body></HTML>";
    String pattern = "<title>'_a</title>";

    options.setCaseSensitiveMatch(true);
    assertEquals("case sensitive search", 0, findMatchesCount(html, pattern, StdFileTypes.HTML));
  }

  public void testXmlSearch() {
    String s1 = "<aaa><bbb class=\"11\"></bbb></aaa><bbb class=\"22\"></bbb>";
    String s2 = "<bbb></bbb>";
    String s2_2 = "<bbb/>";
    String s2_3 = "<'t:[ regex( aaa ) ] />";
    String s2_4 = "<'_ 't:[ regex( class ) ]=\"'_\" />";
    String s2_5 = "<'_ '_=\"'t:[ regex( 11 ) ]\" />";

    assertEquals("Simple xml find", 2, findMatchesCount(s1, s2, StdFileTypes.XML));
    assertEquals("Simple xml find with empty tag", 2, findMatchesCount(s1, s2_2, StdFileTypes.XML));
    assertEquals("Simple xml find with typed var", 1, findMatchesCount(s1, s2_3, StdFileTypes.XML));

    assertEquals("Simple xml find with typed attr", 2, findMatchesCount(s1, s2_4, StdFileTypes.HTML));
    assertEquals("Simple xml find with typed attr value", 1, findMatchesCount(s1, s2_5, StdFileTypes.HTML));
    assertEquals("Simple xml find with attr without value", 2, findMatchesCount(s1, "<'_ '_+ />", StdFileTypes.HTML));

    String s3 = "<a> content </a>\n" +
                "<b> another content </b>\n" +
                "<c>another <aaa>zzz</aaa>content </c>";
    String s4 = "<'_tag>'Content+</'_tag>";
    assertEquals("Content match", 6, findMatchesCount(s3, s4, StdFileTypes.HTML));
    assertEquals("Content match", 6, findMatchesCount(s3, s4, StdFileTypes.XML));
  }

  public void testNoUnexpectedException() {
    String source = "<html><title>title</title></html>";

    try {
      findMatchesCount(source, "<'_tag>", StdFileTypes.XML);
    } catch (MalformedPatternException e) {
      fail();
    }

    try {
      findMatchesCount(source, "<'_tag '_attr>", StdFileTypes.XML);
    } catch (MalformedPatternException e) {
      fail();
    }
  }

  public void testInvalidPatternWarnings() {
    String source = "<x>z</x>";

    try {
      findMatchesCount(source, "<title>$A$$</title>", StdFileTypes.HTML);
      fail();
    } catch (MalformedPatternException ignore) {}

    try {
      findMatchesCount(source, "<x>1<3</x>", StdFileTypes.XML);
      fail();
    } catch (MalformedPatternException ignore) {}

    try {
      findMatchesCount(source, "<x'_tag>", StdFileTypes.XML);
      fail();
    } catch (MalformedPatternException ignore) {}
  }

  public void testWithinPredicate() {
    String source = "<ul><li>one</li></ul><li>two</li><li>three</li>";
    String pattern1 = "[within( \"<ul>'content*</ul>\" )]<'li />";
    assertEquals("within predicate", 1, findMatchesCount(source, pattern1, StdFileTypes.XML));
    String pattern1a = "[within( \"<ul>'_content*</ul>\" )]<'li />";
    assertEquals("within predicate", 1, findMatchesCount(source, pattern1a, StdFileTypes.XML));

    String pattern2 = "[!within( \"<ul>'content*</ul>\" )]<'li />";
    assertEquals("not within predicate", 3, findMatchesCount(source, pattern2, StdFileTypes.XML));
    String pattern2a = "[!within( \"<ul>'_content*</ul>\" )]<'li />";
    assertEquals("not within predicate", 3, findMatchesCount(source, pattern2a, StdFileTypes.XML));
  }

  public void testCssSTyleTag() {
    String source = "<html>\n" +
                   "<style type=\"text/css\">\n" +
                   "    .stretchFormElement { width: auto; }\n" +
                   "</style>\n" +
                   "<img src=\"madonna.jpg\" alt='Foligno Madonna, by Raphael' one=\"tro\"/>\n" +
                   "</html>";
    String pattern = "<'_a type=\"text/css\">'_content*</'_a>";
    assertEquals("find tag with css content", 1, findMatchesCount(source, pattern, StdFileTypes.HTML));
  }

  public void testSearchIgnoreComments() {
    String source = "<user id=\"1\">\n" +
                    "  <first_name>Max</first_name> <!-- asdf -->\n" +
                    "  <last_name>Headroom</last_name>\n" +
                    "</user>";
    String pattern = "<first_name>$A$</first_name><last_name>$B$</last_name>";
    assertEquals("find tag ignoring comments", 1, findMatchesCount(source, pattern, StdFileTypes.XML));
  }

  public void testComments() {
    String in = "<blockquote>one two <!-- and I cannot emphasize this enough --> three</blockquote>";
    assertEquals("find comment", 1, findMatchesCount(in, "<!-- '_x -->", StdFileTypes.HTML));
    assertEquals("find comment 2", 1, findMatchesCount(in, "<!-- and I cannot emphasize this enough -->", StdFileTypes.HTML));
    assertEquals("find partial text", 1, findMatchesCount(in, "two", StdFileTypes.HTML));
    assertEquals("find text ignoring comments & whitespace", 1, findMatchesCount(in, "  one   two   three   ", StdFileTypes.HTML));
    assertEquals("find text with comments, ignoring whitespace", 1,
                 findMatchesCount(in, "  one   two  <!-- and   I   cannot emphasize  this  enough --> three   ", StdFileTypes.HTML));
    assertEquals("find text & var with comments, ignoring whitespace", 1,
                 findMatchesCount(in, "  one   two  <!-- and   I   cannot emphasize  this  enough --> '_x+   ", StdFileTypes.HTML));
    assertEquals("should find nothing, comment doesn't match", 0,
                 findMatchesCount(in, "  one   two  <!--   I can   emphasize  this  enough --> three   ", StdFileTypes.HTML));
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/platform/structuralsearch/testData/html/";
  }

  public void testXmlSearch2() {
    String s1 = "<body><p class=\"11\"> AAA </p><p class=\"22\"></p> <p> ZZZ </p> <p/> <p/> <p/> </body>";
    String s2 = "<p '_a{0,0}=\"'_t:[ regex( 11 ) ]\"> '_content? </p>";

    assertEquals(5, findMatchesCount(s1, s2, StdFileTypes.XML));
  }

  public void testErroneousPatterns() {
    try {
      findMatchesCount("", "<H", StdFileTypes.HTML);
      fail();
    } catch (MalformedPatternException ignore) {}

    findMatchesCount("", "<H>", StdFileTypes.HTML); // although invalid HTML, should not report an error
    findMatchesCount("", "<A href>", StdFileTypes.XML); // although missing attribute value, should not report an error
  }
}
