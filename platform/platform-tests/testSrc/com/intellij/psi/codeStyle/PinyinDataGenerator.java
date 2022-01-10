// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Generates data arrays for {@link PinyinMatcher} using uc-to-py.tbl as the input.
 * "uc-to-py.tbl" is "Unicode do Pinyin table" created by stolfi
 *
 * It contains unicode code point names and the respective pinyin readings, like "4F60 (ni3)"
 * If you have data in other formats, you should update the {@link #readMappings()} and {@link PinyinDataGenerator.Mapping#parse(String)}
 * methods correspondingly. Other parts of code are input-independent.
 */
class PinyinDataGenerator {
  private static final int LINE_LENGTH = 100;

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void generate() throws IOException {
    List<Mapping> mappings = readMappings();
    List<String> initials = generateInitials(mappings);
    System.out.println(dumpInitialsEncoding(initials));
    System.out.println(dumpData(mappings, initials));
  }

  @NotNull
  private static String dumpData(List<Mapping> mappings, List<String> initials) {
    Map<String, Character> encoding = initials.stream()
      .collect(Collectors.toMap(s -> s, s -> (char) (PinyinMatcher.BASE_CHAR + initials.indexOf(s))));

    Map<Integer, Character> map = mappings.stream()
      .collect(Collectors.toMap(mapping -> mapping.codePoint, m -> encoding.get(m.charString())));
    StringBuilder result = new StringBuilder("DATA =\n\"");
    int lastCodePoint = mappings.stream().mapToInt(m -> m.codePoint).max().orElseThrow(NoSuchElementException::new);
    int curLineLength = 0;
    for (int i = PinyinMatcher.BASE_CODE_POINT; i <= lastCodePoint; i++) {
      char ch = map.getOrDefault(i, ' ');
      String charRepresentation;
      if (ch == '"' || ch == '\\') {
        charRepresentation = "\\" + ch;
      } else if (ch < 127) {
        charRepresentation = Character.toString(ch);
      } else {
        charRepresentation = String.format("\\u%04X", (int) ch);
      }
      result.append(charRepresentation);
      curLineLength += charRepresentation.length();
      if (curLineLength > LINE_LENGTH) {
        curLineLength = 0;
        result.append("\" +\n\"");
      }
    }
    result.append("\";");
    return result.toString();
  }

  private static String dumpInitialsEncoding(List<String> initials) {
    String encodingStr = String.join(",", initials);
    String formattedEncodingStr = IntStream.rangeClosed(0, encodingStr.length() / LINE_LENGTH)
      .mapToObj(pos -> encodingStr.substring(pos * LINE_LENGTH, Math.min(pos * LINE_LENGTH + LINE_LENGTH, encodingStr.length())))
      .collect(Collectors.joining("\" +\n\"", "\"", "\""));
    return "ENCODING =\n(" + formattedEncodingStr + ").split(\",\");";
  }

  @NotNull
  private static List<String> generateInitials(List<Mapping> mappings) {
    return mappings.stream().map(Mapping::charString).distinct()
      .sorted(Comparator.comparing(String::length).thenComparing(Comparator.naturalOrder()))
      .collect(Collectors.toList());
  }

  @NotNull
  private static List<Mapping> readMappings() throws IOException {
    List<Mapping> mappings;
    try (InputStream stream = PinyinDataGenerator.class.getResourceAsStream("uc-to-py.tbl")) {
      if (stream == null) {
        throw new IllegalStateException("Please put uc-to-py.tbl next to this class");
      }
      mappings = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines()
        .map(Mapping::parse)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    }
    return mappings;
  }

  private static final class Mapping {
    private final int codePoint;
    private final long chars;

    Mapping(int codePoint, long chars) {
      this.codePoint = codePoint;
      this.chars = chars;
    }

    String charString() {
      return BitSet.valueOf(new long[]{chars}).stream().mapToObj(bit -> Character.toString((char)(bit + 'a')))
        .collect(Collectors.joining());
    }

    static Mapping parse(String line) {
      if (line.startsWith("#")) {
        return null;
      }
      line = line.trim();
      Pattern pattern = Pattern.compile("([0-9A-F]{4}) \\((.+\\))");
      Matcher matcher = pattern.matcher(line);
      if (!matcher.matches()) {
        return null;
      }
      int codePoint = Integer.parseInt(matcher.group(1), 16);
      if (codePoint < PinyinMatcher.BASE_CODE_POINT) {
        return null;
      }
      String[] readings = matcher.group(2).split(",");
      long chars = 0;
      for (String reading : readings) {
        reading = reading.replaceFirst("^\\d+:", "");
        if (reading.isEmpty() || reading.equals("none0")) {
          return null;
        }
        char firstChar = reading.charAt(0);
        if (firstChar < 'a' || firstChar > 'z') {
          return null;
        }
        chars |= (1L << (firstChar - 'a'));
      }
      return new Mapping(codePoint, chars);
    }
  }

  public static void main(String[] args) throws IOException {
    generate();
  }
}
