// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Generates data arrays for {@link PinyinMatcher} using Unihan_Readings.txt file from unicode.org
 * Requires Internet connection.
 */
@Ignore
public class PinyinMatcherDataTest {
  private static final int LINE_LENGTH = 100;
  private static final String DATA_SOURCE = "https://unicode.org/Public/UNIDATA/Unihan.zip";
  private static final String READINGS_FILE = "Unihan_Readings.txt";

  @Test
  public void ensurePinyinDataIsUpToDate() throws IOException {
    List<Mapping> mappings = readMappings();
    List<String> initials = generateInitials(mappings);
    String encodingStr = String.join(",", initials);
    String data = getDataString(mappings, initials);

    Supplier<String> message = () ->
      "Pinyin data mismatch. Please update constants in " + PinyinMatcher.class.getName() + " to the following:\n" +
      toJavaStringLiteral("ENCODING", encodingStr) + "\n" +
      toJavaStringLiteral("DATA", data) + "\n";

    Assertions.assertEquals(encodingStr, PinyinMatcher.ENCODING, message);
    Assertions.assertEquals(data, PinyinMatcher.DATA, message);
  }

  private static String toJavaStringLiteral(String varName, String input) {
    StringBuilder result = new StringBuilder(varName + " =\n\"");
    int curLineLength = 0;
    for (int i = 0; i < input.length(); i++) {
      char ch = input.charAt(i);
      String charRepresentation;
      if (ch == '"' || ch == '\\') {
        charRepresentation = "\\" + ch;
      }
      else if (ch < 127) {
        charRepresentation = Character.toString(ch);
      }
      else {
        charRepresentation = String.format("\\u%04X", (int)ch);
      }
      result.append(charRepresentation);
      curLineLength += charRepresentation.length();
      if (curLineLength > LINE_LENGTH && i < input.length() - 1) {
        curLineLength = 0;
        result.append("\" +\n\"");
      }
    }
    return result.append("\";").toString();
  }

  @NotNull
  private static String getDataString(List<Mapping> mappings, List<String> initials) {
    Map<String, Character> encoding = initials.stream()
      .collect(Collectors.toMap(s -> s, s -> (char)(PinyinMatcher.BASE_CHAR + initials.indexOf(s))));

    Map<Integer, Character> map = mappings.stream()
      .collect(Collectors.toMap(mapping -> mapping.codePoint, m -> encoding.get(m.charString())));
    int lastCodePoint = mappings.stream().mapToInt(m -> m.codePoint).max().orElseThrow(NoSuchElementException::new);
    return IntStream.rangeClosed(PinyinMatcher.BASE_CODE_POINT, lastCodePoint)
      .mapToObj(i -> map.getOrDefault(i, ' ').toString()).collect(Collectors.joining());
  }

  @NotNull
  private static List<String> generateInitials(List<Mapping> mappings) {
    return mappings.stream().map(Mapping::charString).distinct()
      .sorted(Comparator.comparing(String::length).thenComparing(Comparator.naturalOrder()))
      .collect(Collectors.toList());
  }

  @NotNull
  private static List<Mapping> readMappings() throws IOException {
    try (ZipInputStream zis = new ZipInputStream(new URL(DATA_SOURCE).openStream())) {
      while (true) {
        ZipEntry entry = zis.getNextEntry();
        if (entry == null) {
          throw new IllegalStateException(String.format("No %s found inside %s", READINGS_FILE, DATA_SOURCE));
        }
        if (entry.getName().equals(READINGS_FILE)) {
          Collection<Mapping> mappings = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8)).lines()
            .map(Mapping::parseUniHan)
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(m -> m.codePoint, m -> m, Mapping::merge, LinkedHashMap::new))
            .values();
          return new ArrayList<>(mappings);
        }
      }
    }
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

    static Mapping merge(Mapping m1, Mapping m2) {
      if (m1.codePoint != m2.codePoint) throw new IllegalArgumentException();
      return new Mapping(m1.codePoint, m1.chars | m2.chars);
    }

    static Mapping parseUniHan(String line) {
      if (line.startsWith("#")) return null;
      String[] parts = line.split("\\s+");
      if (parts.length != 3) return null;
      if (!parts[0].startsWith("U+")) return null;
      int codePoint = Integer.parseInt(parts[0].substring(2), 16);
      if (codePoint < PinyinMatcher.BASE_CODE_POINT) return null;
      // Codepoints outside BMP are not supported for now
      if (codePoint > 0xA000) return null;
      String[] readings;
      switch (parts[1]) {
        case "kMandarin":
          readings = new String[]{parts[2]};
          break;
        case "kHanyuPinyin":
          int colonPos = parts[2].indexOf(':');
          if (colonPos == -1) return null;
          readings = parts[2].substring(colonPos + 1).split(",");
          break;
        default:
          return null;
      }
      long encoded = 0;
      for (String reading : readings) {
        char initial = Normalizer.normalize(reading, Normalizer.Form.NFKD).charAt(0);
        encoded |= 1L << (initial - 'a');
      }
      return new Mapping(codePoint, encoded);
    }

    @Override
    public String toString() {
      return String.format("%04X: %s", codePoint, charString());
    }
  }
}
