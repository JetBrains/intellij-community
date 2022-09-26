///////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2001, Eric D. Friedman All Rights Reserved.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
///////////////////////////////////////////////////////////////////////////////

package gnu.trove;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Implements all iterator functions for the hashed object set.
 * Subclasses may override objectAtIndex to vary the object
 * returned by calls to next() (e.g. for values, and Map.Entry
 * objects).
 *
 * <p> Note that iteration is fastest if you forego the calls to
 * <tt>hasNext</tt> in favor of checking the size of the structure
 * yourself and then call next() that many times:
 *
 * <pre>
 * Iterator i = collection.iterator();
 * for (int size = collection.size(); size-- > 0;) {
 *   Object o = i.next();
 * }
 * </pre>
 *
 * <p>You may, of course, use the hasNext(), next() idiom too if
 * you aren't in a performance critical spot.</p>
 */
abstract class THashIterator<V> extends TIterator implements Iterator<V> {
  protected final TObjectHash _hash;

  /**
   * Create an instance of THashIterator over the values of the TObjectHash
   */
  THashIterator(TObjectHash hash) {
    super(hash);
    _hash = hash;
  }

  /**
   * Moves the iterator to the next Object and returns it.
   *
   * @return an <code>Object</code> value
   * @throws ConcurrentModificationException if the structure
   *                                         was changed using a method that isn't on this iterator.
   * @throws NoSuchElementException          if this is called on an
   *                                         exhausted iterator.
   */
  @Override
  public V next() {
    moveToNextIndex();
    return objectAtIndex(_index);
  }

  /**
   * Returns the index of the next value in the data structure
   * or a negative value if the iterator is exhausted.
   *
   * @return an <code>int</code> value
   * @throws ConcurrentModificationException if the underlying
   *                                         collection's size has been modified since the iterator was
   *                                         created.
   */
  @Override
  protected final int nextIndex() {
    if (_expectedSize != _hash.size()) {
      throw new ConcurrentModificationException();
    }

    Object[] set = _hash._set;
    int i = _index;
    while (i-- > 0 && (set[i] == null || set[i] == TObjectHash.REMOVED)) ;
    return i;
  }

  /**
   * Returns the object at the specified index.  Subclasses should
   * implement this to return the appropriate object for the given
   * index.
   *
   * @param index the index of the value to return.
   * @return an <code>Object</code> value
   */
  protected abstract V objectAtIndex(int index);
} // THashIterator
