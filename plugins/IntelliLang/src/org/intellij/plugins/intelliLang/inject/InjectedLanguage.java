/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class InjectedLanguage {
  private static Map<String, Language> ourLanguageCache;
  private static int ourLanguageCount;

  @NotNull
  private final String myID;
  @NotNull
  private final String myPrefix;
  @NotNull
  private final String mySuffix;
  private final boolean myDynamic;

  private InjectedLanguage(@NotNull String id, @NotNull String prefix, @NotNull String suffix, boolean dynamic) {
    myID = id;
    myPrefix = prefix;
    mySuffix = suffix;
    myDynamic = dynamic;
  }

  @NotNull
  public String getID() {
    return myID;
  }

  @Nullable
  public Language getLanguage() {
    return findLanguageById(myID);
  }

  @NotNull
  public String getPrefix() {
    return myPrefix;
  }

  @NotNull
  public String getSuffix() {
    return mySuffix;
  }

  /**
   * Returns whether prefix/suffix were computed dynamically
   */
  public boolean isDynamic() {
    return myDynamic;
  }

  @Nullable
  public static Language findLanguageById(@Nullable String langID) {
    if (langID == null || langID.isEmpty()) {
      return null;
    }
    synchronized (InjectedLanguage.class) {
      if (ourLanguageCache == null || ourLanguageCount != Language.getRegisteredLanguages().size()) {
        initLanguageCache();
      }
      return ourLanguageCache.get(langID);
    }
  }

  @NotNull
  public static String[] getAvailableLanguageIDs() {
    synchronized (InjectedLanguage.class) {
      if (ourLanguageCache == null || ourLanguageCount != Language.getRegisteredLanguages().size()) {
        initLanguageCache();
      }
      final Set<String> keys = ourLanguageCache.keySet();
      return ArrayUtil.toStringArray(keys);
    }
  }

  @NotNull
  public static Language[] getAvailableLanguages() {
    synchronized (InjectedLanguage.class) {
      if (ourLanguageCache == null || ourLanguageCount != Language.getRegisteredLanguages().size()) {
        initLanguageCache();
      }
      final Collection<Language> keys = ourLanguageCache.values();
      return keys.toArray(new Language[keys.size()]);
    }
  }

  private static void initLanguageCache() {
    ourLanguageCache = new HashMap<>();

    Collection<Language> registeredLanguages;
    do {
      registeredLanguages = new ArrayList<>(Language.getRegisteredLanguages());
      for (Language language : registeredLanguages) {
        if (LanguageUtil.isInjectableLanguage(language)) {
          ourLanguageCache.put(language.getID(), language);
        }
      }
    } while (Language.getRegisteredLanguages().size() != registeredLanguages.size());

    ourLanguageCount = registeredLanguages.size();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final InjectedLanguage that = (InjectedLanguage)o;

    return myID.equals(that.myID);
  }

  public int hashCode() {
    return myID.hashCode();
  }

  @Nullable
  public static InjectedLanguage create(String id) {
    return create(id, "", "", false);
  }

  @Nullable
  public static InjectedLanguage create(@Nullable String id, String prefix, String suffix, boolean isDynamic) {
    return id == null ? null : new InjectedLanguage(id, prefix == null ? "" : prefix, suffix == null ? "" : suffix, isDynamic);
  }
}
