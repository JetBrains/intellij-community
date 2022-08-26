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
import java.util.NoSuchElementException;

/**
 * Abstract iterator class for THash implementations.  This class provides some
 * of the common iterator operations (hasNext(), remove()) and allows subclasses
 * to define the mechanism(s) for advancing the iterator and returning data.
 *
 * @author Eric D. Friedman
 * @version $Id: TIterator.java,v 1.6 2004/09/24 09:11:15 cdr Exp $
 */
abstract class TIterator {
  /**
   * the data structure this iterator traverses
   */
  protected final THash _hash;
  /**
   * the number of elements this iterator believes are in the
   * data structure it accesses.
   */
  protected int _expectedSize;
  /**
   * the index used for iteration.
   */
  protected int _index;

  /**
   * Create an instance of TIterator over the specified THash.
   */
  TIterator(THash hash) {
    _hash = hash;
    _expectedSize = _hash.size();
    _index = _hash.capacity();
  }

  /**
   * Returns true if the iterator can be advanced past its current
   * location.
   *
   * @return a <code>boolean</code> value
   */
  public boolean hasNext() {
    return nextIndex() >= 0;
  }

  /**
   * Removes the last entry returned by the iterator.
   * Invoking this method more than once for a single entry
   * will leave the underlying data structure in a confused
   * state.
   */
  public void remove() {
    if (_expectedSize != _hash.size()) {
      throw new ConcurrentModificationException();
    }

    _hash.stopCompactingOnRemove();
    try {
      _hash.removeAt(_index);
    }
    finally {
      _hash.startCompactingOnRemove(false);
    }

    _expectedSize--;
  }

  /**
   * Sets the internal <tt>index</tt> so that the `next' object
   * can be returned.
   */
  protected final void moveToNextIndex() {
    // doing the assignment && < 0 in one line shaves
    // 3 opcodes...
    if ((_index = nextIndex()) < 0) {
      throw new NoSuchElementException();
    }
  }

  /**
   * Returns the index of the next value in the data structure
   * or a negative value if the iterator is exhausted.
   *
   * @return an <code>int</code> value
   */
  protected abstract int nextIndex();
} // TIterator
