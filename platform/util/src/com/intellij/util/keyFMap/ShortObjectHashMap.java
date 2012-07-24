/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.keyFMap;

import gnu.trove.TIntObjectProcedure;
import gnu.trove.TPrimitiveHash;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class ShortObjectHashMap<V> extends TPrimitiveHash {
  private V[] _values;
  private short[] _set;


  /**
   * Creates a new <code>TIntObjectHashMap</code> instance with the default
   * capacity and load factor.
   */
  public ShortObjectHashMap() {
    super();
  }

  /**
   * Creates a new <code>TIntObjectHashMap</code> instance with a prime
   * capacity equal to or greater than <tt>initialCapacity</tt> and
   * with the default load factor.
   *
   * @param initialCapacity an <code>int</code> value
   */
  public ShortObjectHashMap(int initialCapacity) {
    super(initialCapacity);
  }

  /**
   * initializes the hashtable to a prime capacity which is at least
   * <tt>initialCapacity + 1</tt>.
   *
   * @param initialCapacity an <code>int</code> value
   * @return the actual capacity chosen
   */
  @Override
  protected int setUp(int initialCapacity) {

    int capacity = super.setUp(initialCapacity);
    //noinspection unchecked
    _values = (V[])new Object[capacity];
    _set = new short[capacity];
    return capacity;
  }

  /**
   * Inserts a key/value pair into the map.
   *
   * @param key   an <code>int</code> value
   * @param value an <code>Object</code> value
   * @return the previous value associated with <tt>key</tt>,
   *         or null if none was found.
   */
  public V put(short key, @NotNull V value) {
    V previous = null;
    int index = insertionIndex(key);
    boolean isNewMapping = true;
    if (index < 0) {
      index = -index - 1;
      previous = _values[index];
      isNewMapping = false;
    }
    byte previousState = _states[index];
    _set[index] = key;
    _states[index] = FULL;
    _values[index] = value;
    if (isNewMapping) {
      postInsertHook(previousState == FREE);
    }

    return previous;
  }

  /**
   * rehashes the map to the new capacity.
   *
   * @param newCapacity an <code>int</code> value
   */
  @Override
  protected void rehash(int newCapacity) {
    int oldCapacity = _set.length;
    short[] oldKeys = _set;
    V[] oldVals = _values;
    byte[] oldStates = _states;

    _set = new short[newCapacity];
    //noinspection unchecked
    _values = (V[])new Object[newCapacity];
    _states = new byte[newCapacity];

    for (int i = oldCapacity; i-- > 0; ) {
      if (oldStates[i] == FULL) {
        short o = oldKeys[i];
        int index = insertionIndex(o);
        _set[index] = o;
        _values[index] = oldVals[i];
        _states[index] = FULL;
      }
    }
  }

  /**
   * retrieves the value for <tt>key</tt>
   *
   * @param key an <code>int</code> value
   * @return the value of <tt>key</tt> or null if no such mapping exists.
   */
  public V get(short key) {
    int index = index(key);
    return index < 0 ? null : _values[index];
  }

  /**
   * Empties the map.
   */
  @Override
  public void clear() {
    super.clear();

    Arrays.fill(_set, (short)0);
    Arrays.fill(_values, null);
    Arrays.fill(_states, FREE);
  }

  /**
   * Deletes a key/value pair from the map.
   *
   * @param key an <code>int</code> value
   * @return an <code>Object</code> value
   */
  public V remove(short key) {
    V prev = null;
    int index = index(key);
    if (index >= 0) {
      prev = _values[index];
      removeAt(index);    // clear key,state; adjust size
    }
    return prev;
  }


  /**
   * removes the mapping at <tt>index</tt> from the map.
   *
   * @param index an <code>int</code> value
   */
  @Override
  protected void removeAt(int index) {
    _values[index] = null;
    _set[index] = 0;
    super.removeAt(index);  // clear key, state; adjust size
  }

  /**
   * Returns the values of the map.
   *
   * @return a <code>Collection</code> value
   */
  public Object[] getValues() {
    Object[] vals = new Object[size()];
    V[] v = _values;
    byte[] states = _states;

    for (int i = v.length, j = 0; i-- > 0; ) {
      if (states[i] == FULL) {
        vals[j++] = v[i];
      }
    }
    return vals;
  }

  /**
   * returns the keys of the map.
   *
   * @return a <code>Set</code> value
   */
  public short[] keys() {
    short[] keys = new short[size()];
    short[] k = _set;
    byte[] states = _states;

    for (int i = k.length, j = 0; i-- > 0; ) {
      if (states[i] == FULL) {
        keys[j++] = k[i];
      }
    }
    return keys;
  }


  /**
   * checks for the present of <tt>key</tt> in the keys of the map.
   *
   * @param key an <code>int</code> value
   * @return a <code>boolean</code> value
   */
  public boolean containsKey(int key) {
    return index(key) >= 0;
  }


  /**
   * Locates the index of <tt>val</tt>.
   *
   * @param val an <code>int</code> value
   * @return the index of <tt>val</tt> or -1 if it isn't in the set.
   */
  protected int index(int val) {
    byte[] states = _states;
    short[] set = _set;
    int length = states.length;
    int hash = val & 0x7fffffff;
    int index = hash % length;

    if (states[index] != FREE &&
        (states[index] == REMOVED || set[index] != val)) {
      // see Knuth, p. 529
      int probe = 1 + hash % (length - 2);

      do {
        index -= probe;
        if (index < 0) {
          index += length;
        }
      }
      while (states[index] != FREE &&
             (states[index] == REMOVED || set[index] != val));
    }

    return states[index] == FREE ? -1 : index;
  }

  /**
   * Locates the index at which <tt>val</tt> can be inserted.  if
   * there is already a value equal()ing <tt>val</tt> in the set,
   * returns that value as a negative integer.
   *
   * @param val an <code>int</code> value
   * @return an <code>int</code> value
   */
  protected int insertionIndex(int val) {

    byte[] states = _states;
    short[] set = _set;
    int length = states.length;
    int hash = val & 0x7fffffff;
    int index = hash % length;

    if (states[index] == FREE) {
      return index;       // empty, all done
    }
    else if (states[index] == FULL && set[index] == val) {
      return -index - 1;   // already stored
    }
    else {                // already FULL or REMOVED, must probe
      // compute the double hash
      int probe = 1 + hash % (length - 2);
      // starting at the natural offset, probe until we find an
      // offset that isn't full.
      do {
        index -= probe;
        if (index < 0) {
          index += length;
        }
      }
      while (states[index] == FULL && set[index] != val);

      // if the index we found was removed: continue probing until we
      // locate a free location or an element which equal()s the
      // one we have.
      if (states[index] == REMOVED) {
        int firstRemoved = index;
        while (states[index] != FREE &&
               (states[index] == REMOVED || set[index] != val)) {
          index -= probe;
          if (index < 0) {
            index += length;
          }
        }
        return states[index] == FULL ? -index - 1 : firstRemoved;
      }
      // if it's full, the key is already stored
      return states[index] == FULL ? -index - 1 : index;
    }
  }

  public boolean forEachEntry(TIntObjectProcedure<V> procedure) {
    byte[] states = _states;
    short[] keys = _set;
    V[] values = _values;
    for (int i = keys.length; i-- > 0; ) {
      if (states[i] == FULL && !procedure.execute(keys[i], values[i])) {
        return false;
      }
    }
    return true;
  }
} // TIntObjectHashMap
