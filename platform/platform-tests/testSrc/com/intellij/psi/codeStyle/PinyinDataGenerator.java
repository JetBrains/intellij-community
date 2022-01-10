// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Generates data arrays for {@link PinyinMatcher} using Unihan_Readings.txt file from unicode.org
 * Requires Internet connection.
 */
class PinyinDataGenerator {
  private static final int LINE_LENGTH = 100;
  private static final String DATA_SOURCE = "https://unicode.org/Public/UNIDATA/Unihan.zip";
  private static final String READINGS_FILE = "Unihan_Readings.txt";

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

  public static void main(String[] args) throws IOException {
    generate();
  }
}
