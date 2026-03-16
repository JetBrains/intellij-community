// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.matching.MatchingMode;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

@PerformanceUnitTest
public class MinusculeMatcherPerformanceTest extends TestCase {
  public void testPerformance() {
    @NonNls final String longName = "ThisIsAQuiteLongNameWithParentheses().Dots.-Minuses-_UNDERSCORES_digits239:colons:/slashes\\AndOfCourseManyLetters";
    final List<MinusculeMatcher> matching = new ArrayList<>();
    final List<MinusculeMatcher> nonMatching = new ArrayList<>();

    for (String s : ContainerUtil.ar("*", "*i", "*a", "*u", "T", "ti", longName, longName.substring(0, 20))) {
      matching.add(NameUtil.buildMatcher(s, MatchingMode.IGNORE_CASE));
    }
    for (String s : ContainerUtil.ar("A", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "tag")) {
      nonMatching.add(NameUtil.buildMatcher(s, MatchingMode.IGNORE_CASE));
    }

    Benchmark.newBenchmark("Matching", () -> {
      for (int i = 0; i < 100_000; i++) {
        for (MinusculeMatcher matcher : matching) {
          assertTrue(matcher.matches(longName));
          matcher.matchingDegree(longName);
        }
        for (MinusculeMatcher matcher : nonMatching) {
          assertFalse(matcher.matches(longName));
        }
      }
    }).runAsStressTest().start();
  }

  public void testOnlyUnderscoresPerformance() {
    String small = StringUtil.repeat("_", 50000);
    String smallWildcard = "*" + small;
    MinusculeMatcher smallMatcher = NameUtil.buildMatcher(smallWildcard, MatchingMode.IGNORE_CASE);
    String big = StringUtil.repeat("_", small.length() + 1);
    MinusculeMatcher bigMatcher = NameUtil.buildMatcher("*" + big, MatchingMode.IGNORE_CASE);
    Benchmark.newBenchmark(getName(), () -> {
      for (int i = 0; i < 10_000; i++) {
        assertMatches(smallMatcher, big);
        assertDoesntMatch(bigMatcher, small);
      }
    }).runAsStressTest().start();
  }

  public void testRepeatedLetterPerformance() {
    String big = StringUtil.repeat("Aaaaaa", 50000);
    MinusculeMatcher matcher = NameUtil.buildMatcher("aaaaaaaaaaaaaaaaaaaaaaaa", MatchingMode.IGNORE_CASE);
    MinusculeMatcher mismatchMatcher = NameUtil.buildMatcher("aaaaaaaaaaaaaaaaaaaaaaaab", MatchingMode.IGNORE_CASE);
    Benchmark.newBenchmark(getName(), () -> {
      for (int i = 0; i < 1_000; i++) {
        assertMatches(matcher, big);
        assertDoesntMatch(mismatchMatcher, big);
      }
    }).runAsStressTest().start();
  }

  public void testMatchingLongHtmlWithShortHtml() {
    String html =
      "<html> <body> <H2> <FONT SIZE=\"-1\"> com.sshtools.cipher</FONT> <BR> Class AES128Cbc</H2> <PRE> java.lang.Object   <IMG SRC=\"../../../resources/inherit.gif\" ALT=\"extended by\">com.maverick.ssh.cipher.SshCipher       <IMG SRC=\"../../../resources/inherit.gif\" ALT=\"extended by\">com.maverick.ssh.crypto.engines.CbcBlockCipher           <IMG SRC=\"../../../resources/inherit.gif\" ALT=\"extended by\"><B>com.sshtools.cipher.AES128Cbc</B> </PRE> <HR> <DL> <DT>public class <B>AES128Cbc</B><DT>extends com.maverick.ssh.crypto.engines.CbcBlockCipher</DL>  <P> This cipher can optionally be added to the J2SSH Maverick API. To add  the ciphers from this package simply add them to the <A HREF=\"../../../com/maverick/ssh2/Ssh2Context.html\" title=\"class in com.maverick.ssh2\"><CODE>Ssh2Context</CODE></A>  <blockquote><pre>   import com.sshtools.cipher.*;   </pre></blockquote> <P>  <P> <DL> <DT><B>Version:</B></DT>   <DD>Revision: 1.20</DD> </DL> <HR> </body> </html>";
    MinusculeMatcher matcher = NameUtil.buildMatcher("*<p> aaa <div id=\"a", MatchingMode.IGNORE_CASE);
    Benchmark.newBenchmark(getName(), () -> {
      for (int i = 0; i < 10_000; i++) {
        assertDoesntMatch(matcher, html);
      }
    }).runAsStressTest().start();
  }

  public void testMatchingLongStringWithAnotherLongStringWhereOnlyEndsDiffer() {
    String pattern = "*Then the large string is '{asdbsfafds adsfadasdfasdfasdfasdfasdfasdfsfasf adsfasdf sfasdfasdfasdfasdfasdfasdfd adsfadsfsafd adsfafdadsfsdfasdf sdf asdfasdfasfadsfasdfasfd asdfafd fasdfasdfasdfdsfas dadsfasfadsfafdsafddf  dsf dsasdfasdfsdafsdfsdfsdfasdffafdadfafafasdfasdf asdfasdfasdfasdfasdfasdfasdfasdfaasdfsdfasdfds adfafddfas aa afds}' is sent into the abyss\nThen";
    String name =     "Then the large string is '{asdbsfafds adsfadasdfasdfasdfasdfasdfasdfsfasf adsfasdf sfasdfasdfasdfasdfasdfasdfd adsfadsfsafd adsfafdadsfsdfasdf sdf asdfasdfasfadsfasdfasfd asdfafd fasdfasdfasdfdsfas dadsfasfadsfafdsafddf  dsf dsasdfasdfsdafsdfsdfsdfasdffafdadfafafasdfasdf asdfasdfasdfasdfasdfasdfasdfasdfaasdfsdfasdfds adfafddfas aa afds}' is sent into the abyss\nTh' is sent into the abyss";
    assertDoesntMatchFast(pattern, name, "matching1");

    pattern = "findFirstAdjLoanPlanTemplateByAdjLoanPlan_AdjLoanProgram_AdjLoanProgramCodeAndTemplateVersions";
    name =    "findFirstAdjLoanPlanTemplateByAdjLoanPlan_AdjLoanProgram_AdjLoanProgramCodeAndTemplateVersion_TemplateVersionCode";
    assertDoesntMatchFast(pattern, name, "matching2");

    pattern = "tip.how.to.select.a.thing.and.that.selected.things.are.shown.as.bold";
    name    = "tip.how.to.select.a.thing.and.that.selected.things.are.shown.as.bolid";
    assertDoesntMatchFast(pattern, name, "matching3");
  }

  private void assertDoesntMatchFast(String pattern, String name, String subTestName) {
    MinusculeMatcher matcher = NameUtil.buildMatcher(pattern, MatchingMode.IGNORE_CASE);
    Benchmark.newBenchmark(getName(), () -> {
      for (int i = 0; i < 10_000; i++) {
        assertDoesntMatch(matcher, name);
      }
    }).runAsStressTest().startAsSubtest(subTestName);
  }

  public void testMatchingLongRuby() {
    String name =
      "# -*- coding: utf-8 -*-$:.unshift(\"/Library/RubyMotion/lib\")require 'motion/project'Motion::Project::App.setup do |app|  # Use `rake config' to see complete project settings.  app.sdk_version = '4.3'  app.frameworks -= ['UIKit']end";
    MinusculeMatcher matcher = NameUtil.buildMatcher(
      "*# -*- coding: utf-8 -*-$:. unshift(\"/Library/RubyMotion/lib\")require 'motion/project'Motion::Project::App. setup do |app|  # Use `rake config' to see complete project settings.   app. sdk_version = '4. 3'end", MatchingMode.IGNORE_CASE);
    Benchmark.newBenchmark(getName(), () -> {
      for (int i = 0; i < 100_000; i++) {
        assertDoesntMatch(matcher, name);
      }
    }).runAsStressTest().start();
  }

  public void testLongStringMatchingWithItself() {
    String s =
      "the class with its attributes mapped to fields of records parsed by an {@link AbstractParser} or written by an {@link AbstractWriter}.";
    String substring = s.substring(0, 10);
    MinusculeMatcher matcher = NameUtil.buildMatcher(s, MatchingMode.IGNORE_CASE);
    MinusculeMatcher matcherWildcard = NameUtil.buildMatcher("*" + s, MatchingMode.IGNORE_CASE);
    Benchmark.newBenchmark(getName(), () -> {
      for (int i = 0; i < 100_000; i++) {
        assertMatches(matcher, s);
        assertMatches(matcherWildcard, s);

        assertPreference(matcher, substring, s);
        assertPreference(matcherWildcard, substring, s);
      }
    }).runAsStressTest().start();
  }

  private static void assertMatches(MinusculeMatcher matcher, @NonNls String name) {
    if (!matcher.matches(name)) {
      fail(matcher.getPattern() + " doesn't match " + name + "!!!");
    }
  }

  private static void assertDoesntMatch(MinusculeMatcher matcher, @NonNls String name) {
    if (matcher.matches(name)) {
      fail(matcher.getPattern() + " matches " + name + "!!!");
    }
  }

  static void assertPreference(MinusculeMatcher matcher, @NonNls String less, @NonNls String more) {
    int iLess = matcher.matchingDegree(less);
    int iMore = matcher.matchingDegree(more);
    if (iLess >= iMore) {
      fail(iLess + ">=" + iMore + "; " + less + ">=" + more);
    }
  }
}
