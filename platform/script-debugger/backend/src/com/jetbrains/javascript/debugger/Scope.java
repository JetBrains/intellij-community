package com.jetbrains.javascript.debugger;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An object that represents a scope in JavaScript. It could be either declarative or object
 * scope.
 */
public interface Scope {
  enum Type {
    GLOBAL,
    LOCAL,
    WITH,
    CLOSURE,
    CATCH,
    UNKNOWN
  }

  Type getType();

  @Nullable
  String getClassName();

  @NotNull
  AsyncResult<List<? extends Variable>> getVariables();

  /**
   * Mirrors <i>declarative</i> scope. It's all scopes except 'with' and 'global
   */
  interface Declarative extends Scope {
  }

  /**
   * Mirrors <i>object</i> scope, i.e. the one built above a JavaScript object. It's either
   * 'with' or 'global' scope. Such scope contains all properties of the object, including
   * indirect ones from the prototype chain.
   */
  interface ObjectBased extends Scope {
  }
}