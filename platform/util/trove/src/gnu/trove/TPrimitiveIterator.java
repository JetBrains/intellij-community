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
abstract class TPrimitiveIterator extends TIterator {
  /**
   * the collection on which this iterator operates.
   */
  protected final TPrimitiveHash _hash;

  /**
   * Creates a TPrimitiveIterator for the specified collection.
   */
  TPrimitiveIterator(TPrimitiveHash hash) {
    super(hash);
    _hash = hash;
  }

  /**
   * Returns the index of the next value in the data structure
   * or a negative value if the iterator is exhausted.
   *
   * @return an <code>int</code> value
   * @throws ConcurrentModificationException if the underlying collection's
   *                                         size has been modified since the iterator was created.
   */
  @Override
  protected final int nextIndex() {
    if (_expectedSize != _hash.size()) {
      throw new ConcurrentModificationException();
    }

    byte[] states = _hash._states;
    int i = _index;
    while (i-- > 0 && (states[i] != TPrimitiveHash.FULL)) ;
    return i;
  }
} // TPrimitiveIterator
