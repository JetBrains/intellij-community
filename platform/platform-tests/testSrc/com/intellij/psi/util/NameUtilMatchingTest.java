// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.util;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.AllOccurrencesMatcher;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.util.text.Matcher;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;

import java.util.function.ToIntFunction;

public class NameUtilMatchingTest extends TestCase {
  public void testSimpleCases() {
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

  public void testEmptyPrefix() {
    assertMatches("", "");
    assertMatches("", "asdfs");
  }

  public void testSkipWords() {
    assertMatches("nt", "NameUtilTest");
    assertMatches("repl map", "ReplacePathToMacroMap");
    assertMatches("replmap", "ReplacePathToMacroMap");
    assertMatches("CertificateEx", "CertificateEncodingException");
    assertDoesntMatch("ABCD", "AbstractButton.DISABLED_ICON_CHANGED_PROPERTY");

    assertMatches("templipa", "template_impl_template_list_panel");
    assertMatches("templistpa", "template_impl_template_list_panel");
  }
  
  public void testSimpleCasesWithFirstLowercased() {
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
  
  public void testSpaceDelimiters() {
    assertMatches("Na Ut Te", "name util test");
    assertMatches("Na Ut Te", "name Util Test");
    assertDoesntMatch("Na Ut Ta", "name Util Test");
    assertMatches("na ut te", "name util test");

    assertMatches("na ut", "name_util_test");
    assertMatches("na te", "name_util_test");
    assertDoesntMatch("na ti", "name_util_test");

    assertDoesntMatch("alias imple", "alias simple");
    assertDoesntMatch("alias mple", "alias simple");
    assertDoesntMatch("alias nother", "alias another");
  }
  
  public void testXMLCompletion() {
    assertDoesntMatch("N_T", "NameUtilTest");
    assertMatches("ORGS_ACC", "ORGS_POSITION_ACCOUNTABILITY");
    assertMatches("ORGS-ACC", "ORGS-POSITION_ACCOUNTABILITY");
    assertMatches("ORGS.ACC", "ORGS.POSITION_ACCOUNTABILITY");
  }

  public void testStarFalsePositive() {
    assertDoesntMatch("ar*l*p", "AbstractResponseHandler");
  }

  public void testUnderscoreStyle() {
    assertMatches("N_U_T", "NAME_UTIL_TEST");
    assertMatches("NUT", "NAME_UTIL_TEST");
    assertDoesntMatch("NUT", "NameutilTest");
  }

  public void testAllUppercase() {
    assertMatches("NOS", "NetOutputStream");
  }

  public void testCommonFileNameConventions() {
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

    assertMatches("foo.goo", "foo.bar.goo");
    assertDoesntMatch("*.ico", "sm.th.iks.concierge");
  }

  public void testSpaceForAnyWordsInBetween() {
    assertMatches("fo bar", "fooBar");
    assertMatches("foo bar", "fooBar");
    assertMatches("foo bar", "fooGooBar");
    assertMatches("foo bar", "fooGoo bar");
    assertDoesntMatch(" b", "fbi");
    assertDoesntMatch(" for", "performAction");
    assertTrue(caseInsensitiveMatcher(" us").matches("getUsage"));
    assertTrue(caseInsensitiveMatcher(" us").matches("getMyUsage"));
  }

  public void testFilenamesWithDotsAndSpaces() {
    assertMatches("Google Test.html", "Google Test Test.cc.html");
    assertMatches("Google.html", "Google Test Test.cc.html");
    assertMatches("Google .html", "Google Test Test.cc.html");
    assertMatches("Google Test*.html", "Google Test Test.cc.html");
  }

  private static MinusculeMatcher caseInsensitiveMatcher(String pattern) {
    return NameUtil.buildMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE);
  }

  public void testStartDot() {
    assertMatches("A*.html", "A.html");
    assertMatches("A*.html", "Abc.html");
    assertMatches("A*.html", "after.html");
    assertDoesntMatch("A*.html", "10_after.html");
  }

  public void testIDEADEV15503() {
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
    assertDoesntMatch("ja.js", "jquery.autocomplete.js");
    assertMatches("jajs", "jquery.autocomplete.js");
    assertMatches("jjs", "jquery.autocomplete.js");
    assertMatches("j.js", "jquery.autocomplete.js");
    assertDoesntMatch("j.ajs", "jquery.autocomplete.js");
    assertMatches("oracle.bnf", "oracle-11.2.bnf");
    assertMatches("*foo.*bar", "foo.b.bar");
  }

  public void testNoExtension() {
    assertMatches("#.p", "#.php");
    assertMatches("#", "#.php");
    assertMatches("a", "a.php");
  }

  public void testIgnoreCaseWhenCompleteMatch() {
    assertMatches("comboBox", "combobox");
    assertMatches("combobox", "comboBox");
  }
  
  public void testStartsWithDot() {
    assertMatches(".foo", ".foo");
  }

  public void testProperDotEscaping() {
    assertMatches("*inspection*.pro", "InspectionsBundle.properties");
    assertDoesntMatch("*inspection*.pro", "InspectionsInProgress.png");
  }

  public void testLeadingUnderscore() {
    assertDoesntMatch("form", "_form.html.erb");
    assertMatches("_form", "_form.html.erb");
    assertMatches("_form", "__form");
    assertTrue(firstLetterMatcher("_form").matches("__form"));
  }

  public void testLowerCaseWords() {
    assertMatches("uct", "unit_controller_test");
    assertMatches("unictest", "unit_controller_test");
    assertMatches("uc", "unit_controller_test");
    assertDoesntMatch("nc", "unit_controller_test");
    assertDoesntMatch("utc", "unit_controller_test");
  }

  public void testObjectiveCCases() {
    assertMatches("h*:", "h:aaa");
    assertMatches("h:", "h:aaa");
    assertMatches("text:sh", "textField:shouldChangeCharactersInRange:replacementString:");
    assertMatches("abc", "aaa:bbb:ccc");
    assertMatches("textField:sh", "textField:shouldChangeCharactersInRange:replacementString:");
    assertMatches("text*:sh", "textField:shouldChangeCharactersInRange:replacementString:");
  }

  public void testMiddleMatchingMinimumTwoConsecutiveLettersInWordMiddle() {
    assertMatches("*fo", "reformat");
    assertMatches("*f", "reFormat");
    assertMatches("*f", "format");
    assertMatches("*f", "Format");
    assertMatches("*Stri", "string");
    assertMatches("*f", "reformat");
    assertMatches("*f", "reformatCode");
    assertDoesntMatch("*fc", "reformatCode");
    assertDoesntMatch("*foc", "reformatCode");
    assertMatches("*forc", "reformatCode");
    assertDoesntMatch("*sTC", "LazyClassTypeConstructor");

    assertDoesntMatch("*Icon", "LEADING_CONSTRUCTOR");
    assertMatches("*I", "LEADING_CONSTRUCTOR");
    assertMatches("*i", "LEADING_CONSTRUCTOR");
    assertMatches("*in", "LEADING_CONSTRUCTOR");
    assertMatches("*ing", "LEADING_CONSTRUCTOR");
    assertDoesntMatch("*inc", "LEADING_CONSTRUCTOR");
    assertDoesntMatch("*ico", "drawLinePickedOut");

    assertMatches("*l", "AppDelegate");
    assertMatches("*le", "AppDelegate");
    assertMatches("*leg", "AppDelegate");

  }

  public void testMiddleMatchingUnderscore() {
    assertMatches("*_dark", "collapseAll_dark.png");
    assertMatches("*_dark.png", "collapseAll_dark.png");
    assertMatches("**_dark.png", "collapseAll_dark.png");
    assertTrue(firstLetterMatcher("*_DARK").matches("A_DARK.png"));
  }

  public void testMiddleMatching() {
    assertMatches("*zz*", "ListConfigzzKey");
    assertMatches("*zz", "ListConfigzzKey");
    assertTrue(caseInsensitiveMatcher("*old").matches("folder"));
    assertMatches("SWU*H*7", "SWUpgradeHdlrFSPR7Test");
    assertMatches("SWU*H*R", "SWUpgradeHdlrFSPR7Test");
    assertMatches("SWU*H*R", "SWUPGRADEHDLRFSPR7TEST");
    assertMatches("*git", "GitBlaBla");
    assertMatches("*Git", "GitBlaBla");
    assertDoesntMatch("*get*A", "getClass");
    assertMatches("*git", "BlaGitBla");
    assertMatches("*Git", "BlaGitBla");
    assertTrue(firstLetterMatcher("*Git").matches("BlagitBla"));
    assertMatches("*git", "BlagitBla");
    assertMatches("*Git*", "AtpGenerationItem");
    assertMatches("Collec*Util*", "CollectionUtils");
    assertMatches("Collec*Util*", "CollectionUtilsTest");
    assertTrue(caseInsensitiveMatcher("*us").matches("usage"));
    assertTrue(caseInsensitiveMatcher(" us").matches("usage"));
    assertTrue(caseInsensitiveMatcher(" fo. ba").matches("getFoo.getBar"));
    assertMatches(" File. sepa", "File.separator");
    assertMatches(" File. sepa", "File._separator");
    assertMatches(" File. _sepa", "File._separator");
    assertMatches(" _fo", "_foo");
    assertMatches("*BComp", "BaseComponent");
  }

  public void testUppercasePrefixWithMiddleMatching() {
    assertMatches("*OS", "ios");
    assertMatches("*OS", "IOS");
    assertMatches("*OS", "osx");
    assertMatches("*OS", "OSX");

    assertTrue(firstLetterMatcher("*I").matches("ID"));
    assertFalse(firstLetterMatcher("*I").matches("id"));
  }

  public void testAsteriskEndingInsideUppercaseWord() {
    assertMatches("*LRUMap", "SLRUMap");
  }

  public void testMiddleMatchingFirstLetterSensitive() {
    assertTrue(firstLetterMatcher(" cl").matches("getClass"));
    assertFalse(firstLetterMatcher(" EUC-").matches("x-EUC-TW"));
    assertTrue(firstLetterMatcher(" a").matches("aaa"));
    assertFalse(firstLetterMatcher(" a").matches("Aaa"));
    assertFalse(firstLetterMatcher(" a").matches("Aaa"));
    assertFalse(firstLetterMatcher(" _bl").matches("_top"));
    assertFalse(firstLetterMatcher("*Ch").matches("char"));
    assertTrue(firstLetterMatcher("*Codes").matches("CFLocaleCopyISOCountryCodes"));
    assertFalse(firstLetterMatcher("*codes").matches("CFLocaleCopyISOCountryCodes"));
    assertTrue(firstLetterMatcher("*codes").matches("getCFLocaleCopyISOCountryCodes"));
    assertTrue(firstLetterMatcher("*Bcomp").matches("BaseComponent"));
  }

  public void testPreferCamelHumpsToAllUppers() {
    assertPreference("ProVi", "PROVIDER", "ProjectView");
  }

  private static Matcher firstLetterMatcher(String pattern) {
    return NameUtil.buildMatcher(pattern, NameUtil.MatchingCaseSensitivity.FIRST_LETTER);
  }

  public void testSpaceInCompletionPrefix() {
    assertTrue(caseInsensitiveMatcher("create ").matches("create module"));
  }

  public void testLong() {
    assertMatches("Product.findByDateAndNameGreaterThanEqualsAndQualityGreaterThanEqual",
                  "Product.findByDateAndNameGreaterThanEqualsAndQualityGreaterThanEqualsIntellijIdeaRulezzz");
  }

  static void assertMatches(@NonNls String pattern, @NonNls String name) {
    assertTrue(pattern + " doesn't match " + name + "!!!", caseInsensitiveMatcher(pattern).matches(name));
  }
  static void assertDoesntMatch(@NonNls String pattern, @NonNls String name) {
    assertFalse(pattern + " matches " + name + "!!!", caseInsensitiveMatcher(pattern).matches(name));
  }

  public void testUpperCaseMatchesLowerCase() {
    assertMatches("ABC_B.C", "abc_b.c");
  }

  public void testLowerCaseHumps() {
    assertMatches("foo", "foo");
    assertDoesntMatch("foo", "fxoo");
    assertMatches("foo", "fOo");
    assertMatches("foo", "fxOo");
    assertMatches("foo", "fXOo");
    assertMatches("fOo", "foo");
    assertDoesntMatch("fOo", "FaOaOaXXXX");
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

    assertMatches("WebLogic", "Weblogic");
    assertMatches("WebLOgic", "WebLogic");
    assertMatches("WEbLogic", "WebLogic");
    assertDoesntMatch("WebLogic", "Webologic");

    assertMatches("Wlo", "WebLogic");
  }

  public void testFinalSpace() {
    assertMatches("a ", "alpha + beta");
    assertMatches("a ", "a ");
    assertMatches("a ", "a");
    assertMatches("GrDebT ", "GroovyDebuggerTest");
    assertDoesntMatch("grdebT ", "GroovyDebuggerTest");
    assertDoesntMatch("grdebt ", "GroovyDebuggerTest");
    assertMatches("Foo ", "Foo");
    assertDoesntMatch("Foo ", "FooBar");
    assertDoesntMatch("Foo ", "Foox");
    assertDoesntMatch("Collections ", "CollectionSplitter");
    assertMatches("CollectionS ", "CollectionSplitter");
    assertMatches("*run ", "in Runnable.run");

    assertDoesntMatch("*l ", "AppDelegate");
    assertDoesntMatch("*le ", "AppDelegate");
    assertDoesntMatch("*leg ", "AppDelegate");
  }

  public void testDigits() {
    assertMatches("foba4", "FooBar4");
    assertMatches("foba", "Foo4Bar");
    assertMatches("*TEST-* ", "TEST-001");
    assertMatches("*TEST-0* ", "TEST-001");
    assertMatches("*v2 ", "VARCHAR2");
    assertMatches("smart8co", "SmartType18CompletionTest");
    assertMatches("smart8co", "smart18completion");
  }

  public void testDoNotAllowDigitsBetweenMatchingDigits() {
    assertDoesntMatch("*012", "001122");
    assertMatches("012", "0a1_22");
  }

  public void testSpecialSymbols() {
    assertMatches("a@b", "a@bc");
    assertDoesntMatch("*@in", "a int");

    assertMatches("a/text", "a/Text");
    assertMatches("a/text", "a/bbbText");
  }

  public void testMinusculeFirstLetter() {
    assertTrue(firstLetterMatcher("WebLogic").matches("WebLogic"));
    assertFalse(firstLetterMatcher("webLogic").matches("WebLogic"));
    assertTrue(firstLetterMatcher("cL").matches("class"));
    assertTrue(firstLetterMatcher("CL").matches("Class"));
    assertTrue(firstLetterMatcher("Cl").matches("CoreLoader"));
    assertFalse(firstLetterMatcher("abc").matches("_abc"));
  }

  public void testMinusculeAllImportant() {
    assertTrue(NameUtil.buildMatcher("WebLogic", NameUtil.MatchingCaseSensitivity.ALL).matches("WebLogic"));
    assertFalse(NameUtil.buildMatcher("webLogic", NameUtil.MatchingCaseSensitivity.ALL).matches("weblogic"));
    assertFalse(NameUtil.buildMatcher("FOO", NameUtil.MatchingCaseSensitivity.ALL).matches("foo"));
    assertFalse(NameUtil.buildMatcher("foo", NameUtil.MatchingCaseSensitivity.ALL).matches("fOO"));
    assertFalse(NameUtil.buildMatcher("Wl", NameUtil.MatchingCaseSensitivity.ALL).matches("WebLogic"));
    assertTrue(NameUtil.buildMatcher("WL", NameUtil.MatchingCaseSensitivity.ALL).matches("WebLogic"));
    assertFalse(NameUtil.buildMatcher("WL", NameUtil.MatchingCaseSensitivity.ALL).matches("Weblogic"));
    assertFalse(NameUtil.buildMatcher("WL", NameUtil.MatchingCaseSensitivity.ALL).matches("weblogic"));
    assertFalse(NameUtil.buildMatcher("webLogic", NameUtil.MatchingCaseSensitivity.ALL).matches("WebLogic"));
    assertFalse(NameUtil.buildMatcher("Str", NameUtil.MatchingCaseSensitivity.ALL).matches("SomeThingRidiculous"));
    assertFalse(NameUtil.buildMatcher("*list*", NameUtil.MatchingCaseSensitivity.ALL).matches("List"));
    assertFalse(NameUtil.buildMatcher("*list*", NameUtil.MatchingCaseSensitivity.ALL).matches("AbstractList"));
    assertFalse(NameUtil.buildMatcher("java.util.list", NameUtil.MatchingCaseSensitivity.ALL).matches("java.util.List"));
    assertFalse(NameUtil.buildMatcher("java.util.list", NameUtil.MatchingCaseSensitivity.ALL).matches("java.util.AbstractList"));
  }

  public void testMatchingFragments() {
    @NonNls String sample = "NoClassDefFoundException";
    //                       0 2    7  10   15    21
    UsefulTestCase.assertOrderedEquals(NameUtil.buildMatcher("ncldfou*ion", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                                       TextRange.from(0, 1), TextRange.from(2, 2), TextRange.from(7, 1), TextRange.from(10, 3), TextRange.from(21, 3));

    sample = "doGet(HttpServletRequest, HttpServletResponse):void";
    //        0                     22
    UsefulTestCase.assertOrderedEquals(NameUtil.buildMatcher("d*st", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 1), TextRange.from(22, 2));
    UsefulTestCase.assertOrderedEquals(NameUtil.buildMatcher("doge*st", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 4), TextRange.from(22, 2));

    sample = "_test";
    UsefulTestCase.assertOrderedEquals(NameUtil.buildMatcher("_", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 1));
    UsefulTestCase.assertOrderedEquals(NameUtil.buildMatcher("_t", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 2));
  }

  public void testMatchingFragmentsSorted() {
    @NonNls String sample = "SWUPGRADEHDLRFSPR7TEST";
    //                       0        9  12
    UsefulTestCase.assertOrderedEquals(NameUtil.buildMatcher("SWU*H*R", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 3), TextRange.from(9, 1), TextRange.from(12, 1));
  }

  public void testPreferCapsMatching() {
    String sample = "getCurrentUser";
    //               0   4     10
    UsefulTestCase.assertOrderedEquals(NameUtil.buildMatcher("getCU", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 4), TextRange.from(10, 1));
  }

  public void testPlusOrMinusInThePatternShouldAllowToBeSpaceSurrounded() {
    assertMatches("a+b", "alpha+beta");
    assertMatches("a+b", "alpha_gamma+beta");
    assertMatches("a+b", "alpha + beta");
    assertMatches("Foo+", "Foo+Bar.txt");
    assertMatches("Foo+", "Foo + Bar.txt");
    assertMatches("a", "alpha+beta");
    assertMatches("*b", "alpha+beta");
    assertMatches("a + b", "alpha+beta");
    assertMatches("a+", "alpha+beta");
    assertDoesntMatch("a ", "alpha+beta");
    assertMatches("", "alpha+beta");
    assertMatches("*+ b", "alpha+beta");
    assertDoesntMatch("d+g", "alphaDelta+betaGamma");
    assertMatches("*d+g", "alphaDelta+betaGamma");

    assertMatches("a-b", "alpha-beta");
    assertMatches("a-b", "alpha - beta");
  }

  public void testMatchingDegree() {
    assertPreference("jscote", "JsfCompletionTest", "JSCompletionTest", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference("OCO", "OneCoolObject", "OCObject");
    assertPreference("MUp", "MavenUmlProvider", "MarkUp");
    assertPreference("MUP", "MarkUp", "MavenUmlProvider");
    assertPreference("CertificateExce", "CertificateEncodingException", "CertificateException");
    assertPreference("boo", "Boolean", "boolean", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference("Boo", "boolean", "Boolean", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference("getCU", "getCurrentSomething", "getCurrentUser");
    assertPreference("cL", "class", "coreLoader");
    assertPreference("cL", "class", "classLoader");
    assertPreference("inse", "InstrumentationError", "intSet", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference("String", "STRING", "String", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference("*String", "STRING", "String", NameUtil.MatchingCaseSensitivity.NONE);
  }

  public void testPreferAdjacentWords() {
    assertPreference("*psfi", "PsiJavaFileBaseImpl", "PsiFileImpl", NameUtil.MatchingCaseSensitivity.NONE);
  }

  public void testPreferMatchesToTheEnd() {
    assertPreference("*e", "fileIndex", "file", NameUtil.MatchingCaseSensitivity.NONE);
  }

  public void testPreferences() {
    assertPreference(" fb", "FooBar", "_fooBar", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference("*foo", "barFoo", "foobar");
    assertPreference("*fo", "barfoo", "barFoo");
    assertPreference("*fo", "barfoo", "foo");
    assertPreference("*fo", "asdfo", "Foo", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference(" sto", "StackOverflowError", "ArrayStoreException", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference(" EUC-", "x-EUC-TW", "EUC-JP", NameUtil.MatchingCaseSensitivity.FIRST_LETTER);
    assertPreference(" boo", "Boolean", "boolean", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference(" Boo", "boolean", "Boolean", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference("ob", "oci_bind_array_by_name", "obj");
    assertNoPreference("en", "ENABLED", "Enum", NameUtil.MatchingCaseSensitivity.NONE);
  }

  public void testHonorFirstLetterCaseInCompletion() {
    MinusculeMatcher matcher = NameUtil.buildMatcher("*pim", NameUtil.MatchingCaseSensitivity.NONE);
    int iLess = matcher.matchingDegree("PImageDecoder", true);
    int iMore = matcher.matchingDegree("posIdMap", true);
    assertTrue(iLess < iMore);
  }

  public void testPreferWordBoundaryMatch() {
    assertPreference("*ap", "add_profile", "application", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference("*les", "configureByFiles", "getLookupElementStrings");
    assertPreference("*les", "configureByFiles", "getLookupElementStrings", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference("*ea", "LEADING", "NORTH_EAST", NameUtil.MatchingCaseSensitivity.NONE);

    assertPreference("*Icon", "isControlKeyDown", "getErrorIcon", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference("*icon", "isControlKeyDown", "getErrorIcon", NameUtil.MatchingCaseSensitivity.NONE);

    assertPreference("*Icon", "getInitControl", "getErrorIcon", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference("*icon", "getInitControl", "getErrorIcon", NameUtil.MatchingCaseSensitivity.NONE);
  }

  public void testPreferNoWordSkipping() {
    assertPreference("CBP", "CustomProcessBP", "ComputationBatchProcess", NameUtil.MatchingCaseSensitivity.NONE);
  }

  public void testWordLengthDoesNotMatter() {
    assertNoPreference("PropComp", "PropertyComponent", "PropertiesComponent", NameUtil.MatchingCaseSensitivity.NONE);
  }

  public void testMatchStartDoesntMatterForDegree() {
    assertNoPreference(" path", "getAbsolutePath", "findPath", NameUtil.MatchingCaseSensitivity.FIRST_LETTER);
  }

  public void testPreferStartMatching() {
    assertPreference("*tree", "FooTree", "Tree", NameUtil.MatchingCaseSensitivity.NONE);
  }

  public void testPreferContiguousMatching() {
    assertPreference("*mappablejs", "mappable-js.scope.js", "MappableJs.js", NameUtil.MatchingCaseSensitivity.NONE);
  }

  public void testMeaningfulMatchingDegree() {
    assertTrue(caseInsensitiveMatcher(" EUC-").matchingDegree("x-EUC-TW") > Integer.MIN_VALUE);
  }

  static void assertPreference(@NonNls String pattern, @NonNls String less, @NonNls String more) {
    assertPreference(pattern, less, more, NameUtil.MatchingCaseSensitivity.FIRST_LETTER);
  }

  private static void assertPreference(@NonNls String pattern,
                                       @NonNls String less,
                                       @NonNls String more,
                                       NameUtil.MatchingCaseSensitivity sensitivity) {
    assertPreference(NameUtil.buildMatcher(pattern, sensitivity), less, more);
  }

  private static void assertPreference(MinusculeMatcher matcher, String less, String more) {
    assertPreference(less, more, matcher::matchingDegree);
  }

  private static void assertPreference(String less, String more, ToIntFunction<String> matchingDegree) {
    int iLess = matchingDegree.applyAsInt(less);
    int iMore = matchingDegree.applyAsInt(more);
    assertTrue(iLess + ">=" + iMore + "; " + less + ">=" + more, iLess < iMore);
  }

  private static void assertNoPreference(@NonNls String pattern,
                                       @NonNls String name1,
                                       @NonNls String name2,
                                       NameUtil.MatchingCaseSensitivity sensitivity) {
    MinusculeMatcher matcher = NameUtil.buildMatcher(pattern, sensitivity);
    assertEquals(matcher.matchingDegree(name1), matcher.matchingDegree(name2));
  }

 public void testSpeedSearchComparator() {
   final SpeedSearchComparator c = new SpeedSearchComparator(false, true);

   assertNotNull(c.matchingFragments("a", "Ant"));
   assertNotNull(c.matchingFragments("an", "Changes"));
   assertNotNull(c.matchingFragments("a", "Changes"));
 }

  public void testFilePatterns() {
    assertMatches("groovy*.jar", "groovy-1.7.jar");
    assertDoesntMatch("*.ico", "a.i.c.o");
  }

  public void testCapsMayMatchNonCaps() {
    assertMatches("PDFRe", "PdfRenderer");
    assertMatches("*pGETPartTimePositionInfo", "dbo.pGetPartTimePositionInfo.sql");
  }

  public void testACapitalAfterAnotherCapitalMayMatchALowercaseLetterBecauseShiftWasAccidentallyHeldTooLong() {
    assertMatches("USerDefa", "UserDefaults");
    assertMatches("NSUSerDefa", "NSUserDefaults");
    assertMatches("NSUSER", "NSUserDefaults");
    assertMatches("NSUSD", "NSUserDefaults");
    assertMatches("NSUserDEF", "NSUserDefaults");
  }

  public void testCyrillicMatch() {
    assertMatches("ыек", "String");
  }

  public void testMatchingAllOccurrences() {
    String text = "some text";
    MinusculeMatcher matcher = AllOccurrencesMatcher.create("*e", NameUtil.MatchingCaseSensitivity.NONE, "");
    UsefulTestCase.assertOrderedEquals(matcher.matchingFragments(text),
                        new TextRange(3, 4), new TextRange(6, 7));
  }

  public void testCamelHumpWinsOverConsecutiveCaseMismatch() {
    UsefulTestCase.assertSize(3, NameUtil.buildMatcher("GEN", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments("GetExtendedName"));

    assertPreference("GEN", "GetName", "GetExtendedName");
    assertPreference("*GEN", "GetName", "GetExtendedName");
  }

  public void testPrintln() {
    assertMatches("pl", "println");
    assertMatches("pl", "printlnFoo");
    assertDoesntMatch("pl", "printlnx");
  }

  public void testLowerCaseAfterCamels() {
    assertMatches("LSTMa", "LineStatusTrackerManager");
  }

  public void testProperties() {
    assertMatches("*pro", "spring.activemq.pool.configuration.reconnect-on-exception");
  }
}
