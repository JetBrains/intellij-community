// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.dictionaries;

import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.GsonBuilder;
import com.intellij.internal.statistic.service.fus.FUStatisticsSettingsService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FUSDictionariesServiceImpl implements FUSDictionariesService {
  private static final Logger LOG = Logger.getInstance(FUSDictionariesServiceImpl.class);

  private final ExecutorService myExecutorService;
  private final DictionaryLoader myDictionaryLoader;
  private final LoadingCache<String, FUSCachedDictionary> myCache;

  @FunctionalInterface
  interface DictionaryLoader {
    @NotNull
    FUSCachedDictionary load(@NotNull String dictionaryId) throws Exception;
  }

  // Used by ServiceManager.
  @SuppressWarnings("unused")
  private FUSDictionariesServiceImpl() {
    myExecutorService = Executors.newSingleThreadExecutor();
    myDictionaryLoader = FUSDictionariesServiceImpl::loadDictionary;
    myCache = create(myExecutorService, myDictionaryLoader, null);
  }

  @TestOnly
  FUSDictionariesServiceImpl(@NotNull DictionaryLoader dictionaryLoader, @NotNull Ticker ticker) {
    myExecutorService = MoreExecutors.newDirectExecutorService();
    myDictionaryLoader = dictionaryLoader;
    myCache = create(myExecutorService, myDictionaryLoader, ticker);
  }

  @Override
  @Nullable
  public FUSDictionary getDictionary(@NotNull String id) {
    FUSCachedDictionary cachedDictionary = myCache.getIfPresent(id);
    if (cachedDictionary != null) {
      return FUSCachedDictionary.DEPRECATED.equals(cachedDictionary) ? null : cachedDictionary.getDictionary();
    }
    myExecutorService.execute(() -> {
      try {
        myCache.get(id);
      }
      catch (ExecutionException e) {
        LOG.warn(e);
      }
    });
    return null;
  }

  @Override
  public void asyncPreloadDictionaries(@NotNull Set<String> ids) {
    for (String dictionaryId : ids) {
      getDictionary(dictionaryId);
    }
  }

  @NotNull
  private static LoadingCache<String, FUSCachedDictionary> create(@NotNull ExecutorService executorService,
                                                                  @NotNull DictionaryLoader dictionaryLoader,
                                                                  @Nullable Ticker customTicker) {
    CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder()
      .maximumSize(100)
      .refreshAfterWrite(1, TimeUnit.DAYS) // refresh after a day since the last write
      .expireAfterAccess(1, TimeUnit.DAYS); // if not read/written withing a day, remove from cache
    if (customTicker != null) {
      builder.ticker(customTicker);
    }
    return builder.build(new CacheLoader<String, FUSCachedDictionary>() {
      @Override
      public FUSCachedDictionary load(@NotNull String key) throws Exception {
        return dictionaryLoader.load(key);
      }

      @Override
      public ListenableFuture<FUSCachedDictionary> reload(@NotNull String key, @NotNull FUSCachedDictionary oldValue) {
        ListenableFutureTask<FUSCachedDictionary> task = ListenableFutureTask.create(() -> dictionaryLoader.load(key));
        executorService.execute(task);
        return task;
      }
    });
  }

  @NotNull
  private static FUSCachedDictionary loadDictionary(@NotNull String id) throws IOException {
    String serviceUrl = FUStatisticsSettingsService.getInstance().getDictionaryServiceUrl();
    if (serviceUrl == null) {
      throw new IOException("Failed to get dictionary service url");
    }
    String content;
    try {
      content = HttpRequests.request(serviceUrl + id + ".json")
        .productNameAsUserAgent()
        .readString(null);
    }
    catch (IOException e) {
      throw new IOException("Failed to download dictionary", e);
    }
    Dictionary dictionary;
    try {
      dictionary = new GsonBuilder().create().fromJson(content, Dictionary.class);
    }
    catch (Exception e) {
      throw new IOException("Failed to parse dictionary", e);
    }
    if (dictionary == null || dictionary.version == null || dictionary.dictionary == null) {
      throw new IOException("Unexpected dictionary format, some data may be missing");
    }
    if (dictionary.deprecated) {
      LOG.warn("Dictionary is deprecated, id: " + id);
      return FUSCachedDictionary.DEPRECATED;
    }
    return new FUSCachedDictionary(new FUSDictionary(dictionary.version, dictionary.dictionary));
  }

  // Fields of this class are used during json unmarshalling via Gson.
  @SuppressWarnings("unused")
  private static class Dictionary {
    private String version;
    private Map<String, List<String>> dictionary;
    private boolean deprecated;
  }
}