/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class MergeQuery<T> implements Query<T>{
  private final Query<? extends T> myQuery1;
  private final Query<? extends T> myQuery2;

  public MergeQuery(final Query<? extends T> query1, final Query<? extends T> query2) {
    myQuery1 = query1;
    myQuery2 = query2;
  }

  @NotNull
  public Collection<T> findAll() {
    List<T> results = new ArrayList<T>();
    results.addAll(myQuery1.findAll());
    results.addAll(myQuery2.findAll());
    return results;
  }

  public T findFirst() {
    final T r1 = myQuery1.findFirst();
    if (r1 != null) return r1;
    return myQuery2.findFirst();
  }

  public boolean forEach(@NotNull final Processor<T> consumer) {
    return processSubQuery(consumer, myQuery1) || processSubQuery(consumer, myQuery2);
  }

  private <V extends T> boolean processSubQuery(final Processor<T> consumer, Query<V> query1) {
    return query1.forEach(new Processor<V>() {
      public boolean process(final V t) {
        return consumer.process(t);
      }
    });
  }

  public T[] toArray(final T[] a) {
    final Collection<T> results = findAll();
    return results.toArray(a);
  }

  public Iterator<T> iterator() {
    return findAll().iterator();
  }
}
