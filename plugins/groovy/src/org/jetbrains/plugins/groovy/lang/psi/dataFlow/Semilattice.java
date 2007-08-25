package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

/**
 * @author ven
 */
public interface Semilattice<E> {
  E cap (E e1, E e2);

  boolean eq (E e1, E e2);
}
