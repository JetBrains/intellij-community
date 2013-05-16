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

package com.intellij.psi.util
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.SpeedSearchComparator
import com.intellij.util.ThrowableRunnable
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.Matcher
import org.jetbrains.annotations.NonNls
/**
 * @author max
 * @author peter
 * @author Konstantin Bulenkov
 */
public class NameUtilMatchingTest extends UsefulTestCase {

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

  public void testEmptyPrefix() {
    assertMatches("", "");
    assertMatches("", "asdfs");
  }

  public void testSkipWords() {
    assertMatches("nt", "NameUtilTest");
    assertMatches("repl map", "ReplacePathToMacroMap");
    assertMatches("replmap", "ReplacePathToMacroMap");
    assertDoesntMatch("ABCD", "AbstractButton.DISABLED_ICON_CHANGED_PROPERTY");
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
    assertMatches("na te", "name_util_test");
    assertDoesntMatch("na ti", "name_util_test");
  }
  
  public void testXMLCompletion() throws Exception {
    assertDoesntMatch("N_T", "NameUtilTest");
    assertMatches("ORGS_ACC", "ORGS_POSITION_ACCOUNTABILITY");
    assertMatches("ORGS-ACC", "ORGS-POSITION_ACCOUNTABILITY");
    assertMatches("ORGS.ACC", "ORGS.POSITION_ACCOUNTABILITY");
  }

  public void testStarFalsePositive() throws Exception {
    assertDoesntMatch("ar*l*p", "AbstractResponseHandler");
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

  private static MinusculeMatcher caseInsensitiveMatcher(String pattern) {
    return NameUtil.buildMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE);
  }

  public void testStartDot() {
    assertMatches("A*.html", "A.html");
    assertMatches("A*.html", "Abc.html");
    assertMatches("A*.html", "after.html");
    assertDoesntMatch("A*.html", "10_after.html");
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
    assertDoesntMatch("ja.js", "jquery.autocomplete.js");
    assertMatches("jajs", "jquery.autocomplete.js");
    assertDoesntMatch("j.ajs", "jquery.autocomplete.js");
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
  
  public void testStartsWithDot() throws Exception {
    assertMatches(".foo", ".foo");
  }

  public void testProperDotEscaping() throws Exception {
    assertMatches("*inspection*.pro", "InspectionsBundle.properties");
    assertDoesntMatch("*inspection*.pro", "InspectionsInProgress.png");
  }

  public void testLeadingUnderscore() throws Exception {
    assertDoesntMatch("form", "_form.html.erb");
    assertMatches("_form", "_form.html.erb");
    assertMatches("_form", "__form");
    assertTrue(firstLetterMatcher("_form").matches("__form"));
  }

  public void testLowerCaseWords() throws Exception {
    assertMatches("uct", "unit_controller_test");
    assertMatches("unictest", "unit_controller_test");
    assertMatches("uc", "unit_controller_test");
    assertDoesntMatch("nc", "unit_controller_test");
    assertDoesntMatch("utc", "unit_controller_test");
  }

  public void testObjectiveCCases() throws Exception {
    assertMatches("h*:", "h:aaa");
    assertMatches("h:", "h:aaa");
    assertMatches("text:sh", "textField:shouldChangeCharactersInRange:replacementString:");
    assertMatches("abc", "aaa:bbb:ccc");
    assertMatches("textField:sh", "textField:shouldChangeCharactersInRange:replacementString:");
    assertMatches("text*:sh", "textField:shouldChangeCharactersInRange:replacementString:");
  }

  public void testFileStructure() {
    assertDoesntMatch("hint", "height: int");
    assertMatches("Hint", "Height:int");
    assertDoesntMatch("Hint", "Height: int");
    assertMatches("hI", "Height: int");

    assertMatches("getColor", "getBackground(): Color");
    assertMatches("get color", "getBackground(): Color");
    assertDoesntMatch("getcolor", "getBackground(): Color");

    assertMatches("get()", "getBackground(): Color");

    assertMatches("setColor", "setBackground(Color): void");
    assertMatches("set color", "setBackground(Color): void");
    assertMatches("set Color", "setBackground(Color): void");
    assertMatches("set(color", "setBackground(Color): void");
    assertMatches("set(color)", "setBackground(Color): void");
    assertDoesntMatch("setcolor", "setBackground(Color): void");
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
    assertFalse(firstLetterMatcher("*Git").matches("BlagitBla"));
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

  public void testMiddleMatchingFirstLetterSensitive() {
    assertTrue(firstLetterMatcher(" cl").matches("getClass"));
    assertTrue(firstLetterMatcher(" EUC-").matches("x-EUC-TW"));
    assertTrue(firstLetterMatcher(" a").matches("aaa"));
    assertFalse(firstLetterMatcher(" a").matches("Aaa"));
    assertFalse(firstLetterMatcher(" a").matches("Aaa"));
    assertFalse(firstLetterMatcher(" _bl").matches("_top"));
    assertFalse(firstLetterMatcher("*Ch").matches("char"));
    assertTrue(firstLetterMatcher("*codes").matches("CFLocaleCopyISOCountryCodes"));
    assertTrue(firstLetterMatcher("*Bcomp").matches("BaseComponent"));
  }

  private static Matcher firstLetterMatcher(String pattern) {
    return NameUtil.buildMatcher(pattern, NameUtil.MatchingCaseSensitivity.FIRST_LETTER);
  }

  public void testSpaceInCompletionPrefix() throws Exception {
    assertTrue(caseInsensitiveMatcher("create ").matches("create module"));
  }

  public void testLong() throws Exception {
    assertMatches("Product.findByDateAndNameGreaterThanEqualsAndQualityGreaterThanEqual",
                  "Product.findByDateAndNameGreaterThanEqualsAndQualityGreaterThanEqualsIntellijIdeaRulezzz");
  }

  private static void assertMatches(@NonNls String pattern, @NonNls String name) {
    assertTrue(pattern + " doesn't match " + name + "!!!", caseInsensitiveMatcher(pattern).matches(name));
  }
  private static void assertDoesntMatch(@NonNls String pattern, @NonNls String name) {
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
    assertDoesntMatch("fob", "FOO BAR");
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

    assertDoesntMatch("*l ", "AppDelegate");
    assertDoesntMatch("*le ", "AppDelegate");
    assertDoesntMatch("*leg ", "AppDelegate");
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
    assertTrue(new MinusculeMatcher("WebLogic", NameUtil.MatchingCaseSensitivity.ALL).matches("WebLogic"));
    assertFalse(new MinusculeMatcher("webLogic", NameUtil.MatchingCaseSensitivity.ALL).matches("weblogic"));
    assertFalse(new MinusculeMatcher("FOO", NameUtil.MatchingCaseSensitivity.ALL).matches("foo"));
    assertFalse(new MinusculeMatcher("foo", NameUtil.MatchingCaseSensitivity.ALL).matches("fOO"));
    assertFalse(new MinusculeMatcher("Wl", NameUtil.MatchingCaseSensitivity.ALL).matches("WebLogic"));
    assertTrue(new MinusculeMatcher("WL", NameUtil.MatchingCaseSensitivity.ALL).matches("WebLogic"));
    assertFalse(new MinusculeMatcher("WL", NameUtil.MatchingCaseSensitivity.ALL).matches("Weblogic"));
    assertFalse(new MinusculeMatcher("WL", NameUtil.MatchingCaseSensitivity.ALL).matches("weblogic"));
    assertFalse(new MinusculeMatcher("webLogic", NameUtil.MatchingCaseSensitivity.ALL).matches("WebLogic"));
    assertFalse(new MinusculeMatcher("Str", NameUtil.MatchingCaseSensitivity.ALL).matches("SomeThingRidiculous"));
    assertFalse(new MinusculeMatcher("*list*", NameUtil.MatchingCaseSensitivity.ALL).matches("List"));
    assertFalse(new MinusculeMatcher("*list*", NameUtil.MatchingCaseSensitivity.ALL).matches("AbstractList"));
    assertFalse(new MinusculeMatcher("java.util.list", NameUtil.MatchingCaseSensitivity.ALL).matches("java.util.List"));
    assertFalse(new MinusculeMatcher("java.util.list", NameUtil.MatchingCaseSensitivity.ALL).matches("java.util.AbstractList"));
  }

  public void testMatchingFragments() {
    @NonNls String sample = "NoClassDefFoundException";
    //                       0 2    7  10   15    21
    assertOrderedEquals(new MinusculeMatcher("ncldfou*ion", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 1), TextRange.from(2, 2), TextRange.from(7, 1), TextRange.from(10, 3), TextRange.from(21, 3));

    sample = "doGet(HttpServletRequest, HttpServletResponse):void";
    //        0                     22
    assertOrderedEquals(new MinusculeMatcher("d*st", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 1), TextRange.from(22, 2));
    assertOrderedEquals(new MinusculeMatcher("doge*st", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 4), TextRange.from(22, 2));

    sample = "_test";
    assertOrderedEquals(new MinusculeMatcher("_", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 1));
    assertOrderedEquals(new MinusculeMatcher("_t", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 2));
  }

  public void testMatchingFragmentsSorted() {
    @NonNls String sample = "SWUPGRADEHDLRFSPR7TEST";
    //                       0        9  12
    assertOrderedEquals(new MinusculeMatcher("SWU*H*R", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 3), TextRange.from(9, 1), TextRange.from(12, 1));
  }

  public void testPreferCapsMatching() {
    String sample = "getCurrentUser";
    //               0   4     10
    assertOrderedEquals(new MinusculeMatcher("getCU", NameUtil.MatchingCaseSensitivity.NONE).matchingFragments(sample),
                        TextRange.from(0, 4), TextRange.from(10, 1));
  }

  public void "test plus/minus in the pattern should allow to be space-surrounded"() {
    assertMatches("a+b", "alpha+beta")
    assertMatches("a+b", "alpha_gamma+beta")
    assertMatches("a+b", "alpha + beta")
    assertMatches("Foo+", "Foo+Bar.txt")
    assertMatches("Foo+", "Foo + Bar.txt")
    assertMatches("a", "alpha+beta");
    assertMatches("*b", "alpha+beta");
    assertMatches("a + b", "alpha+beta");
    assertMatches("a+", "alpha+beta");
    assertDoesntMatch("a ", "alpha+beta");
    assertMatches("", "alpha+beta");
    assertMatches("*+ b", "alpha+beta");
    assertDoesntMatch("d+g", "alphaDelta+betaGamma");
    assertMatches("*d+g", "alphaDelta+betaGamma");

    assertMatches("a-b", "alpha-beta")
    assertMatches("a-b", "alpha - beta")
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
  }

  public void testPreferAdjacentWords() {
    assertPreference("*psfi", "PsiJavaFileBaseImpl", "PsiFileImpl", NameUtil.MatchingCaseSensitivity.NONE);
  }

  public void testPreferences() {
    assertPreference(" fb", "FooBar", "_fooBar", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference("*foo", "barFoo", "foobar");
    assertPreference("*fo", "barfoo", "barFoo");
    assertPreference("*fo", "barfoo", "foo");
    assertPreference("*fo", "asdfo", "Foo", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference(" sto", "StackOverflowError", "ArrayStoreException", NameUtil.MatchingCaseSensitivity.NONE);
    assertNoPreference(" EUC-", "x-EUC-TW", "EUC-JP", NameUtil.MatchingCaseSensitivity.FIRST_LETTER);
    assertPreference(" boo", "Boolean", "boolean", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference(" Boo", "boolean", "Boolean", NameUtil.MatchingCaseSensitivity.NONE);
    assertPreference("ob", "oci_bind_array_by_name", "obj");
    assertNoPreference("en", "ENABLED", "Enum", NameUtil.MatchingCaseSensitivity.NONE);
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

  public void testPreferBeforeSeparators() {
    assertPreference("*point", "getLocation(): Point", "getPoint(): Point", NameUtil.MatchingCaseSensitivity.NONE);
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

  public void testMeaningfulMatchingDegree() {
    assertTrue(new MinusculeMatcher(" EUC-", NameUtil.MatchingCaseSensitivity.FIRST_LETTER).matchingDegree("x-EUC-TW") > Integer.MIN_VALUE);
  }

  private static void assertPreference(@NonNls String pattern,
                                       @NonNls String less,
                                       @NonNls String more) {
    assertPreference(pattern, less, more, NameUtil.MatchingCaseSensitivity.FIRST_LETTER);
  }

  private static void assertPreference(@NonNls String pattern,
                                       @NonNls String less,
                                       @NonNls String more,
                                       NameUtil.MatchingCaseSensitivity sensitivity) {
    MinusculeMatcher matcher = new MinusculeMatcher(pattern, sensitivity);
    int iLess = matcher.matchingDegree(less);
    int iMore = matcher.matchingDegree(more);
    assertTrue(iLess + ">=" + iMore + "; " + less + ">=" + more, iLess < iMore);
  }

  private static void assertNoPreference(@NonNls String pattern,
                                       @NonNls String name1,
                                       @NonNls String name2,
                                       NameUtil.MatchingCaseSensitivity sensitivity) {
    MinusculeMatcher matcher = new MinusculeMatcher(pattern, sensitivity);
    assertEquals(matcher.matchingDegree(name1), matcher.matchingDegree(name2));
  }

 public void testSpeedSearchComparator() {
   final SpeedSearchComparator c = new SpeedSearchComparator(false, true);

   assertTrue(c.matchingFragments("a", "Ant") != null);
   assertTrue(c.matchingFragments("an", "Changes") != null);
   assertTrue(c.matchingFragments("a", "Changes") != null);
 }

  public void testFilePatterns() {
    assertMatches("groovy*.jar", "groovy-1.7.jar");
    assertDoesntMatch("*.ico", "a.i.c.o");
  }

  public void testUsingCapsMeansTheyShouldMatchCaps() {
    assertDoesntMatch("URLCl", "UrlClassLoader");
  }

  public void "test a capital after another capital may match a lowercase letter because shift was accidentally held too long"() {
    assertMatches("USerDefa", "UserDefaults")
    assertMatches("NSUSerDefa", "NSUserDefaults")
    assertMatches("NSUSER", "NSUserDefaults")
    assertMatches("NSUSD", "NSUserDefaults")
    assertMatches("NSUserDEF", "NSUserDefaults")
  }

  public void testPerformance() {
    System.out.println("test start: " + System.currentTimeMillis());
    @NonNls final String longName = "ThisIsAQuiteLongNameWithParentheses().Dots.-Minuses-_UNDERSCORES_digits239:colons:/slashes\\AndOfCourseManyLetters";
    final List<MinusculeMatcher> matching = new ArrayList<MinusculeMatcher>();
    final List<MinusculeMatcher> nonMatching = new ArrayList<MinusculeMatcher>();

    for (String s : ContainerUtil.ar("*", "*i", "*a", "*u", "T", "ti", longName, longName.substring(0, 20))) {
      matching.add(new MinusculeMatcher(s, NameUtil.MatchingCaseSensitivity.NONE));
    }
    for (String s : ContainerUtil.ar("A", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "tag")) {
      nonMatching.add(new MinusculeMatcher(s, NameUtil.MatchingCaseSensitivity.NONE));
    }

    System.out.println("measuring start: " + System.currentTimeMillis());
    PlatformTestUtil.startPerformanceTest("Matcher is slow", 4500, new ThrowableRunnable() {
      @Override
      public void run() {
        System.out.println("attempt start: " + System.currentTimeMillis());
        for (int i = 0; i < 100000; i++) {
          for (MinusculeMatcher matcher : matching) {
            assertTrue(matcher.toString(), matcher.matches(longName));
            matcher.matchingDegree(longName);
          }
          for (MinusculeMatcher matcher : nonMatching) {
            assertFalse(matcher.toString(), matcher.matches(longName));
          }
        }
        System.out.println("attempt end: " + System.currentTimeMillis());
      }
    }).cpuBound().assertTiming();
  }

  public void testOnlyUnderscoresPerformance() {
    PlatformTestUtil.startPerformanceTest("Matcher is exponential", 300, new ThrowableRunnable() {
      @Override
      public void run() {
        String small = StringUtil.repeat("_", 50);
        String big = StringUtil.repeat("_", small.length() + 1);
        assertMatches("*" + small, big);
        assertDoesntMatch("*" + big, small);
      }
    }).cpuBound().assertTiming();
  }

  public void testRepeatedLetterPerformance() {
    PlatformTestUtil.startPerformanceTest("Matcher is exponential", 300, new ThrowableRunnable() {
      @Override
      public void run() {
        String big = StringUtil.repeat("Aaaaaa", 50);
        assertMatches("aaaaaaaaaaaaaaaaaaaaaaaa", big);
        assertDoesntMatch("aaaaaaaaaaaaaaaaaaaaaaaab", big);
      }
    }).cpuBound().assertTiming();
  }

}
