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

/**
 * @deprecated Use {@link it.unimi.dsi.fastutil.ints.IntArrayList}
 */
@Deprecated
public final class TIntStack {

  /**
   * the list used to hold the stack values.
   */
  private TIntArrayList _list;

  public static final int DEFAULT_CAPACITY = TIntArrayList.DEFAULT_CAPACITY;

  /**
   * Creates a new <code>TIntStack</code> instance with the default
   * capacity.
   */
  public TIntStack() {
    this(DEFAULT_CAPACITY);
  }

  /**
   * Copy constructor.
   */
  public TIntStack(TIntStack copy) {
    _list = new TIntArrayList(copy._list.toNativeArray());
  }

  /**
   * Creates a new <code>TIntStack</code> instance with the
   * specified capacity.
   *
   * @param capacity the initial depth of the stack
   */
  public TIntStack(int capacity) {
    _list = new TIntArrayList(capacity);
  }

  /**
   * Pushes the value onto the top of the stack.
   *
   * @param val an <code>int</code> value
   */
  public void push(int val) {
    _list.add(val);
  }

  /**
   * Removes and returns the value at the top of the stack.
   *
   * @return an <code>int</code> value
   */
  public int pop() {
    return _list.remove(_list.size() - 1);
  }

  /**
   * Returns the value at the top of the stack.
   *
   * @return an <code>int</code> value
   */
  public int peek() {
    return _list.get(_list.size() - 1);
  }

  /**
   * Returns the current depth of the stack.
   *
   * @return an <code>int</code> value
   */
  public int size() {
    return _list.size();
  }

  /**
   * Clears the stack, reseting its capacity to the default.
   */
  public void clear() {
    _list.clear(DEFAULT_CAPACITY);
  }

  /**
   * Clears the stack without releasing its internal capacity allocation.
   */
  public void reset() {
    _list.reset();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TIntStack)) return false;

    final TIntStack tIntStack = (TIntStack)o;

    return _list.equals(tIntStack._list);
  }

  public int hashCode() {
    return _list.hashCode();
  }
} // TIntStack
