/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.impl.softwrap.mapping.DataProvider;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * There is an ubiquitous situation when single source has various different sub-sources. E.g. there is a document
 * and its soft wraps, fold regions etc.
 * <p/>
 * So, it's often convenient to encapsulate those sources in order to provide every sub-source instance one-by-one
 * in particular order. This class does exactly that job, i.e. it encapsulates multiple {@link DataProvider} instance
 * and exposes their data by {@link DataProvider#getSortingKey() sorting order}.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since Aug 31, 2010 10:57:44 AM
 */
public class CompositeDataProvider {

  /**
   * We use array here for performance reasons (on the basis of profiling results analysis).
   */
  private final DataProvider<? extends Comparable<?>, ?>[] myProviders;

  /**
   * Creates new <code>CompositeDataProvider</code> object with the given providers to register.
   *
   * @param providers   providers to register within the current composite provider
   */
  public CompositeDataProvider(@NotNull DataProvider<? extends Comparable<?>, ?> ... providers) {
    // We assume here that given array ownership belongs to the current object now.
    for (int i = 0; i < providers.length; i++) {
      DataProvider<? extends Comparable<?>, ?> provider = providers[i];
      if (provider.getData() == null) {
        providers[i] = null;
      }
    }
    myProviders = providers;
    sort();
  }

  /**
   * Allows to answer if there is any data at the current provider.
   * <p/>
   * I.e. it's safe to call {@link #getData()} if this method returns <code>true</code>.
   *
   * @return    <code>true</code> if there is a data at the current provider; <code>false</code> otherwise
   */
  public boolean hasData() {
    for (DataProvider<? extends Comparable<?>, ?> provider : myProviders) {
      if (provider != null) {
        return true;
      }
    }
    return false;
  }

  /**
   * Asks current provider to ignore all data which {@link DataProvider#getSortingKey() sorting keys} are less than the given value.
   *
   * @param sortingKey    min sorting key to use
   */
  public void advance(int sortingKey) {
    for (int i = 0; i < myProviders.length; i++) {
      DataProvider<? extends Comparable<?>, ?> provider = myProviders[i];
      if (provider == null) {
        continue;
      }
      provider.advance(sortingKey);
      if (provider.getData() == null) {
        myProviders[i] = null;
      }
    }
    sort();
  }

  /**
   * Retrieves the data with the the smallest {@link DataProvider#getSortingKey() sorting key} from registered providers.
   * <p/>
   * <b>Note:</b> subsequent invocations of this method are expected to return the same value unless {@link #next()} is called.
   *
   * @param <K>   data key type
   * @param <V>   data value type
   * @return      <code>(key; value)</code> pair that is {@link DataProvider#getData() returned from data provider} with the
   *              smallest {@link DataProvider#getSortingKey() sorting key}
   * @throws IllegalStateException    if no data left at the current composite provider
   * @see #hasData()
   */
  @SuppressWarnings({"unchecked"})
  @NotNull
  public <K, V> Pair<K, V> getData() throws IllegalStateException {
    if (!hasData()) {
      throw new IllegalStateException("No more data is available within the current provider.");
    }
    DataProvider<?, ?> provider = myProviders[0];
    Pair<K, V> result = (Pair<K, V>)provider.getData();
    if (result == null) {
      throw new IllegalStateException(String.format("Programming error detected - registered data provider doesn't have data (%s). "
                                                      + "Registered providers: %s", provider, Arrays.toString(myProviders)));
    }
    return result;
  }

  /**
   * Allows to ask for the {@link DataProvider#getSortingKey() sorting key} of the {@link #getData() current data}.
   * <p/>
   * It was possible to add sorting key to the {@link #getData()} result but the idea was to avoid unnecessary boxing/un-boxing.
   *
   * @return    sorting key for the {@link #getData() current data} if any.
   * @throws IllegalStateException    if there is no data within the current provider
   */
  public int getSortingKey() throws IllegalStateException {
    if (!hasData()) {
      throw new IllegalStateException("No more data is available within the current provider");
    }

    return myProviders[0].getSortingKey();
  }

  /**
   * Asks current provider to drop the data {@link #getData() returned last time} and advance to the next data with the
   * smallest {@link DataProvider#getSortingKey() sorting key}.
   *
   * @return      <code>true</code> if there is more data at the current provider ({@link #hasData()} returns <code>true</code>
   *              and {@link #getData()} doesn't throw exception); <code>false</code> otherwise
   */
  public boolean next() {
    if (!hasData()) {
      return false;
    }

    DataProvider<?, ?> provider = myProviders[0];
    if (!provider.next()) {
      myProviders[0] = null;
    }
    if (hasData()) {
      sort();
      return true;
    }
    return false;
  }

  private void sort() {
    if (myProviders.length <= 0) {
      return;
    }

    // Profiling results analysis-implied optimization.
    int i = 0;
    DataProvider<? extends Comparable<?>, ?> dataProvider = myProviders[0];
    for (int j = 1; j < myProviders.length; j++) {
      DataProvider<? extends Comparable<?>, ?> candidate = myProviders[j];
      if (candidate == null) {
        continue;
      }
      if (dataProvider == null || compare(dataProvider, candidate) > 0) {
        i = j;
        dataProvider = candidate;
      }
    }

    if (i > 0) {
      myProviders[i] = myProviders[0];
      myProviders[0] = dataProvider;
    }
  }

  @SuppressWarnings({"unchecked"})
  private static int compare(DataProvider<? extends Comparable<?>, ?> o1, DataProvider<? extends Comparable<?>, ?> o2) {
    // We assume here that given data providers have data to expose.
    int result = o1.getSortingKey() - o2.getSortingKey();
    if (result != 0) {
      return result;
    }

    Pair<? extends Comparable<Object>, ?> d1 = (Pair<? extends Comparable<Object>, ?>)o1.getData();
    Pair<? extends Comparable<Object>, ?> d2 = (Pair<? extends Comparable<Object>, ?>)o2.getData();
    if (d1 != null && d2 != null) {
      return d1.first.compareTo(d2.first);
    }
    return result;
  }
}
