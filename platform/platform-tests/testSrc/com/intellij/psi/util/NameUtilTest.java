/*
 * @author max
 */
package com.intellij.psi.util;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

public class NameUtilTest extends UsefulTestCase {

  public void testSimpleCases() throws Exception {
    assertMatches("N", "NameUtilTest");
    assertMatches("NU", "NameUtilTest");
    assertMatches("NUT", "NameUtilTest");
    assertMatches("NaUT", "NameUtilTest");
    assertDoesntMatch("NeUT", "NameUtilTest");
    assertDoesntMatch("NaUTa", "NameUtilTest");
    assertMatches("NaUtT", "NameUtilTest");
    assertMatches("NaUtT", "NameUtilTest");
    assertMatches("NaUtTe", "NameUtilTest");
    assertMatches("AACl", "AAClass");
    assertMatches("ZZZ", "ZZZZZZZZZZ");
  }
  
  public void testSimpleCasesWithFirstLowercased() throws Exception {
    assertMatches("N", "nameUtilTest");
    assertDoesntMatch("N", "anameUtilTest");
    assertMatches("NU", "nameUtilTest");
    assertDoesntMatch("NU", "anameUtilTest");
    assertMatches("NUT", "nameUtilTest");
    assertMatches("NaUT", "nameUtilTest");
    assertDoesntMatch("NeUT", "nameUtilTest");
    assertDoesntMatch("NaUTa", "nameUtilTest");
    assertMatches("NaUtT", "nameUtilTest");
    assertMatches("NaUtT", "nameUtilTest");
    assertMatches("NaUtTe", "nameUtilTest");
  }
  
  public void testSpaceDelimiters() throws Exception {
    assertMatches("Na Ut Te", "name util test");
    assertMatches("Na Ut Te", "name Util Test");
    assertDoesntMatch("Na Ut Ta", "name Util Test");
    assertMatches("na ut te", "name util test");

    assertMatches("na ut", "name_util_test");
    assertDoesntMatch("na te", "name_util_test");
    assertDoesntMatch("na ti", "name_util_test");
  }
  
  public void testXMLCompletion() throws Exception {
    assertDoesntMatch("N_T", "NameUtilTest");
    assertDoesntMatch("ORGS_ACC", "ORGS_POSITION_ACCOUNTABILITY");
    assertDoesntMatch("ORGS-ACC", "ORGS-POSITION_ACCOUNTABILITY");
    assertDoesntMatch("ORGS.ACC", "ORGS.POSITION_ACCOUNTABILITY");
  }
  
  public void testUnderscoreStyle() throws Exception {
    assertMatches("N_U_T", "NAME_UTIL_TEST");
    assertMatches("NUT", "NAME_UTIL_TEST");
    assertDoesntMatch("NUT", "NameutilTest");
  }

  public void testAllUppercase() {
    assertMatches("NOS", "NetOutputStream");
  }

  public void testCommonFileNameConventions() throws Exception {
    // See IDEADEV-12310

    assertMatches("BLWN", "base_layout_without_navigation.xhtml");
    assertMatches("BLWN", "base-layout-without-navigation.xhtml");
    assertMatches("FC", "faces-config.xml");
    assertMatches("ARS", "activity_report_summary.jsp");
    assertMatches("AD", "arrow_down.gif");
    assertMatches("VL", "vehicle-listings.css");

    assertMatches("ARS.j", "activity_report_summary.jsp");
    assertDoesntMatch("ARS.j", "activity_report_summary.xml");
    assertDoesntMatch("ARS.j", "activity_report_summary_justsometingwrong.xml");

    assertDoesntMatch("foo.goo", "foo.bar.goo");
  }

  public void testIDEADEV15503() throws Exception {
    assertMatches("AR.jsp", "add_relationship.jsp");
    assertMatches("AR.jsp", "advanced_rule.jsp");
    assertMatches("AR.jsp", "alarm_reduction.jsp");
    assertMatches("AR.jsp", "audiot_report.jsp");
    assertMatches("AR.jsp", "audiot_r.jsp");

    assertMatches("AR.jsp", "alarm_rule_action.jsp");
    assertMatches("AR.jsp", "alarm_rule_admin.jsp");
    assertMatches("AR.jsp", "alarm_rule_administration.jsp");
    assertMatches("AR.jsp", "alarm_rule_controller.jsp");
    assertMatches("AR.jsp", "alarm_rule_frame.jsp");
    assertMatches("AR.jsp", "alarm_rule_severity.jsp");

    assertMatches("AR.jsp", "AddRelationship.jsp");
    assertMatches("AR.jsp", "AdvancedRule.jsp");
    assertMatches("AR.jsp", "AlarmReduction.jsp");
    assertMatches("AR.jsp", "AudiotReport.jsp");
    assertMatches("AR.jsp", "AudiotR.jsp");

    assertMatches("AR.jsp", "AlarmRuleAction.jsp");
    assertMatches("AR.jsp", "AlarmRuleAdmin.jsp");
    assertMatches("AR.jsp", "AlarmRuleAdministration.jsp");
    assertMatches("AR.jsp", "AlarmRuleController.jsp");
    assertMatches("AR.jsp", "AlarmRuleFrame.jsp");
    assertMatches("AR.jsp", "AlarmRuleSeverity.jsp");
  }

  public void testSkipDot() {
    assertMatches("ja", "jquery.autocomplete.js");
    assertMatches("ja.js", "jquery.autocomplete.js");
    assertDoesntMatch("jajs", "jquery.autocomplete.js");
    assertDoesntMatch("j.ajs", "jquery.autocomplete.js");
  }

  public void testNoExtension() {
    assertMatches("#.p", "#.php");
    assertMatches("#", "#.php");
    assertMatches("a", "a.php");
  }
  
  public void testStartsWithDot() throws Exception {
    assertMatches(".foo", ".foo");
  }

  public void testProperDotEscaping() throws Exception {
    assertMatches("*inspection*.pro", "InspectionsBundle.properties");
    assertDoesntMatch("*inspection*.pro", "InspectionsInProgress.png");
  }

  public void testIgnoreLeadingUnderscore() throws Exception {
    assertMatches("form", "_form.html.erb");
    assertMatches("_form", "_form.html.erb");
    assertMatches("_form", "__form");
    assertFalse(NameUtil.buildMatcher("_form", 1, true, true, false).matches("__form"));
  }

  public void testLowerCaseWords() throws Exception {
    assertTrue(matches("uct", "unit_controller_test", true));
    assertTrue(matches("unictest", "unit_controller_test", true));
    assertTrue(matches("uc", "unit_controller_test", true));
    assertFalse(matches("nc", "unit_controller_test", true));
    assertFalse(matches("utc", "unit_controller_test", true));
  }

  public void testObjectiveCCases() throws Exception {
    assertMatches("textField:sh", "textField:shouldChangeCharactersInRange:replacementString:");
    assertMatches("text:sh", "textField:shouldChangeCharactersInRange:replacementString:");
    assertMatches("text*:sh", "textField:shouldChangeCharactersInRange:replacementString:");
  }

  public void testMiddleMatching() {
    assertMatches("SWU*H*7", "SWUpgradeHdlrFSPR7Test");
    assertMatches("SWU*H*R", "SWUpgradeHdlrFSPR7Test");
    assertMatches("SWU*H*R", "SWUPGRADEHDLRFSPR7TEST");
    assertMatches("*git", "GitBlaBla");
    assertMatches("*Git", "GitBlaBla");
    assertMatches("*git", "BlaGitBla");
    assertMatches("*Git", "BlaGitBla");
    assertDoesntMatch("*Git", "BlagitBla");
    assertMatches("*git", "BlagitBla");
    assertMatches("*Git*", "AtpGenerationItem");
    assertMatches("Collec*Util*", "CollectionUtils");
    assertMatches("Collec*Util*", "CollectionUtilsTest");
  }

  public void testSpaceInCompletionPrefix() throws Exception {
    assertTrue(NameUtil.buildCompletionMatcher("create ", 0, true, true).matches("create module"));
  }

  public void testLong() throws Exception {
    assertTrue(matches("Product.findByDateAndNameGreaterThanEqualsAndQualityGreaterThanEqual", "Product.findByDateAndNameGreaterThanEqualsAndQualityGreaterThanEqualsIntellijIdeaRulezzz", false));
  }

  private static void assertMatches(@NonNls String pattern, @NonNls String name) {
    assertTrue(matches(pattern, name, false));
  }
  private static void assertDoesntMatch(@NonNls String pattern, @NonNls String name) {
    assertFalse(matches(pattern, name, false));
  }

  private static boolean matches(@NonNls final String pattern, @NonNls final String name, final boolean lowerCaseWords) {
    //System.out.println("\n--- " + name + " " + lowerCaseWords);
    return NameUtil.buildMatcher(pattern, 0, true, true, lowerCaseWords).matches(name);
  }

  public void testLowerCaseHumps() {
    assertMatches("foo", "foo");
    assertDoesntMatch("foo", "fxoo");
    assertMatches("foo", "fOo");
    assertMatches("foo", "fxOo");
    assertDoesntMatch("foo", "fXOo");
    assertDoesntMatch("fOo", "foo");
    assertMatches("fOo", "FaOaOaXXXX");
    assertMatches("ncdfoe", "NoClassDefFoundException");
    assertMatches("fob", "FOO_BAR");
    assertMatches("fo_b", "FOO_BAR");
    assertMatches("fob", "FOO BAR");
    assertMatches("fo b", "FOO BAR");
    assertMatches("AACl", "AAClass");
    assertMatches("ZZZ", "ZZZZZZZZZZ");
    assertMatches("em", "emptyList");
    assertMatches("bui", "BuildConfig.groovy");
    assertMatches("buico", "BuildConfig.groovy");
    assertMatches("buico.gr", "BuildConfig.groovy");
    assertMatches("bui.gr", "BuildConfig.groovy");
    assertMatches("*fz", "azzzfzzz");

    assertDoesntMatch("WebLogic", "Weblogic");
    assertMatches("WebLOgic", "WebLogic");
    assertMatches("WEbLogic", "WebLogic");
    assertDoesntMatch("WebLogic", "Webologic");
  }

  public void testFinalSpace() {
    assertMatches("GrDebT ", "GroovyDebuggerTest");
    assertMatches("grdebT ", "GroovyDebuggerTest");
    assertDoesntMatch("grdebt ", "GroovyDebuggerTest");
    assertMatches("Foo ", "Foo");
    assertDoesntMatch("Foo ", "FooBar");
    assertDoesntMatch("Foo ", "Foox");
    assertDoesntMatch("Collections ", "CollectionSplitter");
    assertMatches("CollectionS ", "CollectionSplitter");
  }

  public void testDigits() {
    assertMatches("foba4", "FooBar4");
    assertMatches("foba", "Foo4Bar");
    assertMatches("*TEST-* ", "TEST-001");
    assertMatches("*TEST-0* ", "TEST-001");
  }

  public void testSpecialSymbols() {
    assertMatches("a@b", "a@bc");

    assertMatches("a/text", "a/Text");
    assertDoesntMatch("a/text", "a/bbbText");
  }

  public void testMinusculeFirstLetter() {
    assertTrue(new NameUtil.MinusculeMatcher("WebLogic", NameUtil.MatchingCaseSensitivity.FIRST_LETTER).matches("WebLogic"));
    assertFalse(new NameUtil.MinusculeMatcher("webLogic", NameUtil.MatchingCaseSensitivity.FIRST_LETTER).matches("WebLogic"));
    assertFalse(new NameUtil.MinusculeMatcher("cL", NameUtil.MatchingCaseSensitivity.FIRST_LETTER).matches("class"));
    assertTrue(new NameUtil.MinusculeMatcher("CL", NameUtil.MatchingCaseSensitivity.FIRST_LETTER).matches("Class"));
    assertFalse(new NameUtil.MinusculeMatcher("abc", NameUtil.MatchingCaseSensitivity.FIRST_LETTER).matches("_abc"));
  }

  public void testMinusculeAllImportant() {
    assertTrue(new NameUtil.MinusculeMatcher("WebLogic", NameUtil.MatchingCaseSensitivity.ALL).matches("WebLogic"));
    assertFalse(new NameUtil.MinusculeMatcher("webLogic", NameUtil.MatchingCaseSensitivity.ALL).matches("weblogic"));
    assertFalse(new NameUtil.MinusculeMatcher("FOO", NameUtil.MatchingCaseSensitivity.ALL).matches("foo"));
    assertFalse(new NameUtil.MinusculeMatcher("foo", NameUtil.MatchingCaseSensitivity.ALL).matches("fOO"));
    assertFalse(new NameUtil.MinusculeMatcher("Wl", NameUtil.MatchingCaseSensitivity.ALL).matches("WebLogic"));
    assertTrue(new NameUtil.MinusculeMatcher("WL", NameUtil.MatchingCaseSensitivity.ALL).matches("WebLogic"));
    assertFalse(new NameUtil.MinusculeMatcher("WL", NameUtil.MatchingCaseSensitivity.ALL).matches("Weblogic"));
    assertFalse(new NameUtil.MinusculeMatcher("WL", NameUtil.MatchingCaseSensitivity.ALL).matches("weblogic"));
    assertFalse(new NameUtil.MinusculeMatcher("webLogic", NameUtil.MatchingCaseSensitivity.ALL).matches("WebLogic"));
    assertFalse(new NameUtil.MinusculeMatcher("Str", NameUtil.MatchingCaseSensitivity.ALL).matches("SomeThingRidiculous"));
  }

  public void testMatchingFragments() {
    @NonNls String sample = "NoClassDefFoundException";
    //                       0 2    7  10   15    21
    assertOrderedEquals(new NameUtil.MinusculeMatcher("ncldfou*ion", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 1), TextRange.from(2, 2), TextRange.from(7, 1), TextRange.from(10, 3), TextRange.from(21, 3));

    sample = "doGet(HttpServletRequest, HttpServletResponse):void";
    //        0                     22
    assertOrderedEquals(new NameUtil.MinusculeMatcher("d*st", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 1), TextRange.from(22, 2));
    assertOrderedEquals(new NameUtil.MinusculeMatcher("doge*st", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 4), TextRange.from(22, 2));

    sample = "_test";
    assertOrderedEquals(new NameUtil.MinusculeMatcher("_", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 1));
    assertOrderedEquals(new NameUtil.MinusculeMatcher("_t", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 2));
  }

  public void testMatchingDegree() {
    assertPreference("OCO", "OneCoolObject", "OCObject");
    assertPreference("MUp", "MavenUmlProvider", "MarkUp");
    assertPreference("MUP", "MarkUp", "MavenUmlProvider");
    assertPreference("CertificateExce", "CertificateEncodingException", "CertificateException");
  }

  private static void assertPreference(@NonNls String pattern, @NonNls String less, @NonNls String more) {
    NameUtil.MinusculeMatcher matcher = new NameUtil.MinusculeMatcher(pattern, NameUtil.MatchingCaseSensitivity.FIRST_LETTER);
    assertTrue(less + ">=" + more, matcher.matchingDegree(less) < matcher.matchingDegree(more));
  }

  public void testPerformance() {
    @NonNls final String longName = "ThisIsAQuiteLongNameWithParentheses().Dots.-Minuses-_UNDERSCORES_digits239:colons:/slashes\\AndOfCourseManyLetters";
    final List<NameUtil.MinusculeMatcher> matching = new ArrayList<NameUtil.MinusculeMatcher>();
    final List<NameUtil.MinusculeMatcher> nonMatching = new ArrayList<NameUtil.MinusculeMatcher>();

    for (String s : CollectionFactory.ar("*", "*i", "*a", "*u", "T", "ti", longName, longName.substring(0, 20))) {
      matching.add(new NameUtil.MinusculeMatcher(s, NameUtil.MatchingCaseSensitivity.NONE));
    }
    for (String s : CollectionFactory.ar("A", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "ta")) {
      nonMatching.add(new NameUtil.MinusculeMatcher(s, NameUtil.MatchingCaseSensitivity.NONE));
    }

    PlatformTestUtil.startPerformanceTest("Matcher is slow", 1200, new ThrowableRunnable() {
      @Override
      public void run() {
        for (int i = 0; i < 100000; i++) {
          for (NameUtil.MinusculeMatcher matcher : matching) {
            assertTrue(matcher.matches(longName));
          }
          for (NameUtil.MinusculeMatcher matcher : nonMatching) {
            assertFalse(matcher.matches(longName));
          }
        }
      }
    }).cpuBound().assertTiming();
  }
}
