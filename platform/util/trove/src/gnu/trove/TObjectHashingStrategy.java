// Copyright (c) 2002, Eric D. Friedman All Rights Reserved.

package gnu.trove;

import java.io.Serializable;
import java.util.Objects;

/**
 * Interface to support pluggable hashing strategies in maps and sets.
 * Implementors can use this interface to make the trove hashing
 * algorithms use object values, values provided by the java runtime,
 * or a custom strategy when computing hashcodes.
 * <p>
 * Created: Sat Aug 17 10:52:32 2002
 *
 * @author Eric Friedman
 * @version $Id: TObjectHashingStrategy.java,v 1.6 2004/09/24 09:11:15 cdr Exp $
 */
public interface TObjectHashingStrategy<T> extends Serializable, Equality<T> {
  /**
   * Computes a hash code for the specified object.  Implementors
   * can use the object's own <tt>hashCode</tt> method, the Java
   * runtime's <tt>identityHashCode</tt>, or a custom scheme.
   *
   * @param object for which the hashcode is to be computed
   * @return the hashCode
   */
  int computeHashCode(T object);

  /**
   * Compares o1 and o2 for equality.  Strategy implementors may use
   * the objects' own equals() methods, compare object references,
   * or implement some custom scheme.
   *
   * @param o1 an <code>Object</code> value
   * @param o2 an <code>Object</code> value
   * @return true if the objects are equal according to this strategy.
   */
  @Override
  boolean equals(T o1, T o2);

  @Deprecated
  TObjectHashingStrategy CANONICAL = new TObjectCanonicalHashingStrategy();
} // TObjectHashingStrategy

final class TObjectCanonicalHashingStrategy<T> implements TObjectHashingStrategy<T> {
  @Override
  public int computeHashCode(T value) {
    return value != null ? value.hashCode() : 0;
  }

  @Override
  public boolean equals(T value, T value1) {
    return Objects.equals(value, value1);
  }
}