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

import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stands for key-value storage with <code>int</code> keys that allows to mark particular range for deletion and
 * remove it later.
 * 
 * @author Denis Zhdanov
 * @since 11/15/10 10:56 AM
 * @param <T>   value type
 */
class DelayedRemovalMap<T> {
  
  //TODO den add simple array that tracks stored key in assumption that they are adjacent
  private final TIntObjectHashMap<T> myMap = new TIntObjectHashMap<T>();
  private final TIntHashSet myKeysToRemove = new TIntHashSet();
  private final TIntProcedure myRemoveEntriesProcedure = new TIntProcedure() {
    @Override
    public boolean execute(int value) {
      myMap.remove(value);
      return true;
    }
  };

  /**
   * Asks for the value stored for the given key if any.
   *  
   * @param key   target key
   * @return      value mapped for the given key if any; <code>null</code> otherwise
   */
  @Nullable
  public T get(int key) {
    return myMap.get(key);
  }

  /**
   * Stores mapping between the given key and value.
   * 
   * @param key       target key
   * @param value     target value
   */
  public void put(int key, @NotNull T value) {
    myMap.put(key, value);
    myKeysToRemove.remove(key);
  }

  /**
   * Marks all entries of the given map that belongs to the <code>[start; end]</code> interval for further removal (triggered by
   * call to {@link #deleteMarked()}).
   * 
   * @param start   target key's interval start (inclusive)
   * @param end     target key's interval end (inclusive)
   */
  public void markForDeletion(int start, int end) {
    for (int i = start; i <= end; i++) {
      if (myMap.containsKey(i)) {
        myKeysToRemove.add(i);
      }
    }
  }

  /**
   * Asks current map to remove all keys that were marked during {@link #markForDeletion(int, int)}.
   * <p/>
   * <b>Note:</b> following actions sequence is possible:
   * <pre>
   * <ol>
   *   <li>
   *      {@link #put(int, Object)} is called for entries with keys, say, <code>'1'</code>, <code>'2'</code> and <code>'3'</code>;
   *   </li>
   *   <li>
   *      {@link #markForDeletion(int, int)} is called with range <code>[2; 3]</code>;
   *   </li>
   *   <li>
   *      {@link #put(int, Object)} is called for key <code>'2'</code>;
   *   </li>
   *   <li>
   *      {@link #deleteMarked()} is called;
   *   </li>
   * </ol>
   * </pre>
   * Entry with key <code>'2'</code> is not removed on {@link #deleteMarked()} processing then because it was updated <b>after</b>
   * {@link #markForDeletion(int, int)}.
   */
  public void deleteMarked() {
    myKeysToRemove.forEach(myRemoveEntriesProcedure);
  }

  /**
   * Asks current map to drop all entries which keys are greater or equal to the given one.
   * 
   * @param key   target start key
   */
  public void deleteFrom(final int key) {
    myMap.retainEntries(new TIntObjectProcedure<T>() {
      @Override
      public boolean execute(int a, T b) {
        if (a >= key) {
          myKeysToRemove.remove(a);
          return false;
        }
        return true;
      }
    });
  }

  /**
   * Asks current map to clear its state.
   */
  public void clear() {
    myMap.clear();
    myKeysToRemove.clear();
  }
  
  public int size() {
    return myMap.size();
  }
}
