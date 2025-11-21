// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.text.matching

import com.intellij.psi.codeStyle.AllOccurrencesMatcher.Companion.create
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.text.Matcher
import com.intellij.util.text.matching.KeyboardLayoutConverter
import com.intellij.util.text.matching.MatchedFragment
import com.intellij.util.text.matching.MatchingMode
import org.jetbrains.annotations.NonNls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NameUtilMatchingTest {
  @Test
  fun testSimpleCases() {
    assertMatches("N", "NameUtilTest")
    assertMatches("NU", "NameUtilTest")
    assertMatches("NUT", "NameUtilTest")
    assertMatches("NaUT", "NameUtilTest")
    assertDoesntMatch("NeUT", "NameUtilTest")
    assertDoesntMatch("NaUTa", "NameUtilTest")
    assertMatches("NaUtT", "NameUtilTest")
    assertMatches("NaUtT", "NameUtilTest")
    assertMatches("NaUtTe", "NameUtilTest")
    assertMatches("AACl", "AAClass")
    assertMatches("ZZZ", "ZZZZZZZZZZ")
  }

  @Test
  fun testEmptyPrefix() {
    assertMatches("", "")
    assertMatches("", "asdfs")
  }

  @Test
  fun testSkipWords() {
    assertMatches("nt", "NameUtilTest")
    assertMatches("repl map", "ReplacePathToMacroMap")
    assertMatches("replmap", "ReplacePathToMacroMap")
    assertMatches("CertificateEx", "CertificateEncodingException")
    assertDoesntMatch("ABCD", "AbstractButton.DISABLED_ICON_CHANGED_PROPERTY")

    assertMatches("templipa", "template_impl_template_list_panel")
    assertMatches("templistpa", "template_impl_template_list_panel")
  }

  @Test
  fun testSimpleCasesWithFirstLowercased() {
    assertMatches("N", "nameUtilTest")
    assertDoesntMatch("N", "anameUtilTest")
    assertMatches("NU", "nameUtilTest")
    assertDoesntMatch("NU", "anameUtilTest")
    assertMatches("NUT", "nameUtilTest")
    assertMatches("NaUT", "nameUtilTest")
    assertDoesntMatch("NeUT", "nameUtilTest")
    assertDoesntMatch("NaUTa", "nameUtilTest")
    assertMatches("NaUtT", "nameUtilTest")
    assertMatches("NaUtT", "nameUtilTest")
    assertMatches("NaUtTe", "nameUtilTest")
  }

  @Test
  fun testSpaceDelimiters() {
    assertMatches("Na Ut Te", "name util test")
    assertMatches("Na Ut Te", "name Util Test")
    assertDoesntMatch("Na Ut Ta", "name Util Test")
    assertMatches("na ut te", "name util test")

    assertMatches("na ut", "name_util_test")
    assertMatches("na te", "name_util_test")
    assertDoesntMatch("na ti", "name_util_test")

    assertDoesntMatch("alias imple", "alias simple")
    assertDoesntMatch("alias mple", "alias simple")
    assertDoesntMatch("alias nother", "alias another")
  }

  @Test
  fun testXMLCompletion() {
    assertDoesntMatch("N_T", "NameUtilTest")
    assertMatches("ORGS_ACC", "ORGS_POSITION_ACCOUNTABILITY")
    assertMatches("ORGS-ACC", "ORGS-POSITION_ACCOUNTABILITY")
    assertMatches("ORGS.ACC", "ORGS.POSITION_ACCOUNTABILITY")
  }

  @Test
  fun testStarFalsePositive() {
    assertDoesntMatch("ar*l*p", "AbstractResponseHandler")
  }

  @Test
  fun testUnderscoreStyle() {
    assertMatches("N_U_T", "NAME_UTIL_TEST")
    assertMatches("NUT", "NAME_UTIL_TEST")
    assertDoesntMatch("NUT", "NameutilTest")
  }

  @Test
  fun testAllUppercase() {
    assertMatches("NOS", "NetOutputStream")
  }

  @Test
  fun testCommonFileNameConventions() {
    // See IDEADEV-12310

    assertMatches("BLWN", "base_layout_without_navigation.xhtml")
    assertMatches("BLWN", "base-layout-without-navigation.xhtml")
    assertMatches("FC", "faces-config.xml")
    assertMatches("ARS", "activity_report_summary.jsp")
    assertMatches("AD", "arrow_down.gif")
    assertMatches("VL", "vehicle-listings.css")

    assertMatches("ARS.j", "activity_report_summary.jsp")
    assertDoesntMatch("ARS.j", "activity_report_summary.xml")
    assertDoesntMatch("ARS.j", "activity_report_summary_justsometingwrong.xml")

    assertMatches("foo.goo", "foo.bar.goo")
    assertDoesntMatch("*.ico", "sm.th.iks.concierge")
  }

  @Test
  fun testSpaceForAnyWordsInBetween() {
    assertMatches("fo bar", "fooBar")
    assertMatches("foo bar", "fooBar")
    assertMatches("foo bar", "fooGooBar")
    assertMatches("foo bar", "fooGoo bar")
    assertDoesntMatch(" b", "fbi")
    assertDoesntMatch(" for", "performAction")
    assertTrue(caseInsensitiveMatcher(" us").matches("getUsage"))
    assertTrue(caseInsensitiveMatcher(" us").matches("getMyUsage"))
  }

  @Test
  fun testFilenamesWithDotsAndSpaces() {
    assertMatches("Google Test.html", "Google Test Test.cc.html")
    assertMatches("Google.html", "Google Test Test.cc.html")
    assertMatches("Google .html", "Google Test Test.cc.html")
    assertMatches("Google Test*.html", "Google Test Test.cc.html")
  }

  @Test
  fun testStartDot() {
    assertMatches("A*.html", "A.html")
    assertMatches("A*.html", "Abc.html")
    assertMatches("A*.html", "after.html")
    assertDoesntMatch("A*.html", "10_after.html")
  }

  @Test
  fun testIDEADEV15503() {
    assertMatches("AR.jsp", "add_relationship.jsp")
    assertMatches("AR.jsp", "advanced_rule.jsp")
    assertMatches("AR.jsp", "alarm_reduction.jsp")
    assertMatches("AR.jsp", "audiot_report.jsp")
    assertMatches("AR.jsp", "audiot_r.jsp")

    assertMatches("AR.jsp", "alarm_rule_action.jsp")
    assertMatches("AR.jsp", "alarm_rule_admin.jsp")
    assertMatches("AR.jsp", "alarm_rule_administration.jsp")
    assertMatches("AR.jsp", "alarm_rule_controller.jsp")
    assertMatches("AR.jsp", "alarm_rule_frame.jsp")
    assertMatches("AR.jsp", "alarm_rule_severity.jsp")

    assertMatches("AR.jsp", "AddRelationship.jsp")
    assertMatches("AR.jsp", "AdvancedRule.jsp")
    assertMatches("AR.jsp", "AlarmReduction.jsp")
    assertMatches("AR.jsp", "AudiotReport.jsp")
    assertMatches("AR.jsp", "AudiotR.jsp")

    assertMatches("AR.jsp", "AlarmRuleAction.jsp")
    assertMatches("AR.jsp", "AlarmRuleAdmin.jsp")
    assertMatches("AR.jsp", "AlarmRuleAdministration.jsp")
    assertMatches("AR.jsp", "AlarmRuleController.jsp")
    assertMatches("AR.jsp", "AlarmRuleFrame.jsp")
    assertMatches("AR.jsp", "AlarmRuleSeverity.jsp")
  }

  @Test
  fun testSkipDot() {
    assertMatches("ja", "jquery.autocomplete.js")
    assertDoesntMatch("ja.js", "jquery.autocomplete.js")
    assertMatches("jajs", "jquery.autocomplete.js")
    assertMatches("jjs", "jquery.autocomplete.js")
    assertMatches("j.js", "jquery.autocomplete.js")
    assertDoesntMatch("j.ajs", "jquery.autocomplete.js")
    assertMatches("oracle.bnf", "oracle-11.2.bnf")
    assertMatches("*foo.*bar", "foo.b.bar")
  }

  @Test
  fun testNoExtension() {
    assertMatches("#.p", "#.php")
    assertMatches("#", "#.php")
    assertMatches("a", "a.php")
  }

  @Test
  fun testIgnoreCaseWhenCompleteMatch() {
    assertMatches("comboBox", "combobox")
    assertMatches("combobox", "comboBox")
  }

  @Test
  fun testStartsWithDot() {
    assertMatches(".foo", ".foo")
  }

  @Test
  fun testProperDotEscaping() {
    assertMatches("*inspection*.pro", "InspectionsBundle.properties")
    assertDoesntMatch("*inspection*.pro", "InspectionsInProgress.png")
  }

  @Test
  fun testLeadingUnderscore() {
    assertDoesntMatch("form", "_form.html.erb")
    assertMatches("_form", "_form.html.erb")
    assertMatches("_form", "__form")
    assertTrue(firstLetterMatcher("_form").matches("__form"))
  }

  @Test
  fun testLowerCaseWords() {
    assertMatches("uct", "unit_controller_test")
    assertMatches("unictest", "unit_controller_test")
    assertMatches("uc", "unit_controller_test")
    assertDoesntMatch("nc", "unit_controller_test")
    assertDoesntMatch("utc", "unit_controller_test")
  }

  @Test
  fun testObjectiveCCases() {
    assertMatches("h*:", "h:aaa")
    assertMatches("h:", "h:aaa")
    assertMatches("text:sh", "textField:shouldChangeCharactersInRange:replacementString:")
    assertMatches("abc", "aaa:bbb:ccc")
    assertMatches("textField:sh", "textField:shouldChangeCharactersInRange:replacementString:")
    assertMatches("text*:sh", "textField:shouldChangeCharactersInRange:replacementString:")
  }

  @Test
  fun testMiddleMatchingMinimumTwoConsecutiveLettersInWordMiddle() {
    assertMatches("*fo", "reformat")
    assertMatches("*f", "reFormat")
    assertMatches("*f", "format")
    assertMatches("*f", "Format")
    assertMatches("*Stri", "string")
    assertMatches("*f", "reformat")
    assertMatches("*f", "reformatCode")
    assertDoesntMatch("*fc", "reformatCode")
    assertDoesntMatch("*foc", "reformatCode")
    assertMatches("*forc", "reformatCode")
    assertDoesntMatch("*sTC", "LazyClassTypeConstructor")

    assertDoesntMatch("*Icon", "LEADING_CONSTRUCTOR")
    assertMatches("*I", "LEADING_CONSTRUCTOR")
    assertMatches("*i", "LEADING_CONSTRUCTOR")
    assertMatches("*in", "LEADING_CONSTRUCTOR")
    assertMatches("*ing", "LEADING_CONSTRUCTOR")
    assertDoesntMatch("*inc", "LEADING_CONSTRUCTOR")
    assertDoesntMatch("*ico", "drawLinePickedOut")

    assertMatches("*l", "AppDelegate")
    assertMatches("*le", "AppDelegate")
    assertMatches("*leg", "AppDelegate")
  }

  @Test
  fun testMiddleMatchingUnderscore() {
    assertMatches("*_dark", "collapseAll_dark.png")
    assertMatches("*_dark.png", "collapseAll_dark.png")
    assertMatches("**_dark.png", "collapseAll_dark.png")
    assertTrue(firstLetterMatcher("*_DARK").matches("A_DARK.png"))
  }

  @Test
  fun testMiddleMatching() {
    assertMatches("*zz*", "ListConfigzzKey")
    assertMatches("*zz", "ListConfigzzKey")
    assertTrue(caseInsensitiveMatcher("*old").matches("folder"))
    assertMatches("SWU*H*7", "SWUpgradeHdlrFSPR7Test")
    assertMatches("SWU*H*R", "SWUpgradeHdlrFSPR7Test")
    assertMatches("SWU*H*R", "SWUPGRADEHDLRFSPR7TEST")
    assertMatches("*git", "GitBlaBla")
    assertMatches("*Git", "GitBlaBla")
    assertDoesntMatch("*get*A", "getClass")
    assertMatches("*git", "BlaGitBla")
    assertMatches("*Git", "BlaGitBla")
    assertTrue(firstLetterMatcher("*Git").matches("BlagitBla"))
    assertMatches("*git", "BlagitBla")
    assertMatches("*Git*", "AtpGenerationItem")
    assertMatches("Collec*Util*", "CollectionUtils")
    assertMatches("Collec*Util*", "CollectionUtilsTest")
    assertTrue(caseInsensitiveMatcher("*us").matches("usage"))
    assertTrue(caseInsensitiveMatcher(" us").matches("usage"))
    assertTrue(caseInsensitiveMatcher(" fo. ba").matches("getFoo.getBar"))
    assertMatches(" File. sepa", "File.separator")
    assertMatches(" File. sepa", "File._separator")
    assertMatches(" File. _sepa", "File._separator")
    assertMatches(" _fo", "_foo")
    assertMatches("*BComp", "BaseComponent")
  }

  @Test
  fun testUppercasePrefixWithMiddleMatching() {
    assertMatches("*OS", "ios")
    assertMatches("*OS", "IOS")
    assertMatches("*OS", "osx")
    assertMatches("*OS", "OSX")

    assertTrue(firstLetterMatcher("*I").matches("ID"))
    assertFalse(firstLetterMatcher("*I").matches("id"))
  }

  @Test
  fun testAsteriskEndingInsideUppercaseWord() {
    assertMatches("*LRUMap", "SLRUMap")
  }

  @Test
  fun testMiddleMatchingFirstLetterSensitive() {
    assertTrue(firstLetterMatcher(" cl").matches("getClass"))
    assertFalse(firstLetterMatcher(" EUC-").matches("x-EUC-TW"))
    assertTrue(firstLetterMatcher(" a").matches("aaa"))
    assertFalse(firstLetterMatcher(" a").matches("Aaa"))
    assertFalse(firstLetterMatcher(" a").matches("Aaa"))
    assertFalse(firstLetterMatcher(" _bl").matches("_top"))
    assertFalse(firstLetterMatcher("*Ch").matches("char"))
    assertTrue(firstLetterMatcher("*Codes").matches("CFLocaleCopyISOCountryCodes"))
    assertFalse(firstLetterMatcher("*codes").matches("CFLocaleCopyISOCountryCodes"))
    assertTrue(firstLetterMatcher("*codes").matches("getCFLocaleCopyISOCountryCodes"))
    assertTrue(firstLetterMatcher("*Bcomp").matches("BaseComponent"))
  }

  @Test
  fun testPreferCamelHumpsToAllUppers() {
    assertPreference("ProVi", "PROVIDER", "ProjectView")
  }

  @Test
  fun testSpaceInCompletionPrefix() {
    assertTrue(caseInsensitiveMatcher("create ").matches("create module"))
  }

  @Test
  fun testLong() {
    assertMatches("Product.findByDateAndNameGreaterThanEqualsAndQualityGreaterThanEqual",
                  "Product.findByDateAndNameGreaterThanEqualsAndQualityGreaterThanEqualsIntellijIdeaRulezzz")
  }

  @Test
  fun testUpperCaseMatchesLowerCase() {
    assertMatches("ABC_B.C", "abc_b.c")
  }

  @Test
  fun testLowerCaseHumps() {
    assertMatches("foo", "foo")
    assertDoesntMatch("foo", "fxoo")
    assertMatches("foo", "fOo")
    assertMatches("foo", "fxOo")
    assertMatches("foo", "fXOo")
    assertMatches("fOo", "foo")
    assertDoesntMatch("fOo", "FaOaOaXXXX")
    assertMatches("ncdfoe", "NoClassDefFoundException")
    assertMatches("fob", "FOO_BAR")
    assertMatches("fo_b", "FOO_BAR")
    assertMatches("fob", "FOO BAR")
    assertMatches("fo b", "FOO BAR")
    assertMatches("AACl", "AAClass")
    assertMatches("ZZZ", "ZZZZZZZZZZ")
    assertMatches("em", "emptyList")
    assertMatches("bui", "BuildConfig.groovy")
    assertMatches("buico", "BuildConfig.groovy")
    assertMatches("buico.gr", "BuildConfig.groovy")
    assertMatches("bui.gr", "BuildConfig.groovy")
    assertMatches("*fz", "azzzfzzz")

    assertMatches("WebLogic", "Weblogic")
    assertMatches("WebLOgic", "WebLogic")
    assertMatches("WEbLogic", "WebLogic")
    assertDoesntMatch("WebLogic", "Webologic")

    assertMatches("Wlo", "WebLogic")
  }

  @Test
  fun testFinalSpace() {
    assertMatches("a ", "alpha + beta")
    assertMatches("a ", "a ")
    assertMatches("a ", "a")
    assertMatches("GrDebT ", "GroovyDebuggerTest")
    assertDoesntMatch("grdebT ", "GroovyDebuggerTest")
    assertDoesntMatch("grdebt ", "GroovyDebuggerTest")
    assertMatches("Foo ", "Foo")
    assertDoesntMatch("Foo ", "FooBar")
    assertDoesntMatch("Foo ", "Foox")
    assertDoesntMatch("Collections ", "CollectionSplitter")
    assertMatches("CollectionS ", "CollectionSplitter")
    assertMatches("*run ", "in Runnable.run")

    assertDoesntMatch("*l ", "AppDelegate")
    assertDoesntMatch("*le ", "AppDelegate")
    assertDoesntMatch("*leg ", "AppDelegate")
  }

  @Test
  fun testDigits() {
    assertMatches("foba4", "FooBar4")
    assertMatches("foba", "Foo4Bar")
    assertMatches("*TEST-* ", "TEST-001")
    assertMatches("*TEST-0* ", "TEST-001")
    assertMatches("*v2 ", "VARCHAR2")
    assertMatches("smart8co", "SmartType18CompletionTest")
    assertMatches("smart8co", "smart18completion")
  }

  @Test
  fun testDoNotAllowDigitsBetweenMatchingDigits() {
    assertDoesntMatch("*012", "001122")
    assertMatches("012", "0a1_22")
  }

  @Test
  fun testSpecialSymbols() {
    assertMatches("a@b", "a@bc")
    assertDoesntMatch("*@in", "a int")

    assertMatches("a/text", "a/Text")
    assertMatches("a/text", "a/bbbText")
  }

  @Test
  fun testMinusculeFirstLetter() {
    assertTrue(firstLetterMatcher("WebLogic").matches("WebLogic"))
    assertFalse(firstLetterMatcher("webLogic").matches("WebLogic"))
    assertTrue(firstLetterMatcher("cL").matches("class"))
    assertTrue(firstLetterMatcher("CL").matches("Class"))
    assertTrue(firstLetterMatcher("Cl").matches("CoreLoader"))
    assertFalse(firstLetterMatcher("abc").matches("_abc"))
  }

  @Test
  fun testMinusculeAllImportant() {
    assertTrue(NameUtil.buildMatcher("WebLogic", MatchingMode.MATCH_CASE).matches("WebLogic"))
    assertFalse(NameUtil.buildMatcher("webLogic", MatchingMode.MATCH_CASE).matches("weblogic"))
    assertFalse(NameUtil.buildMatcher("FOO", MatchingMode.MATCH_CASE).matches("foo"))
    assertFalse(NameUtil.buildMatcher("foo", MatchingMode.MATCH_CASE).matches("fOO"))
    assertFalse(NameUtil.buildMatcher("Wl", MatchingMode.MATCH_CASE).matches("WebLogic"))
    assertTrue(NameUtil.buildMatcher("WL", MatchingMode.MATCH_CASE).matches("WebLogic"))
    assertFalse(NameUtil.buildMatcher("WL", MatchingMode.MATCH_CASE).matches("Weblogic"))
    assertFalse(NameUtil.buildMatcher("WL", MatchingMode.MATCH_CASE).matches("weblogic"))
    assertFalse(NameUtil.buildMatcher("webLogic", MatchingMode.MATCH_CASE).matches("WebLogic"))
    assertFalse(NameUtil.buildMatcher("Str", MatchingMode.MATCH_CASE).matches("SomeThingRidiculous"))
    assertFalse(NameUtil.buildMatcher("*list*", MatchingMode.MATCH_CASE).matches("List"))
    assertFalse(NameUtil.buildMatcher("*list*", MatchingMode.MATCH_CASE).matches("AbstractList"))
    assertFalse(NameUtil.buildMatcher("java.util.list", MatchingMode.MATCH_CASE).matches("java.util.List"))
    assertFalse(NameUtil.buildMatcher("java.util.list", MatchingMode.MATCH_CASE).matches("java.util.AbstractList"))
  }

  @Test
  fun testMatchingFragments() {
    @NonNls var sample = "NoClassDefFoundException"
    //                    0 2    7  10   15    21
    assertEquals(NameUtil.buildMatcher("ncldfou*ion", MatchingMode.IGNORE_CASE).match(sample),
                 listOf(MatchedFragment(0, 1), MatchedFragment(2, 4), MatchedFragment(7, 8), MatchedFragment(10, 13), MatchedFragment(21, 24)))

    sample = "doGet(HttpServletRequest, HttpServletResponse):void"
    //        0                     22
    assertEquals(NameUtil.buildMatcher("d*st", MatchingMode.IGNORE_CASE).match(sample),
                 listOf(MatchedFragment(0, 1), MatchedFragment(22, 24)))
    assertEquals(NameUtil.buildMatcher("doge*st", MatchingMode.IGNORE_CASE).match(sample),
                 listOf(MatchedFragment(0, 4), MatchedFragment(22, 24)))

    sample = "_test"
    assertEquals(NameUtil.buildMatcher("_", MatchingMode.IGNORE_CASE).match(sample),
                 listOf(MatchedFragment(0, 1)))
    assertEquals(NameUtil.buildMatcher("_t", MatchingMode.IGNORE_CASE).match(sample),
                 listOf(MatchedFragment(0, 2)))
  }

  @Test
  fun testMatchingFragmentsSorted() {
    @NonNls val sample = "SWUPGRADEHDLRFSPR7TEST"
    //                    0        9  12
    assertEquals(NameUtil.buildMatcher("SWU*H*R", MatchingMode.IGNORE_CASE).match(sample),
                 listOf(MatchedFragment(0, 3), MatchedFragment(9, 10), MatchedFragment(12, 13)))
  }

  @Test
  fun testPreferCapsMatching() {
    val sample = "getCurrentUser"
    //            0   4     10
    assertEquals(NameUtil.buildMatcher("getCU", MatchingMode.IGNORE_CASE).match(sample),
                 listOf(MatchedFragment(0, 4), MatchedFragment(10, 11)))
  }

  @Test
  fun testPlusOrMinusInThePatternShouldAllowToBeSpaceSurrounded() {
    assertMatches("a+b", "alpha+beta")
    assertMatches("a+b", "alpha_gamma+beta")
    assertMatches("a+b", "alpha + beta")
    assertMatches("Foo+", "Foo+Bar.txt")
    assertMatches("Foo+", "Foo + Bar.txt")
    assertMatches("a", "alpha+beta")
    assertMatches("*b", "alpha+beta")
    assertMatches("a + b", "alpha+beta")
    assertMatches("a+", "alpha+beta")
    assertDoesntMatch("a ", "alpha+beta")
    assertMatches("", "alpha+beta")
    assertMatches("*+ b", "alpha+beta")
    assertDoesntMatch("d+g", "alphaDelta+betaGamma")
    assertMatches("*d+g", "alphaDelta+betaGamma")

    assertMatches("a-b", "alpha-beta")
    assertMatches("a-b", "alpha - beta")
  }

  @Test
  fun testMatchingDegree() {
    assertPreference("jscote", "JsfCompletionTest", "JSCompletionTest", MatchingMode.IGNORE_CASE)
    assertPreference("OCO", "OneCoolObject", "OCObject")
    assertPreference("MUp", "MavenUmlProvider", "MarkUp")
    assertPreference("MUP", "MarkUp", "MavenUmlProvider")
    assertPreference("CertificateExce", "CertificateEncodingException", "CertificateException")
    assertPreference("boo", "Boolean", "boolean", MatchingMode.IGNORE_CASE)
    assertPreference("Boo", "boolean", "Boolean", MatchingMode.IGNORE_CASE)
    assertPreference("getCU", "getCurrentSomething", "getCurrentUser")
    assertPreference("cL", "class", "coreLoader")
    assertPreference("cL", "class", "classLoader")
    assertPreference("inse", "InstrumentationError", "intSet", MatchingMode.IGNORE_CASE)
    assertPreference("String", "STRING", "String", MatchingMode.IGNORE_CASE)
    assertPreference("*String", "STRING", "String", MatchingMode.IGNORE_CASE)
  }

  @Test
  fun testPreferAdjacentWords() {
    assertPreference("*psfi", "PsiJavaFileBaseImpl", "PsiFileImpl", MatchingMode.IGNORE_CASE)
  }

  @Test
  fun testPreferMatchesToTheEnd() {
    assertPreference("*e", "fileIndex", "file", MatchingMode.IGNORE_CASE)
  }

  @Test
  fun testPreferences() {
    assertPreference(" fb", "FooBar", "_fooBar", MatchingMode.IGNORE_CASE)
    assertPreference("*foo", "barFoo", "foobar")
    assertPreference("*fo", "barfoo", "barFoo")
    assertPreference("*fo", "barfoo", "foo")
    assertPreference("*fo", "asdfo", "Foo", MatchingMode.IGNORE_CASE)
    assertPreference(" sto", "StackOverflowError", "ArrayStoreException", MatchingMode.IGNORE_CASE)
    assertPreference(" EUC-", "x-EUC-TW", "EUC-JP", MatchingMode.FIRST_LETTER)
    assertPreference(" boo", "Boolean", "boolean", MatchingMode.IGNORE_CASE)
    assertPreference(" Boo", "boolean", "Boolean", MatchingMode.IGNORE_CASE)
    assertPreference("ob", "oci_bind_array_by_name", "obj")
    assertNoPreference("en", "ENABLED", "Enum", MatchingMode.IGNORE_CASE)
  }

  @Test
  fun testHonorFirstLetterCaseInCompletion() {
    val matcher = NameUtil.buildMatcher("*pim", MatchingMode.IGNORE_CASE)
    val iLess = matcher.matchingDegree("PImageDecoder", true)
    val iMore = matcher.matchingDegree("posIdMap", true)
    assertTrue(iLess < iMore)
  }

  @Test
  fun testPreferWordBoundaryMatch() {
    assertPreference("*ap", "add_profile", "application", MatchingMode.IGNORE_CASE)
    assertPreference("*les", "configureByFiles", "getLookupElementStrings")
    assertPreference("*les", "configureByFiles", "getLookupElementStrings", MatchingMode.IGNORE_CASE)
    assertPreference("*ea", "LEADING", "NORTH_EAST", MatchingMode.IGNORE_CASE)

    assertPreference("*Icon", "isControlKeyDown", "getErrorIcon", MatchingMode.IGNORE_CASE)
    assertPreference("*icon", "isControlKeyDown", "getErrorIcon", MatchingMode.IGNORE_CASE)

    assertPreference("*Icon", "getInitControl", "getErrorIcon", MatchingMode.IGNORE_CASE)
    assertPreference("*icon", "getInitControl", "getErrorIcon", MatchingMode.IGNORE_CASE)
  }

  @Test
  fun testPreferNoWordSkipping() {
    assertPreference("CBP", "CustomProcessBP", "ComputationBatchProcess", MatchingMode.IGNORE_CASE)
  }

  @Test
  fun testWordLengthDoesNotMatter() {
    assertNoPreference("PropComp", "PropertyComponent", "PropertiesComponent", MatchingMode.IGNORE_CASE)
  }

  @Test
  fun testMatchStartDoesntMatterForDegree() {
    assertNoPreference(" path", "getAbsolutePath", "findPath", MatchingMode.FIRST_LETTER)
  }

  @Test
  fun testPreferStartMatching() {
    assertPreference("*tree", "FooTree", "Tree", MatchingMode.IGNORE_CASE)
  }

  @Test
  fun testPreferContiguousMatching() {
    assertPreference("*mappablejs", "mappable-js.scope.js", "MappableJs.js", MatchingMode.IGNORE_CASE)
  }

  @Test
  fun testMeaningfulMatchingDegree() {
    assertTrue(caseInsensitiveMatcher(" EUC-").matchingDegree("x-EUC-TW") > Int.MIN_VALUE)
  }

  @Test
  fun testFilePatterns() {
    assertMatches("groovy*.jar", "groovy-1.7.jar")
    assertDoesntMatch("*.ico", "a.i.c.o")
  }

  @Test
  fun testCapsMayMatchNonCaps() {
    assertMatches("PDFRe", "PdfRenderer")
    assertMatches("*pGETPartTimePositionInfo", "dbo.pGetPartTimePositionInfo.sql")
  }

  @Test
  fun testACapitalAfterAnotherCapitalMayMatchALowercaseLetterBecauseShiftWasAccidentallyHeldTooLong() {
    assertMatches("USerDefa", "UserDefaults")
    assertMatches("NSUSerDefa", "NSUserDefaults")
    assertMatches("NSUSER", "NSUserDefaults")
    assertMatches("NSUSD", "NSUserDefaults")
    assertMatches("NSUserDEF", "NSUserDefaults")
  }

  @Test
  fun testCyrillicMatch() {
    assertMatches("ыек", "String")
  }

  @Test
  fun testMatchingAllOccurrences() {
    val text = "some text"
    val matcher = create("*e", MatchingMode.IGNORE_CASE, "", KeyboardLayoutConverter.noop)
    assertEquals(matcher.match(text), listOf(MatchedFragment(3, 4), MatchedFragment(6, 7)))
  }

  @Test
  fun testCamelHumpWinsOverConsecutiveCaseMismatch() {
    assertEquals(3, NameUtil.buildMatcher("GEN", MatchingMode.IGNORE_CASE).match("GetExtendedName")!!.size)

    assertPreference("GEN", "GetName", "GetExtendedName")
    assertPreference("*GEN", "GetName", "GetExtendedName")
  }

  @Test
  fun testLowerCaseAfterCamels() {
    assertMatches("LSTMa", "LineStatusTrackerManager")
  }

  @Test
  fun testProperties() {
    assertMatches("*pro", "spring.activemq.pool.configuration.reconnect-on-exception")
  }

  companion object {
    private fun caseInsensitiveMatcher(pattern: String): MinusculeMatcher {
      return NameUtil.buildMatcher(pattern, MatchingMode.IGNORE_CASE)
    }

    private fun firstLetterMatcher(pattern: String): Matcher {
      return NameUtil.buildMatcher(pattern, MatchingMode.FIRST_LETTER)
    }

    private fun assertMatches(@NonNls pattern: @NonNls String, @NonNls name: @NonNls String) {
      assertTrue(caseInsensitiveMatcher(pattern).matches(name), "$pattern doesn't match $name!!!")
    }

    private fun assertDoesntMatch(@NonNls pattern: @NonNls String, @NonNls name: @NonNls String) {
      assertFalse(caseInsensitiveMatcher(pattern).matches(name), "$pattern matches $name!!!")
    }

    private fun assertPreference(
      @NonNls pattern: @NonNls String,
      @NonNls less: @NonNls String,
      @NonNls more: @NonNls String,
      matchingMode: MatchingMode = MatchingMode.FIRST_LETTER,
    ) {
      assertPreference(NameUtil.buildMatcher(pattern, matchingMode), less, more)
    }

    private fun assertPreference(matcher: MinusculeMatcher, less: String, more: String) {
      assertPreference(less, more) { name -> matcher.matchingDegree(name) }
    }

    private fun assertPreference(less: String, more: String, matchingDegree: (String) -> Int) {
      val iLess = matchingDegree(less)
      val iMore = matchingDegree(more)
      assertTrue(iLess < iMore, "$iLess>=$iMore; $less>=$more")
    }

    private fun assertNoPreference(
      @NonNls pattern: @NonNls String,
      @NonNls name1: @NonNls String,
      @NonNls name2: @NonNls String,
      matchingMode: MatchingMode,
    ) {
      val matcher = NameUtil.buildMatcher(pattern, matchingMode)
      assertEquals(matcher.matchingDegree(name1), matcher.matchingDegree(name2))
    }
  }
}
