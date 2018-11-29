// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.dictionaries;

import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class FUSDictionariesServiceImplTest {
  @Test
  public void testService() {
    FUSDictionariesService service = new FUSDictionariesServiceImpl(
      key -> key == "A" ? testDict("0.1", Collections.emptyMap())
                        : testDict("0.2", Collections.emptyMap()), new FakeTicker());

    service.asyncPreloadDictionaries(ContainerUtil.set("A"));

    FUSDictionary dictA = service.getDictionary("A");
    assertTrue(dictA != null && "0.1".equals(dictA.getVersion()));

    FUSDictionary notExisting = service.getDictionary("B");
    assertNull(notExisting);
    FUSDictionary loaded = service.getDictionary("B");
    assertTrue(loaded != null && "0.2".equals(loaded.getVersion()));
  }

  @Test
  public void testExpire() {
    FakeTicker fakeTicker = new FakeTicker();
    FUSDictionariesService service = new FUSDictionariesServiceImpl(
      key -> testDict("0.1", Collections.emptyMap()), fakeTicker);
    service.asyncPreloadDictionaries(ContainerUtil.set("A"));

    fakeTicker.advance(12, TimeUnit.HOURS);
    assertNotNull(service.getDictionary("A"));
    fakeTicker.advance(25, TimeUnit.HOURS);
    assertNull(service.getDictionary("A"));
  }

  @Test
  public void testRefresh() {
    FakeTicker fakeTicker = new FakeTicker();
    FUSDictionariesService service = new FUSDictionariesServiceImpl(
      key -> fakeTicker.read() == 0 ? testDict("0.1", Collections.emptyMap())
                                    : testDict("0.2", Collections.emptyMap()), fakeTicker);

    service.asyncPreloadDictionaries(ContainerUtil.set("A"));

    FUSDictionary beforeRefresh1 = service.getDictionary("A");
    assertTrue(beforeRefresh1 != null && "0.1".equals(beforeRefresh1.getVersion()));
    fakeTicker.advance(12, TimeUnit.HOURS);
    FUSDictionary beforeRefresh2 = service.getDictionary("A");
    assertTrue(beforeRefresh2 != null && "0.1".equals(beforeRefresh2.getVersion()));
    fakeTicker.advance(13, TimeUnit.HOURS);
    FUSDictionary afterRefresh = service.getDictionary("A");
    assertTrue(afterRefresh != null && "0.2".equals(afterRefresh.getVersion()));
  }

  @Test
  public void testLoadFails() {
    FUSDictionariesService service = new FUSDictionariesServiceImpl(key -> {
      throw new IOException("Network error");
    }, new FakeTicker());
    service.asyncPreloadDictionaries(Collections.singleton("A"));
    assertNull(service.getDictionary("A"));
  }

  @Test
  public void testRefreshFails() {
    FakeTicker fakeTicker = new FakeTicker();
    Ref<Integer> loadCallCount = Ref.create(0);
    FUSDictionariesService service = new FUSDictionariesServiceImpl(key -> {
      if (loadCallCount.get() == 0) {
        loadCallCount.set(1);
        return testDict("0.1", Collections.emptyMap());
      }
      if (loadCallCount.get() == 1) {
        loadCallCount.set(2);
        throw new IOException("Network error");
      }
      return testDict("0.2", Collections.emptyMap());
    }, fakeTicker);
    service.asyncPreloadDictionaries(Collections.singleton("A"));

    fakeTicker.advance(12, TimeUnit.HOURS);
    FUSDictionary beforeRefresh = service.getDictionary("A");
    assertTrue(beforeRefresh != null && "0.1".equals(beforeRefresh.getVersion()));

    fakeTicker.advance(13, TimeUnit.HOURS);
    FUSDictionary afterFailedRefresh = service.getDictionary("A");
    assertTrue(afterFailedRefresh != null && "0.1".equals(afterFailedRefresh.getVersion()));

    FUSDictionary afterSuccessfulRefresh = service.getDictionary("A");
    assertTrue(afterSuccessfulRefresh != null && "0.2".equals(afterSuccessfulRefresh.getVersion()));
  }

  @Test
  public void testDeprecated() {
    FakeTicker fakeTicker = new FakeTicker();
    FUSDictionariesService service = new FUSDictionariesServiceImpl(
      key -> fakeTicker.read() == 0 ? testDict("0.1", Collections.emptyMap()) : FUSCachedDictionary.DEPRECATED,
      fakeTicker
    );
    service.asyncPreloadDictionaries(ContainerUtil.set("A"));

    fakeTicker.advance(12, TimeUnit.HOURS);
    FUSDictionary beforeRefresh = service.getDictionary("A");
    assertTrue(beforeRefresh != null && "0.1".equals(beforeRefresh.getVersion()));

    fakeTicker.advance(13, TimeUnit.HOURS);
    FUSDictionary afterRefresh = service.getDictionary("A");
    assertNull(afterRefresh);
  }

  @NotNull
  private static FUSCachedDictionary testDict(@NotNull String version, @NotNull Map<String, List<String>> contents) {
    return new FUSCachedDictionary(new FUSDictionary(version, contents));
  }
}