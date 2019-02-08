// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.sets;

import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelistHeader;
import org.junit.Test;

import static org.junit.Assert.*;

public class FUSMetricsSetTest {
  @Test
  public void testParsed() {
    FUSMetricsSet.Factory factory = new FUSMetricsSet.Factory();
    FUSMetricsSet set = factory.createWhitelist(
      "{\n" +
      "    \"header\": {\n" +
      "        \"deprecated\": false,\n" +
      "        \"version\": \"0.1\"\n" +
      "    },\n" +
      "    \"entries\": []\n" +
      "}"
    );
    assertFalse(set.getHeader().isDeprecated());
    assertEquals("0.1", set.getHeader().getVersion());
  }

  @Test
  public void testNotParsedWhenBadJson() {
    FUSMetricsSet.Factory factory = new FUSMetricsSet.Factory();
    FUSMetricsSet set = factory.createWhitelist(
      "{\n" +
      "    \"header\": {\n" +
      "        \"deprecated\":: false,\n" +
      "        \"version\": \"0.1\"\n" +
      "    },\n" +
      "    \"entries\": []\n" +
      "}"
    );
    assertNull(set);
  }

  @Test
  public void testContains() {
    FUSMetricsSet.Factory factory = new FUSMetricsSet.Factory();
    FUSMetricsSet set = factory.createWhitelist(
      "{\n" +
      "    \"header\": {\n" +
      "        \"deprecated\": false,\n" +
      "        \"version\": \"0.1\"\n" +
      "    },\n" +
      "    \"entries\": [\"github.com/pkg/errors\", \"github.com/gobuffalo/buffalo\"]\n" +
      "}"
    );
    assertNotNull(set);
    assertTrue(set.contains("github.com/pkg/errors"));
    assertTrue(set.contains("github.com/gobuffalo/buffalo"));
    assertFalse(set.contains("github.com/not/popular/package"));
  }

  @Test
  public void testHeader() {
    FUSMetricsSet.Factory factory = new FUSMetricsSet.Factory();
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