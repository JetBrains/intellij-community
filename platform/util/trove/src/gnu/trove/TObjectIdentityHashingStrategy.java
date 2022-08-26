// Copyright (c) 2002, Eric D. Friedman All Rights Reserved.

package gnu.trove;

/**
 * This object hashing strategy uses the System.identityHashCode
 * method to provide identity hash codes.  These are identical to the
 * value produced by Object.hashCode(), even when the type of the
 * object being hashed overrides that method.
 * <p>
 * Created: Sat Aug 17 11:13:15 2002
 *
 * @author Eric Friedman
 * @version $Id: TObjectIdentityHashingStrategy.java,v 1.8 2004/09/24 09:11:15 cdr Exp $
 */

public final class TObjectIdentityHashingStrategy<T> implements TObjectHashingStrategy<T> {
  /**
   * Delegates hash code computation to the System.identityHashCode(Object) method.
   *
   * @param object for which the hashcode is to be computed
   * @return the hashCode
   */
  @Override
  public int computeHashCode(T object) {
    return System.identityHashCode(object);
  }

  /**
   * Compares object references for equality.
   *
   * @param o1 an <code>Object</code> value
   * @param o2 an <code>Object</code> value
   * @return true if o1 == o2
   */
  @Override
  public boolean equals(T o1, T o2) {
    return o1 == o2;
  }
} // TObjectIdentityHashingStrategy
