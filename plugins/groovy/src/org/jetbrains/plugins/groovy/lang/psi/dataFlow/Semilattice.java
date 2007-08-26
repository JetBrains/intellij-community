package org.jetbrains.plugins.groovy.lang.psi.dataFlow;

import java.util.ArrayList;

/**
 * @author ven
 */
public interface Semilattice<E> {
  E join(ArrayList<E> ins);

  boolean eq (E e1, E e2);
}
