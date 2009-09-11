package org.jetbrains.plugins.groovy.dsl.toplevel

/**
 * @author ilyas
 */
class ContextFilter {

  private final Closure myChecker

  private ContextFilter(Closure cl) {
    myChecker = cl
  }

  boolean check() {
    myChecker()
  }

  static ContextFilter create(Closure cl) {
    new ContextFilter(cl)
  }
  
}
