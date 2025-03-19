// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.util.text.CharArrayCharSequence;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmark performance of {@link com.intellij.openapi.util.text.TrigramBuilder}
 */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(1)
public class TrigramBuilderBenchmark {
  private static final String[] IDENTIFIERS_ASCII = {
    "a", "abc", "abcd", "ab_cde",
    "A", "ABC", "ABCD", "AB_CDE",
    "public", "static", "final",
    "TrigramBuilderTest", "TestCase", "class", "extends", "void", "IDENTI$FIERS", "DELIMITERS",
    "concatShuffling", "String", "List", "infraSTRUCTURE", "getTrigrams"
  };

  private static final String[] IDENTIFIERS_NON_ASCII = {
    "a", "abc", "abcd", "ab_cde",
    "A", "ABC", "ABCD", "AB_CDE",
    "public", "static", "final",
    "TrigramBuilderTest", "TestCase", "class", "extends", "void", "IDENTI$FIERS", "DELIMITERS",
    "concatShuffling", "String", "List", "infraSTRUCTURE", "getTrigrams",

    //add a bit of non-ascii identifiers to stress non-ascii branch also
    "клаСС", "Интерфейс"
  };

  private static final String[] DELIMITERS_ASCII = {
    " ", ",", ".", "-", "\n", "\t"
  };

  private static final String[] DELIMITERS_NON_ASCII = {
    " ", ",", ".", "-", "\n", "\t", "\u00A0", "\u00B8"
  };

  @State(Scope.Benchmark)
  public static class DataContext {

    @Param({/*"1000", */"10000"})
    protected int totalTexts = 10000;

    @Param({"true", "false"})
    protected boolean generateNonAsciiIdentifiers = false;
    @Param({"true", "false"})
    protected boolean generateNonAsciiDelimiters = false;

    protected String[] generatedTexts;
    protected CharArrayCharSequence[] generatedTextsAsCharSequence;

    @Setup
    public void setup() throws Exception {
      generatedTexts = generateTexts(totalTexts, generateNonAsciiIdentifiers, generateNonAsciiDelimiters);
      //Use CharArrayCharSequence which exposes underlying char[]
      generatedTextsAsCharSequence = new CharArrayCharSequence[generatedTexts.length];
      for (int i = 0; i < generatedTextsAsCharSequence.length; i++) {
        generatedTextsAsCharSequence[i] = new CharArrayCharSequence(generatedTexts[i].toCharArray());
      }
    }

    @TearDown
    public void tearDown() throws Exception {
    }

    private static String[] generateTexts(int linesCount,
                                          boolean generateNonAsciiIdentifiers,
                                          boolean generateNonAsciiDelimiters) {
      String[] texts = new String[linesCount];
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      for (int i = 0; i < linesCount; i++) {
        int identifiersCount = rnd.nextInt(4, 1024);
        if (generateNonAsciiIdentifiers) {
          if (generateNonAsciiDelimiters) {
            texts[i] = randomLine(rnd, identifiersCount, IDENTIFIERS_NON_ASCII, DELIMITERS_NON_ASCII);
          }
          else {
            texts[i] = randomLine(rnd, identifiersCount, IDENTIFIERS_NON_ASCII, DELIMITERS_ASCII);
          }
        }
        else {
          if (generateNonAsciiDelimiters) {
            texts[i] = randomLine(rnd, identifiersCount, IDENTIFIERS_ASCII, DELIMITERS_NON_ASCII);
          }
          else {
            texts[i] = randomLine(rnd, identifiersCount, IDENTIFIERS_ASCII, DELIMITERS_ASCII);
          }
        }
      }
      return texts;
    }

    private static String randomLine(Random rnd,
                                     int identifiersCount,
                                     String[] identifiers,
                                     String[] delimiters) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < identifiersCount; i++) {
        sb.append(identifiers[rnd.nextInt(identifiers.length)]);
        if (i < identifiersCount - 1) {
          sb.append(delimiters[rnd.nextInt(delimiters.length)]);
        }
      }
      return sb.toString();
    }
  }

  @Benchmark
  public Object _baseline(DataContext context) {
    //estimate cost of ~overhead -- ops, unrelated to actual trigram calculation:
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    TrigramBuilder.AddonlyIntSet set = new TrigramBuilder.AddonlyIntSet();
    CharArrayCharSequence[] texts = context.generatedTextsAsCharSequence;
    set.add(texts[rnd.nextInt(texts.length)].length());
    return set;
  }


  @Benchmark
  public @NotNull IntSet trigramsFromString(DataContext context) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    String[] texts = context.generatedTexts;

    int index = rnd.nextInt(texts.length);
    String text = texts[index];

    return TrigramBuilder.getTrigrams(text);
  }

  @Benchmark
  public @NotNull IntSet trigramsFromCharSequence(DataContext context) throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    CharArrayCharSequence[] textsAsCharSequence = context.generatedTextsAsCharSequence;

    //CharArrayCharSequence exposes underlying char[], so trigram's calculation will use specialized branch

    int index = rnd.nextInt(textsAsCharSequence.length);
    CharArrayCharSequence text = textsAsCharSequence[index];

    return TrigramBuilder.getTrigrams(text);
  }

  public static void main(String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      //.forks(0)
      .include(TrigramBuilderBenchmark.class.getSimpleName() + ".*")
      //.threads(4)
      .build();

    new Runner(opt).run();
  }
}
