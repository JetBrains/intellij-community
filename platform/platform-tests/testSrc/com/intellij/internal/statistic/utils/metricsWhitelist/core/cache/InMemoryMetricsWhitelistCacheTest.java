// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.core.cache;

import com.intellij.internal.statistic.utils.metricsWhitelist.core.DefaultMetricsWhitelistHeader;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelist;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelistHeader;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.loader.MetricsWhitelistLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class InMemoryMetricsWhitelistCacheTest {
  @Test
  public void testSimple() {
    TestWhitelistLoader loader = new TestWhitelistLoader();
    loader.putWhitelist("A", new TestWhitelist(false, "0.1"));
    loader.putWhitelist("B", new TestWhitelist(false, "0.2"));
    MetricsWhitelistCache<TestWhitelist> cache = new InMemoryMetricsWhitelistCache<>(loader, new FakeTicker());
    preloadWhitelists(cache, "A");

    TestWhitelist whitelistA = cache.get("A");
    assertTrue(whitelistA != null && "0.1".equals(whitelistA.getHeader().getVersion()));

    TestWhitelist notExisting = cache.get("B");
    assertNull(notExisting);
    TestWhitelist loaded = cache.get("B");
    assertTrue(loaded != null && "0.2".equals(loaded.getHeader().getVersion()));
  }

  @Test
  public void testExpire() {
    TestWhitelistLoader loader = new TestWhitelistLoader("A", new TestWhitelist(false, "0.1"));
    FakeTicker fakeTicker = new FakeTicker();
    MetricsWhitelistCache<TestWhitelist> cache = new InMemoryMetricsWhitelistCache<>(loader, fakeTicker);
    preloadWhitelists(cache, "A");

    fakeTicker.advance(InMemoryMetricsWhitelistCache.REMOVE_AFTER_ACCESS_PERIOD_IN_HOURS / 2, TimeUnit.HOURS);
    assertNotNull(cache.get("A"));
    fakeTicker.advance(InMemoryMetricsWhitelistCache.REMOVE_AFTER_ACCESS_PERIOD_IN_HOURS + 1, TimeUnit.HOURS);
    assertNull(cache.get("A"));
  }

  @Test
  public void testRefresh() {
    TestWhitelistLoader loader = new TestWhitelistLoader("A", new TestWhitelist(false, "0.1"));
    FakeTicker fakeTicker = new FakeTicker();
    MetricsWhitelistCache<TestWhitelist> cache = new InMemoryMetricsWhitelistCache<>(loader, fakeTicker);
    preloadWhitelists(cache, "A");

    TestWhitelist beforeRefresh1 = cache.get("A");
    assertTrue(beforeRefresh1 != null && "0.1".equals(beforeRefresh1.getHeader().getVersion()));
    loader.putWhitelist("A", new TestWhitelist(false, "0.2"));
    fakeTicker.advance(halfRefreshPeriod(), TimeUnit.HOURS);
    TestWhitelist beforeRefresh2 = cache.get("A");
    assertTrue(beforeRefresh2 != null && "0.1".equals(beforeRefresh2.getHeader().getVersion()));
    fakeTicker.advance(halfRefreshPeriod() + 1, TimeUnit.HOURS);
    TestWhitelist afterRefresh = cache.get("A");
    assertTrue(afterRefresh != null && "0.2".equals(afterRefresh.getHeader().getVersion()));
  }

  @Test
  public void testLoadFails() {
    TestWhitelistLoader loader = new TestWhitelistLoader("A", new TestWhitelist(false, "0.1"));
    loader.setFailOnLoadWhitelist(true);
    MetricsWhitelistCache<TestWhitelist> cache = new InMemoryMetricsWhitelistCache<>(loader, new FakeTicker());
    preloadWhitelists(cache, "A");
    assertNull(cache.get("A"));
  }

  @Test
  public void testRefreshFails() {
    TestWhitelistLoader loader = new TestWhitelistLoader("A", new TestWhitelist(false, "0.1"));
    FakeTicker fakeTicker = new FakeTicker();
    MetricsWhitelistCache<TestWhitelist> cache = new InMemoryMetricsWhitelistCache<>(loader, fakeTicker);
    preloadWhitelists(cache, "A");

    fakeTicker.advance(halfRefreshPeriod(), TimeUnit.HOURS);
    TestWhitelist beforeRefresh = cache.get("A");
    assertTrue(beforeRefresh != null && "0.1".equals(beforeRefresh.getHeader().getVersion()));

    loader.putWhitelist("A", new TestWhitelist(false, "0.2"));
    loader.setFailOnLoadWhitelist(true);
    fakeTicker.advance(halfRefreshPeriod() + 1, TimeUnit.HOURS);
    TestWhitelist afterFailedRefresh = cache.get("A");
    assertTrue(afterFailedRefresh != null && "0.1".equals(afterFailedRefresh.getHeader().getVersion()));

    loader.setFailOnLoadWhitelist(false);
    TestWhitelist afterSuccessfulRefresh = cache.get("A");
    assertTrue(afterSuccessfulRefresh != null && "0.2".equals(afterSuccessfulRefresh.getHeader().getVersion()));
  }

  @Test
  public void testDeprecated() {
    TestWhitelistLoader loader = new TestWhitelistLoader("A", new TestWhitelist(false, "0.1"));
    FakeTicker fakeTicker = new FakeTicker();
    MetricsWhitelistCache<TestWhitelist> cache = new InMemoryMetricsWhitelistCache<>(loader, fakeTicker);
    preloadWhitelists(cache, "A");

    fakeTicker.advance(halfRefreshPeriod(), TimeUnit.HOURS);
    TestWhitelist beforeRefresh = cache.get("A");
    assertTrue(beforeRefresh != null && "0.1".equals(beforeRefresh.getHeader().getVersion()));

    loader.putWhitelist("A", new TestWhitelist(true, "0.1"));
    fakeTicker.advance(halfRefreshPeriod() + 1, TimeUnit.HOURS);
    TestWhitelist afterRefresh = cache.get("A");
    assertNull(afterRefresh);
  }

  @Test
  public void testDontLoadIfVersionNotChanged() {
    TestWhitelistLoader loader = new TestWhitelistLoader();
    TestWhitelist originalWhitelist = new TestWhitelist(false, "0.1");
    loader.putWhitelist("A", originalWhitelist);
    FakeTicker fakeTicker = new FakeTicker();
    MetricsWhitelistCache<TestWhitelist> cache = new InMemoryMetricsWhitelistCache<>(loader, fakeTicker);
    preloadWhitelists(cache, "A");

    fakeTicker.advance(halfRefreshPeriod(), TimeUnit.HOURS);
    TestWhitelist beforeRefresh = cache.get("A");
    assertSame(originalWhitelist, beforeRefresh);

    loader.putWhitelist("A", new TestWhitelist(false, "0.1"));
    fakeTicker.advance(halfRefreshPeriod() + 1, TimeUnit.HOURS);
    TestWhitelist afterRefresh = cache.get("A");
    assertSame(originalWhitelist, afterRefresh);
  }

  private static void preloadWhitelists(@NotNull MetricsWhitelistCache<?> cache, @NotNull String... ids) {
    Arrays.stream(ids).forEach(cache::get);
  }

  private static long halfRefreshPeriod() {
    return InMemoryMetricsWhitelistCache.REFRESH_AFTER_WRITE_PERIOD_IN_HOURS / 2;
  }

  private static class TestWhitelistLoader implements MetricsWhitelistLoader<TestWhitelist> {
    private final Map<String, TestWhitelist> myWhitelists = ContainerUtil.newHashMap();
    private boolean myFailOnLoadWhitelist;

    private TestWhitelistLoader() {}

    private TestWhitelistLoader(@NotNull String id, @NotNull TestWhitelist whitelist) {
      putWhitelist(id, whitelist);
    }

    @NotNull
    @Override
    public MetricsWhitelistHeader loadHeader(@NotNull String id) {
      return myWhitelists.get(id).myHeader;
    }

    @NotNull
    @Override
    public TestWhitelist loadWhitelist(@NotNull String id) throws IOException {
      if (myFailOnLoadWhitelist) throw new IOException("Load failed");
      return myWhitelists.get(id);
    }

    public void putWhitelist(@NotNull String id, @NotNull TestWhitelist whitelist) {
      myWhitelists.put(id, whitelist);
    }

    private void setFailOnLoadWhitelist(boolean fail) {
      myFailOnLoadWhitelist = fail;
    }
  }

  private static class TestWhitelist implements MetricsWhitelist {
    private final MetricsWhitelistHeader myHeader;

    private TestWhitelist(boolean deprecated, @Nullable String version) {
      myHeader = new DefaultMetricsWhitelistHeader(deprecated, StringUtil.defaultIfEmpty(version, "unknown"));
    }

    @NotNull
    @Override
    public MetricsWhitelistHeader getHeader() {
      return myHeader;
    }
  }
}