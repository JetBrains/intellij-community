package org.jetbrains.concurrency;

public interface Obsolescent {
  /**
   * @return <code>true</code> if result of computation won't be used so computation may be interrupted
   */
  boolean isObsolete();
}
