package org.jetbrains.plugins.groovy.dsl.toplevel

import org.jetbrains.plugins.groovy.dsl.ClassDescriptor
import org.jetbrains.plugins.groovy.dsl.GroovyDslExecutor
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClassScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClosureScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ScriptScope

/**
 * @author ilyas
 */
class GdslMetaClassProperties {

  private final GroovyDslExecutor myExecutor

  public GdslMetaClassProperties(GroovyDslExecutor executor) {
    myExecutor = executor
  }

  /**
   * Context definition
   */
  Closure context = {Map args ->
    def ctx = new Context(args)
    return ctx
  }

  /**
   * Contributor definition
   */
  Closure contributor = {cts, Closure toDo ->
    def contrib = new Contributor(cts, toDo)
    myExecutor.addClassEnhancer {
      ClassDescriptor descriptor, consumer ->
      myExecutor.runContributor(contrib, descriptor, consumer)
    }
  }

  /**
   * Auxiliary methods for context definition
   */
  Closure ClosureScope = {Map args -> return new ClosureScope(args)}
  Closure ScriptScope = {Map args -> return new ScriptScope(args)}
  Closure ClassScope = {Map args -> return new ClassScope(args)}


}

