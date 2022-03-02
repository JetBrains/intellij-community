// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.uploader.util;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtraHTTPHeadersParserTest {

  private static final String malformedStr  = "foo=;";
  private static final String singleKV      = "foo=bar";
  private static final String multiKV       = "foo=bar;A=B";


  @Test
  void parse() {
    Map<String, String> malformed = ExtraHTTPHeadersParser.parse(malformedStr);
    assertTrue(malformed.isEmpty(), "must not parse malformed string");

    Map<String, String> single = ExtraHTTPHeadersParser.parse(singleKV);
    assertEquals(1, single.size());
    assertEquals("bar", single.get("foo"));

    Map<String, String> multi = ExtraHTTPHeadersParser.parse(multiKV);
    assertEquals(2, multi.size());
    assertEquals("bar", multi.get("foo"));
    assertEquals("B", multi.get("A"));
  }

  @Test
  void serialize() {
    assertEquals(singleKV, ExtraHTTPHeadersParser.serialize(Collections.singletonMap("foo", "bar")));
    Map<String, String> multiKVMap = new LinkedHashMap<>();
    multiKVMap.put("foo", "bar");
    multiKVMap.put("A", "B");
    assertEquals(multiKV,  ExtraHTTPHeadersParser.serialize(multiKVMap));
  }

  @Test
  void serializeEmpty() {
    assertEquals("", ExtraHTTPHeadersParser.serialize(Collections.emptyMap()));
  }
}