package org.jetbrains.plugins.groovy.dsl.toplevel

import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.Any
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClosureScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ScriptScope

import org.jetbrains.plugins.groovy.dsl.*

/**
 * @author ilyas
 */
class GdslMetaClassProperties {

  private final GroovyDslExecutor myExecutor

  public GdslMetaClassProperties(GroovyDslExecutor executor) {
    myExecutor = executor
  }

  Closure enhanceScript = {Map args, Closure enh ->
    enh.resolveStrategy = Closure.DELEGATE_FIRST
    myExecutor.addScriptEnhancer {
      ScriptDescriptor wrapper, consumer ->
      if (args.extension == wrapper.extension) {
        myExecutor.runEnhancer enh, consumer
      }
    }
  }

  Closure enhanceClass = {Map args, Closure enh ->
    enh.resolveStrategy = Closure.DELEGATE_FIRST
    myExecutor.addClassEnhancer {
      ClassDescriptor cw, consumer ->
      if (!args.className || cw.getQualifiedName() == args.className) {
        myExecutor.runEnhancer enh, consumer
      }
    }
  }

  /**
   * Context definition
   */
  Closure context = {Map args ->
    def ctx = new Context(args)
    myExecutor.addContext(ctx)
    return ctx
  }

  /**
   * Contributor definition
   */
  Closure contributor = {List cts, Closure toDo ->
    def contrib = new Contributor(cts, toDo)
    myExecutor.addClassEnhancer {
      ClassDescriptor descriptor, consumer ->
      myExecutor.runContributor(contrib, descriptor, consumer)
    }

  }

  /**
   * Auxiliary methods for context definition
   */
  Closure ClosureScope = {Map args -> new ClosureScope(args)}
  Closure ScriptScope = {Map args -> new ScriptScope(args)}
  Closure Any = {Map args -> new Any()}


}

