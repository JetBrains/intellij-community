// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class TrigramBuilderTest {

  //=============================== legacy tests ==============================================================

  @Test
  public void testOnLegacyExample() {
    IntList actualTrigrams = new IntArrayList(trigramsUsingBuilder("String$CharData"));

    int expectedTrigramCount = 13;
    assertEquals(expectedTrigramCount, actualTrigrams.size());

    int[] expectedTrigrams = {
      etalonTrigramOf("$Ch"),
      etalonTrigramOf("arD"),
      etalonTrigramOf("ata"),
      6514785, 6578548, 6759523, 6840690, 6909543, 7235364, 7496801, 7498094, 7566450, 7631465
    };
    actualTrigrams.sort(null);
    Arrays.sort(expectedTrigrams);
    for (int i = 0; i < expectedTrigramCount; ++i) {
      assertEquals(
        expectedTrigrams[i],
        actualTrigrams.getInt(i),
        "Trigram #" + i
      );
    }
  }

  @Test
  public void trigramsSetIterator_followsGeneralIteratorContract() {
    IntIterator iterator = trigramsUsingBuilder("Str").intIterator();

    assertTrue(iterator.hasNext(), "hasNext() returns true since there is something in iterator");
    assertTrue(iterator.hasNext(), "hasNext() is pure & idempotent");
    assertTrue(iterator.hasNext(), "hasNext() is pure & idempotent");

    assertEquals(7566450, iterator.nextInt());

    assertFalse(iterator.hasNext(), "hasNext() returns true since the only element is already taken");
    assertFalse(iterator.hasNext(), "hasNext() is pure & idempotent");

    assertThrows(
      NoSuchElementException.class,
      () -> iterator.nextInt(),
      ".nextInt() must fail since .hasNext() returns false"
    );
  }

  //=============================== new tests ==============================================================

  //prefer a lot, but shorter ids (easier to debug):
  private static final int IDENTIFIERS_COUNT = 1024;
  private static final int MAX_IDENTIFIER_LENGTH = 32;

  @Test
  public void testOnExample_Single_NotASCII_Trigram() {
    assertEquals(
      etalonTrigramsOf("Стр"),
      trigramsUsingBuilder("Стр")
    );
  }

  @Test
  public void testOnExample_Two_NotASCII_Trigrams() {
    String string = "абвг";
    IntSet expected = etalonTrigramsOf(string);
    IntSet actual = trigramsUsingBuilder(string);
    assertEquals(
      expected,
      actual
    );
  }

  @Test
  public void trigramBuilder_CalculatesSameTrigrams_asEtalonAlgo() {
    for (String identifier : generateIdentifiers()) {
      assertEquals(
        etalonTrigramsOf(identifier),
        trigramsUsingBuilder(identifier),
        "[" + identifier + "] trigrams must be the same"
      );
    }
  }

  @Test
  public void trigramsCalculation_Is_CaseInsensitive() {
    for (String identifier : generateIdentifiers()) {
      String caseNormalizedIdentifier = normalizeCase(identifier);
      assertEquals(
        etalonTrigramsOf(identifier),
        etalonTrigramsOf(caseNormalizedIdentifier),
        "[" + identifier + "] vs [" + caseNormalizedIdentifier + "]: trigrams must be the same regardless of case"
      );
      assertEquals(
        etalonTrigramsOf(identifier),
        trigramsUsingBuilder(caseNormalizedIdentifier),
        "[" + identifier + "] vs [" + caseNormalizedIdentifier + "]: trigrams must be the same regardless of case"
      );
    }
  }

  @Test
  public void trigramsOfText_concatenatedFromIdentifiersWithDelimiters_AreSameAsTrigramsOfAllIdentifiers_RegardlessOfIdentifiersOrder() {
    //Generate not too many id, because there are a lot of delimiters:
    List<String> identifiers = generateIdentifiers(/*count: */128);
    for (String delimiter : DELIMITERS) {
      String text = concatShuffling(
        identifiers,
        delimiter,
        ThreadLocalRandom.current()
      );
      assertEquals(
        etalonTrigramsOf(identifiers),
        trigramsUsingBuilder(text),
        "[delimiter: '" + delimiter + "']: [" + text + "]" +
        " should be parsed to the same set of trigrams as individual identifiers in it, in any order"
      );
    }
  }

  @Test
  public void textConcatenatedFromIndividualTrigrams_ContainsAllTheIndividualTrigrams_AndMore() {
    List<String> identifiers = generateIdentifiers();
    String text = concatShuffling(
      identifiers,
      /*delimiter = */ "",
      ThreadLocalRandom.current()
    );
    IntSet trigramsFromConcatenatedText = trigramsUsingBuilder(text);
    IntSet trigramsFromIsolatedComponents = etalonTrigramsOf(identifiers);
    assertTrue(
      trigramsFromConcatenatedText.containsAll(trigramsFromIsolatedComponents),
      "trigrams[" + text + "](=" + trigramsFromConcatenatedText + ") must contain all the trigrams from isolated components " +
      "(=" + trigramsFromIsolatedComponents + ")"
    );
  }


  //============================== infrastructure: ============================================================
  private static @NotNull IntSet trigramsUsingBuilder(@NotNull String text) {
    return TrigramBuilder.getTrigrams(text);
  }

  /** concats identifiers, in a random order, separated by delimiter */
  private static String concatShuffling(@NotNull List<String> identifiers,
                                        @NotNull String delimiter,
                                        @NotNull ThreadLocalRandom rnd) {
    ArrayList<String> shuffledIdentifiers = new ArrayList<>(identifiers);
    Collections.shuffle(shuffledIdentifiers, rnd);
    return String.join(delimiter, shuffledIdentifiers);
  }

  private static List<String> generateIdentifiers() {
    return generateIdentifiers(IDENTIFIERS_COUNT);
  }

  private static List<String> generateIdentifiers(int identifiersCount) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return rnd.ints(identifiersCount, /*minLength: */1, MAX_IDENTIFIER_LENGTH)
      .mapToObj(length -> {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
          int index = rnd.nextInt(IDENTIFIER_CHARS.size());
          String idChar = IDENTIFIER_CHARS.get(index);
          sb.append(idChar);
        }
        return sb.toString();
      }).toList();
  }

  /** All ASCII chars that are not(JavaIdentifierPart) */
  private static final List<String> DELIMITERS = IntStream.range(Character.MIN_VALUE, Character.MAX_VALUE)
    .filter(ch -> !Character.isJavaIdentifierPart(ch))
    .mapToObj(ch -> String.valueOf((char)ch))
    .toList();

  private static String normalizeCase(String identifier) {
    //identifier.toLowerCase() doesn' work: case sensitivity/transformation is quite complicated in whole Unicode
    // E.g. there are exist chars such that ch.toUpper.toLower != ch.toLower -- i.e. case transformation is not
    // reversible. E.g.'ϰ' =toUpper=> 'Κ' =toLower=> 'κ'
    //Also, there are exist chars such that ch.toLower != String.valueOf(ch).toLower -- i.e. case transformation
    // for the char in isolation differs from case transformation for the char in a string.
    // E.g. 'İ'.toLower() => 'i', but "İ".toLower() = "i'̇" (2-char string, sometimes renders as a singe ligature
    // 'i with 2 dots')

    //This is why I normalize the case char-by-char -- exactly in a way we use in trigram generation.
    // Which is not perfect: current approach means that in some cases it'll be impossible to find a differently
    // cased string in a trigram index. But such cases are exotic (most of the time people look for ascii strings),
    // and anyway -- I see no better option right now.
    char[] chars = identifier.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      chars[i] = StringUtil.toLowerCase(chars[i]);
    }
    return new String(chars);
  }

  /** All ASCII chars that are JavaIdentifierPart */
  private static final List<String> IDENTIFIER_CHARS = IntStream.range(Character.MIN_VALUE, Character.MAX_VALUE)
    .filter(ch -> Character.isJavaIdentifierPart(ch))
    .mapToObj(ch -> String.valueOf((char)ch))
    .toList();


  private static IntSet etalonTrigramsOf(@NotNull Iterable<String> strings) {
    IntOpenHashSet trigrams = new IntOpenHashSet();
    for (String string : strings) {
      trigrams.addAll(etalonTrigramsOf(string));
    }
    return trigrams;
  }

  private static IntSet etalonTrigramsOf(@NotNull String s) {
    int length = s.length();
    if (length < 3) {
      return new IntArraySet(0);
    }
    IntArraySet trigrams = new IntArraySet(length - 2);
    for (int i = 0; i < length - 2; i++) {
      trigrams.add(etalonTrigramOf(s, i));
    }
    return trigrams;
  }

  private static int etalonTrigramOf(@NotNull String s) {
    if (s.length() != 3) {
      throw new IllegalArgumentException("[" + s + "] must be 3 chars long");
    }
    return etalonTrigramOf(s, 0);
  }

  private static int etalonTrigramOf(@NotNull String s,
                                     int startingWith) {

    return etalonTrigramOf(
      StringUtil.toLowerCase(s.charAt(startingWith)),
      StringUtil.toLowerCase(s.charAt(startingWith + 1)),
      StringUtil.toLowerCase(s.charAt(startingWith + 2))
    );
  }

  private static int etalonTrigramOf(char ch1, char ch2, char ch3) {
    return (((ch1 << 8) + ch2) << 8) + ch3;
  }
}
