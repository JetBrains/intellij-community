// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.dictionaries;

import com.google.gson.GsonBuilder;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.DefaultMetricsWhitelistHeader;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelist;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelistFactory;
import com.intellij.internal.statistic.utils.metricsWhitelist.core.MetricsWhitelistHeader;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

public class FUSRegexDictionary implements MetricsWhitelist {
  private final MetricsWhitelistHeader myHeader;
  private final List<Pair<Pattern, String>> myEntries;

  private static final long REGEX_MATCH_MAX_MILLISECONDS = 250;

  private FUSRegexDictionary(boolean deprecated, @NotNull String version, @NotNull List<Pair<Pattern, String>> entries) {
    myHeader = new DefaultMetricsWhitelistHeader(deprecated, version);
    myEntries = ContainerUtil.unmodifiableOrEmptyList(entries);
  }

  @NotNull
  @Override
  public MetricsWhitelistHeader getHeader() {
    return myHeader;
  }

  @Nullable
  public String lookupMetric(@NotNull String metric) {
    for (Pair<Pattern, String> entry : myEntries) {
      try {
        if (entry.getFirst().matcher(timeLimitedSequence(metric)).find()) {
          return entry.getSecond();
        }
      }
      catch (ProcessCanceledException e) {
        // continue
      }
    }
    return null;
  }

  private static CharSequence timeLimitedSequence(@NotNull String str) {
    final long expirationTime = System.currentTimeMillis() + REGEX_MATCH_MAX_MILLISECONDS;
    return new StringUtil.BombedCharSequence(str) {
      @Override
      protected void checkCanceled() {
        if (System.currentTimeMillis() > expirationTime) {
          throw new ProcessCanceledException();
        }
      }
    };
  }

  public static class Factory implements MetricsWhitelistFactory<FUSRegexDictionary> {
    @Nullable
    @Override
    public MetricsWhitelistHeader createHeader(@NotNull String rawHeader) {
      try {
        Header header = new GsonBuilder().create().fromJson(rawHeader, Header.class);
        return header != null && header.version != null ? new DefaultMetricsWhitelistHeader(header.deprecated, header.version) : null;
      }
      catch (Exception e) {
        return null;
      }
    }

    @Nullable
    @Override
    public FUSRegexDictionary createWhitelist(@NotNull String rawDictionary) {
      Dictionary dictionary;
      try {
        dictionary = new GsonBuilder().create().fromJson(rawDictionary, Dictionary.class);
      }
      catch (Exception e) {
        return null;
      }
      if (dictionary == null || dictionary.header == null || dictionary.header.version == null || dictionary.entries == null) {
        return null;
      }
      List<Pair<Pattern, String>> entries = ContainerUtil.newSmartList();
      for (Entry entry : dictionary.entries) {
        try {
          Pattern pattern = Pattern.compile(entry.pattern);
          entries.add(Pair.create(pattern, entry.metric));
        }
        catch (Exception e) {
          return null;
        }
      }
      return new FUSRegexDictionary(dictionary.header.deprecated, dictionary.header.version, entries);
    }

    // Fields of these classes are used during json unmarshalling via Gson.
    @SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
    private static class Dictionary {
      private Header header;
      private List<Entry> entries;
    }

    @SuppressWarnings("unused")
    private static class Header {
      private boolean deprecated;
      private String version;
    }

    @SuppressWarnings("unused")
    private static class Entry {
      private String pattern;
      private String metric;
    }
  }
}
