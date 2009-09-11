package org.jetbrains.plugins.groovy.dsl.toplevel.scopes

/**
 * @author ilyas
 */
abstract class Scope {}

class Any extends Scope {}

class ClosureScope extends Scope {
  private final boolean isArg
  private final boolean isTransparent

  ClosureScope(Map args) {
    isArg == args.isArgument
    isTransparent = args.transparent
  }

  def isArg() {
    isArg
  }

  def transparent() {
    isTransparent
  }
}

class ScriptScope extends Scope {
  final private myNamePattern

  ScriptScope(Map agrs) {
    myNamePattern = args.name ?: /.*/
  }

  def getName() {
    myNamePattern
  }
}
