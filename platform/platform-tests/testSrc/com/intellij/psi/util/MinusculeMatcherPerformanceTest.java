// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.util.NameUtilMatchingTest.*;

public class MinusculeMatcherPerformanceTest extends TestCase {
  public void testPerformance() {
    @NonNls final String longName = "ThisIsAQuiteLongNameWithParentheses().Dots.-Minuses-_UNDERSCORES_digits239:colons:/slashes\\AndOfCourseManyLetters";
    final List<MinusculeMatcher> matching = new ArrayList<>();
    final List<MinusculeMatcher> nonMatching = new ArrayList<>();

    for (String s : ContainerUtil.ar("*", "*i", "*a", "*u", "T", "ti", longName, longName.substring(0, 20))) {
      matching.add(NameUtil.buildMatcher(s, NameUtil.MatchingCaseSensitivity.NONE));
    }
    for (String s : ContainerUtil.ar("A", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "tag")) {
      nonMatching.add(NameUtil.buildMatcher(s, NameUtil.MatchingCaseSensitivity.NONE));
    }

    PlatformTestUtil.startPerformanceTest("Matching", 4_000, () -> {
      for (int i = 0; i < 100_000; i++) {
        for (MinusculeMatcher matcher : matching) {
          Assert.assertTrue(matcher.matches(longName));
          matcher.matchingDegree(longName);
        }
        for (MinusculeMatcher matcher : nonMatching) {
          Assert.assertFalse(matcher.matches(longName));
        }
      }
    }).assertTiming();
  }

  public void testOnlyUnderscoresPerformance() {
    PlatformTestUtil.startPerformanceTest(getName(), 120, () -> {
      String small = StringUtil.repeat("_", 50000);
      String big = StringUtil.repeat("_", small.length() + 1);
      assertMatches("*" + small, big);
      assertDoesntMatch("*" + big, small);
    }).assertTiming();
  }

  public void testRepeatedLetterPerformance() {
    PlatformTestUtil.startPerformanceTest(getName(), 30, () -> {
      String big = StringUtil.repeat("Aaaaaa", 50000);
      assertMatches("aaaaaaaaaaaaaaaaaaaaaaaa", big);
      assertDoesntMatch("aaaaaaaaaaaaaaaaaaaaaaaab", big);
    }).assertTiming();
  }

  public void testMatchingLongHtmlWithShortHtml() {
    PlatformTestUtil.startPerformanceTest(getName(), 30, () -> {
      String pattern = "*<p> aaa <div id=\"a";
      String html =
        "<html> <body> <H2> <FONT SIZE=\"-1\"> com.sshtools.cipher</FONT> <BR> Class AES128Cbc</H2> <PRE> java.lang.Object   <IMG SRC=\"../../../resources/inherit.gif\" ALT=\"extended by\">com.maverick.ssh.cipher.SshCipher       <IMG SRC=\"../../../resources/inherit.gif\" ALT=\"extended by\">com.maverick.ssh.crypto.engines.CbcBlockCipher           <IMG SRC=\"../../../resources/inherit.gif\" ALT=\"extended by\"><B>com.sshtools.cipher.AES128Cbc</B> </PRE> <HR> <DL> <DT>public class <B>AES128Cbc</B><DT>extends com.maverick.ssh.crypto.engines.CbcBlockCipher</DL>  <P> This cipher can optionally be added to the J2SSH Maverick API. To add  the ciphers from this package simply add them to the <A HREF=\"../../../com/maverick/ssh2/Ssh2Context.html\" title=\"class in com.maverick.ssh2\"><CODE>Ssh2Context</CODE></A>  <blockquote><pre>   import com.sshtools.cipher.*;   </pre></blockquote> <P>  <P> <DL> <DT><B>Version:</B></DT>   <DD>Revision: 1.20</DD> </DL> <HR> </body> </html>";
      assertDoesntMatch(pattern, html);
    }).assertTiming();
  }

  public void testMatchingLongStringWithAnotherLongStringWhereOnlyEndsDiffer() {
    String pattern = "*Then the large string is '{asdbsfafds adsfadasdfasdfasdfasdfasdfasdfsfasf adsfasdf sfasdfasdfasdfasdfasdfasdfd adsfadsfsafd adsfafdadsfsdfasdf sdf asdfasdfasfadsfasdfasfd asdfafd fasdfasdfasdfdsfas dadsfasfadsfafdsafddf  dsf dsasdfasdfsdafsdfsdfsdfasdffafdadfafafasdfasdf asdfasdfasdfasdfasdfasdfasdfasdfaasdfsdfasdfds adfafddfas aa afds}' is sent into the abyss\nThen";
    String name =     "Then the large string is '{asdbsfafds adsfadasdfasdfasdfasdfasdfasdfsfasf adsfasdf sfasdfasdfasdfasdfasdfasdfd adsfadsfsafd adsfafdadsfsdfasdf sdf asdfasdfasfadsfasdfasfd asdfafd fasdfasdfasdfdsfas dadsfasfadsfafdsafddf  dsf dsasdfasdfsdafsdfsdfsdfasdffafdadfafafasdfasdf asdfasdfasdfasdfasdfasdfasdfasdfaasdfsdfasdfds adfafddfas aa afds}' is sent into the abyss\nTh' is sent into the abyss";
    assertDoesntMatchFast(pattern, name);

    pattern = "findFirstAdjLoanPlanTemplateByAdjLoanPlan_AdjLoanProgram_AdjLoanProgramCodeAndTemplateVersions";
    name =    "findFirstAdjLoanPlanTemplateByAdjLoanPlan_AdjLoanProgram_AdjLoanProgramCodeAndTemplateVersion_TemplateVersionCode";
    assertDoesntMatchFast(pattern, name);

    pattern = "tip.how.to.select.a.thing.and.that.selected.things.are.shown.as.bold";
    name    = "tip.how.to.select.a.thing.and.that.selected.things.are.shown.as.bolid";
    assertDoesntMatchFast(pattern, name);
  }

  private void assertDoesntMatchFast(String pattern, String name) {
    PlatformTestUtil.startPerformanceTest(getName(), 30, () -> assertDoesntMatch(pattern, name)).assertTiming();
  }

  public void testMatchingLongRuby() {
    PlatformTestUtil.startPerformanceTest(getName(), 30, () -> {
      String pattern = "*# -*- coding: utf-8 -*-$:. unshift(\"/Library/RubyMotion/lib\")require 'motion/project'Motion::Project::App. setup do |app|  # Use `rake config' to see complete project settings.   app. sdk_version = '4. 3'end";
      String name    = "# -*- coding: utf-8 -*-$:.unshift(\"/Library/RubyMotion/lib\")require 'motion/project'Motion::Project::App.setup do |app|  # Use `rake config' to see complete project settings.  app.sdk_version = '4.3'  app.frameworks -= ['UIKit']end";
      assertDoesntMatch(pattern, name);
    }).assertTiming();
  }

  public void testLongStringMatchingWithItself() {
    String s =
      "the class with its attributes mapped to fields of records parsed by an {@link AbstractParser} or written by an {@link AbstractWriter}.";
    PlatformTestUtil.startPerformanceTest(getName(), 30, () -> {
      assertMatches(s, s);
      assertMatches("*" + s, s);

      assertPreference(s, s.substring(0, 10), s);
      assertPreference("*" + s, s.substring(0, 10), s);
    }).assertTiming();
  }

}
