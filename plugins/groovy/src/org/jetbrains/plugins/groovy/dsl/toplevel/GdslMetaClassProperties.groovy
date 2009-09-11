package org.jetbrains.plugins.groovy.dsl.toplevel

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.Any
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClosureScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ScriptScope
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.dsl.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

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
      ScriptDescriptor wrapper, GroovyEnhancerConsumer c ->
      if (args.extension == wrapper.extension) {
        myExecutor.runEnhancer enh, new EnhancerDelegate(consumer: c)
      }
    }
  }

  Closure enhanceClass = {Map args, Closure enh ->
    enh.resolveStrategy = Closure.DELEGATE_FIRST
    myExecutor.addClassEnhancer {
      ClassDescriptor cw, GroovyEnhancerConsumer c ->
      if (!args.className || cw.getQualifiedName() == args.className) {
        myExecutor.runEnhancer enh, new EnhancerDelegate(consumer: c)
      }
    }
  }

  /**
   * Context definition
   */
  Closure context = {Map args ->
    def ctx = new Context(args)
    myExecutor.addContext(ctx)
  }

  /**
   * Contributor definition
   */
  Closure contributor = {List cts, Closure toDo ->
    def cb = new Contributor(cts, toDo)

    // Do we need this?
    //myExecutor.addContributor(cb)

    myExecutor.addClassEnhancer {
      ClassDescriptor cw, GroovyEnhancerConsumer c ->
      myExecutor.runContributor cb, cw, new EnhancerDelegate(consumer: c)
    }

  }

  /**
   * Auxiliary methods for context definition
   */
  Closure ClosureScope = {Map args -> new ClosureScope(args)}
  Closure ScriptScope = {Map args -> new ScriptScope(args)}
  Closure Any = {Map args -> new Any()}


}

