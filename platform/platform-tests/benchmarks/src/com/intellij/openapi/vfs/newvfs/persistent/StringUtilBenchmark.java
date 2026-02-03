// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.text.StringUtil;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**  */
@BenchmarkMode({Mode.AverageTime/*, Mode.SampleTime*/})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(1)
public class StringUtilBenchmark {

  @State(Scope.Benchmark)
  public static class DataContext {

    char nextPrintableASCII(ThreadLocalRandom rnd) {
      return (char)rnd.nextInt(32, 0x7F);
    }

    char nextASCII(ThreadLocalRandom rnd) {
      return (char)rnd.nextInt(0, 0x80);
    }

    char nextUTF(ThreadLocalRandom rnd) {
      return (char)rnd.nextInt(0, Character.MAX_VALUE + 1);
    }
  }

  @State(Scope.Benchmark)
  public static class ImplContext {

    @Param({
      "fake",

      "Character.toLowerCase",
      "StringUtils.toLowerCase",
      "toLowerCaseASCII_arithmetic",
      "toLowerCaseASCII_bitmask",

      "Character.toUpperCase",
      "StringUtils.toUpperCase",
      "toUpperCaseASCII_arithmetic",
      "toUpperCaseASCII_bitmask",
    })
    public String converterKind;

    private CaseConverter toLowerCase;

    @Setup
    public void setup() {
      toLowerCase = switch (converterKind) {
        case "fake" -> ch -> ch; //as a baseline

        case "Character.toLowerCase" -> Character::toLowerCase;
        case "StringUtils.toLowerCase" -> StringUtil::toLowerCase;
        case "toLowerCaseASCII_arithmetic" -> ch -> {
          if (ch <= 0x7F) {
            if (ch >= 'A' && ch <= 'Z') {
              return (char)(ch + ('a' - 'A'));
            }
            return ch;
          }
          return Character.toLowerCase(ch);
        };
        case "toLowerCaseASCII_bitmask" -> ch -> {
          if (ch <= 0x7F) {
            if (ch >= 'A' && ch <= 'Z') {
              return (char)(ch | 0b0010_0000);
            }
            return ch;
          }
          return Character.toLowerCase(ch);
        };

        case "Character.toUpperCase" -> Character::toUpperCase;
        case "StringUtils.toUpperCase" -> StringUtil::toUpperCase;
        case "toUpperCaseASCII_arithmetic" -> ch -> {
          if (ch <= 0x7F) {
            if (ch >= 'A' && ch <= 'Z') {
              return (char)(ch + ('A' - 'a'));
            }
            return ch;
          }
          return Character.toUpperCase(ch);
        };
        case "toUpperCaseASCII_bitmask" -> ch -> {
          if (ch <= 0x7F) {
            if (ch >= 'a' && ch <= 'z') {
              return (char)(ch & 0b1101_1111);
            }
            return ch;
          }
          return Character.toUpperCase(ch);
        };
        default -> throw new IllegalStateException("Unexpected value: " + converterKind);
      };
    }

    private char getLowerCase(char ch) {
      return toLowerCase.convert(ch);
    }
  }

  public interface CaseConverter {
    char convert(char ch);
  }

  @Benchmark
  public int toLowerCase_PrintableASCII(DataContext dataContext,
                                        ImplContext impl) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();

    char symbol = dataContext.nextPrintableASCII(rnd);
    return impl.getLowerCase(symbol);
  }

  @Benchmark
  public int toLowerCase_ASCII(DataContext dataContext,
                               ImplContext impl) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();

    char ascii = dataContext.nextASCII(rnd);
    return impl.getLowerCase(ascii);
  }

  @Benchmark
  public int toLowerCase_UTF(DataContext dataContext,
                             ImplContext impl) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();

    char chr = dataContext.nextUTF(rnd);
    return impl.getLowerCase(chr);
  }

  public static void main(String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .forks(1)
      .include(StringUtilBenchmark.class.getSimpleName() + ".*")
      //.threads(4)
      .build();

    new Runner(opt).run();
  }
}
