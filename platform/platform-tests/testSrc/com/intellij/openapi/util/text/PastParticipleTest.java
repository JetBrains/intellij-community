// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import org.junit.Test;

import static com.intellij.openapi.util.text.PastParticiple.pastParticiple;
import static org.junit.Assert.*;

/**
 * @author Bas Leijdekkers
 */
public class PastParticipleTest {
  @Test
  public void testSimple() {
    assertEquals("a", pastParticiple("a"));
    assertEquals("axed", pastParticiple("ax"));
    assertEquals("readied", pastParticiple("ready"));
    assertEquals("REPLAYED", pastParticiple("REPLAY"));
    assertEquals("stayed", pastParticiple("stay"));
    assertEquals("TRIED", pastParticiple("TRY"));
    assertEquals("applied", pastParticiple("apply"));
    assertEquals("resolved", pastParticiple("resolve"));
    assertEquals("ADDED", pastParticiple("ADD"));
    assertEquals("cloned", pastParticiple("clone"));
    assertEquals("hashCoded", pastParticiple("hashCode"));
    assertEquals("agreed", pastParticiple("agree"));
    assertEquals("tied", pastParticiple("tie"));
    assertEquals("Died", pastParticiple("Die"));
    assertEquals("lied", pastParticiple("lie"));
    assertEquals("willed", pastParticiple("will"));
    assertEquals("notified", pastParticiple("notify"));
    assertEquals("pinged", pastParticiple("ping"));
    assertEquals("substringed", pastParticiple("substring"));
  }
  
  @Test
  public void testStrange() {
    assertEquals("panicked", pastParticiple("panic"));
    assertEquals("TRAFFICKED", pastParticiple("TRAFFIC"));
    assertEquals("frolicked", pastParticiple("frolic"));
  }
  
  @Test
  public void testDoubled() {
    assertEquals("stopped", pastParticiple("stop"));
    assertEquals("upped", pastParticiple("up"));
    assertEquals("cancelled", pastParticiple("cancel")); // British English
    assertEquals("travelled", pastParticiple("travel")); // British English
    assertEquals("CHATTED", pastParticiple("CHAT"));
    assertEquals("logged", pastParticiple("log"));
    assertEquals("scrubbed", pastParticiple("scrub"));
    assertEquals("throbbed", pastParticiple("throb"));
    assertEquals("quizzed", pastParticiple("quiz"));
    assertEquals("equipped", pastParticiple("equip"));
    assertEquals("omitted", pastParticiple("omit"));
    assertEquals("preferred", pastParticiple("prefer"));
    assertEquals("incurred", pastParticiple("incur"));
    assertEquals("deterred", pastParticiple("deter"));
    assertEquals("benefitted", pastParticiple("benefit")); // British
    assertEquals("yakked", pastParticiple("yak"));
    assertEquals("focussed", pastParticiple("focus")); // British
    assertEquals("duelled", pastParticiple("duel")); // British
    assertEquals("fuelled", pastParticiple("fuel")); // British
    assertEquals("referred", pastParticiple("refer")); // British
    assertEquals("mapped", pastParticiple("map"));
    assertEquals("bedded", pastParticiple("bed"));
  }
  
  @Test
  public void testNoDoubling() {
    assertEquals("played", pastParticiple("play"));
    assertEquals("fixed", pastParticiple("fix"));
    assertEquals("swallowed", pastParticiple("swallow"));
    assertEquals("opened", pastParticiple("open"));
    assertEquals("battered", pastParticiple("batter"));
    assertEquals("entered", pastParticiple("enter"));
    assertEquals("deposited", pastParticiple("deposit"));
    assertEquals("remembered", pastParticiple("remember"));
    assertEquals("discovered", pastParticiple("discover"));
    assertEquals("visited", pastParticiple("visit"));
    assertEquals("listened", pastParticiple("listen"));
    assertEquals("recovered", pastParticiple("recover"));
    assertEquals("developed", pastParticiple("develop"));
    //assertEquals("focused", generatePastParticiple("focus")); // American
    assertEquals("RECKONED", pastParticiple("RECKON"));
    assertEquals("rendered", pastParticiple("render"));
    assertEquals("profited", pastParticiple("profit"));
    assertEquals("exited", pastParticiple("exit"));
    assertEquals("debuted", pastParticiple("debut"));
    assertEquals("targeted", pastParticiple("target"));
    //assertEquals("dueled", generatePastParticiple("duel")); // American
    //assertEquals("fueled", generatePastParticiple("fuel")); // American
    //assertEquals("canceled", generatePastParticiple("cancel")); // American English
    //assertEquals("traveled", generatePastParticiple("travel")); // American English
  }
  
  @Test
  public void testSomeIrregulars() {
    assertEquals("slept", pastParticiple("sleep"));
    assertEquals("run", pastParticiple("run"));
    assertEquals("GOTTEN", pastParticiple("GET"));
    assertEquals("brought", pastParticiple("bring"));
    assertEquals("Quit", pastParticiple("Quit"));
    assertEquals("gone", pastParticiple("go"));
    assertEquals("swung", pastParticiple("swing"));
    assertEquals("strung", pastParticiple("string"));
    assertEquals("rung", pastParticiple("ring"));
    assertEquals("been", pastParticiple("be"));
    assertEquals("foregone", pastParticiple("forego"));
    assertEquals("interwoven", pastParticiple("interweave"));
  }

  @Test
  public void testIgnore() {
    assertEquals("matches", pastParticiple("matches"));
    assertEquals("equals", pastParticiple("equals"));
    assertEquals("of", pastParticiple("of"));
    assertEquals("проблем", pastParticiple("проблем"));
    assertEquals("string1", pastParticiple("string1"));
  }
}