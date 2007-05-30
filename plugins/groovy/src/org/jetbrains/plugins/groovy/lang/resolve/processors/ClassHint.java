package org.jetbrains.plugins.groovy.lang.resolve.processors;

/**
 * @author ven
 */
public interface ClassHint {
  enum ResolveKind {
    CLASS_OR_PACKAGE,
    METHOD,
    PROPERTY
  }

  boolean shouldProcess(ResolveKind resolveKind);
}
