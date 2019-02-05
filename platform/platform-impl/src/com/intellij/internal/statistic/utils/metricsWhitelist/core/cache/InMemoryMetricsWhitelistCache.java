// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.core.cache;

import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelist;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelistHeader;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.loader.MetricsWhitelistLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class InMemoryMetricsWhitelistCache<T extends MetricsWhitelist> implements MetricsWhitelistCache<T> {
  public static final long REFRESH_AFTER_WRITE_PERIOD_IN_HOURS = 24;
  public static final long REMOVE_AFTER_ACCESS_PERIOD_IN_HOURS = 24;

  private static final Logger LOG = Logger.getInstance(InMemoryMetricsWhitelistCache.class);

  private final ExecutorService myExecutorService;
  private final MetricsWhitelistLoader<T> myLoader;
  // When loaded value is deprecated, we put Optional.empty() in the cache.
  // This helps to avoid potentially expensive calls to MetricsWhitelistLoader#load.
  private final LoadingCache<String, Optional<T>> myCache;

  public InMemoryMetricsWhitelistCache(@NotNull MetricsWhitelistLoader<T> loader) {
    myExecutorService = ConcurrencyUtil.newSingleThreadExecutor("Metrics whitelist in memory cache executor");
    myLoader = loader;
    myCache = create(myExecutorService, myLoader, null);
  }

  @TestOnly
  InMemoryMetricsWhitelistCache(@NotNull MetricsWhitelistLoader<T> loader, @NotNull Ticker ticker) {
    myExecutorService = MoreExecutors.newDirectExecutorService();
    myLoader = loader;
    myCache = create(myExecutorService, myLoader, ticker);
  }

  @Nullable
  @Override
  public T get(@NotNull String whitelistId) {
    Optional<T> cached = myCache.getIfPresent(whitelistId);
    //noinspection OptionalAssignedToNull
    if (cached != null) {
      return cached.orElse(null);
    }
    myExecutorService.execute(() -> {
      try {
        myCache.get(whitelistId);
      }
      catch (ExecutionException e) {
        LOG.debug(e);
      }
    });
    return null;
  }

  @NotNull
  private static <T extends MetricsWhitelist> LoadingCache<String, Optional<T>> create(@NotNull ExecutorService executorService,
                                                                                       @NotNull MetricsWhitelistLoader<T> loader,
                                                                                       @Nullable Ticker customTicker) {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder()
      .maximumSize(100)
      .refreshAfterWrite(REFRESH_AFTER_WRITE_PERIOD_IN_HOURS, TimeUnit.HOURS) // refresh after a day since the last write
      .expireAfterAccess(REMOVE_AFTER_ACCESS_PERIOD_IN_HOURS, TimeUnit.HOURS); // if not read/written withing a day, remove from cache
    if (customTicker != null) {
      builder.ticker(customTicker);
    }
    return builder.build(new CacheLoader<String, Optional<T>>() {
      @Override
      public Optional<T> load(@NotNull String key) throws Exception {
        MetricsWhitelistHeader header = loader.loadHeader(key);
        return header.isDeprecated() ? Optional.empty() : Optional.of(loader.loadWhitelist(key));
      }

      @Override
      public ListenableFuture<Optional<T>> reload(@NotNull String key, @NotNull Optional<T> oldCachedValue) {
        ListenableFutureTask<Optional<T>> task = ListenableFutureTask.create(
          () -> {
            if (!oldCachedValue.isPresent()) return Optional.empty();
            MetricsWhitelistHeader header = loader.loadHeader(key);
            if (header.isDeprecated()) return Optional.empty();
            T oldValue = oldCachedValue.get();
            return header.getVersion().equals(oldValue.getHeader().getVersion()) ? oldCachedValue : Optional.of(loader.loadWhitelist(key));
          }
        );
        executorService.execute(task);
        return task;
      }
    });
  }
}
