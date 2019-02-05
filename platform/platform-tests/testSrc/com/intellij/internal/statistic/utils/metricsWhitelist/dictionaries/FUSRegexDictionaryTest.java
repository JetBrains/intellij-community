// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.dictionaries;

import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelistHeader;
import org.junit.Test;

import static org.junit.Assert.*;

public class FUSRegexDictionaryTest {
  @Test
  public void testParsed() {
    FUSRegexDictionary.Factory factory = new FUSRegexDictionary.Factory();
    FUSRegexDictionary dictionary = factory.createWhitelist(
      "{\n" +
      "    \"header\": {\n" +
      "        \"deprecated\": false,\n" +
      "        \"version\": \"0.1\"\n" +
      "    },\n" +
      "    \"entries\": []\n" +
      "}"
    );
    assertFalse(dictionary.getHeader().isDeprecated());
    assertEquals("0.1", dictionary.getHeader().getVersion());
  }

  @Test
  public void testNotParsedWhenBadJson() {
    FUSRegexDictionary.Factory factory = new FUSRegexDictionary.Factory();
    FUSRegexDictionary dictionary = factory.createWhitelist(
      "{\n" +
      "    \"header\": {\n" +
      "        \"deprecated\":: false,\n" +
      "        \"version\": \"0.1\"\n" +
      "    },\n" +
      "    \"entries\": []\n" +
      "}"
    );
    assertNull(dictionary);
  }

  @Test
  public void testNotParsedWhenBadRegex() {
    FUSRegexDictionary.Factory factory = new FUSRegexDictionary.Factory();
    FUSRegexDictionary dictionary = factory.createWhitelist(
      "{\n" +
      "    \"header\": {\n" +
      "        \"deprecated\": false,\n" +
      "        \"version\": \"0.1\"\n" +
      "    },\n" +
      "    \"entries\": [\n" +
      "        {\n" +
      "            \"pattern\": \"*** is not a type\",\n" +
      "            \"metric\": \"is.not.a.type\"\n" +
      "        }\n" +
      "    ]\n" +
      "}"
    );
    assertNull(dictionary);
  }

  @Test
  public void testLookup() {
    FUSRegexDictionary.Factory factory = new FUSRegexDictionary.Factory();
    FUSRegexDictionary dictionary = factory.createWhitelist(
      "{\n" +
      "    \"header\": {\n" +
      "        \"deprecated\": false,\n" +
      "        \"version\": \"0.1\"\n" +
      "    },\n" +
      "    \"entries\": [\n" +
      "        {\n" +
      "            \"pattern\": \".* is not a type\",\n" +
      "            \"metric\": \"is.not.a.type\"\n" +
      "        },\n" +
      "        {\n" +
      "            \"pattern\": \".* discards result of .*\",\n" +
      "            \"metric\": \"discards.result\"\n" +
      "        }\n" +
      "    ]\n" +
      "}"
    );
    assertNotNull(dictionary);
    assertEquals("is.not.a.type", dictionary.lookupMetric("myVariable is not a type"));
    assertNull(dictionary.lookupMetric("some string"));
  }

  @Test
  public void testHeader() {
    FUSRegexDictionary.Factory factory = new FUSRegexDictionary.Factory();
    MetricsWhitelistHeader header = factory.createHeader(
      "{\n" +
      "    \"deprecated\": false,\n" +
      "    \"version\": \"0.1\"\n" +
      "}"
    );
    assertNotNull(header);
    assertFalse(header.isDeprecated());
    assertEquals("0.1", header.getVersion());
  }
}