package org.jetbrains.plugins.groovy.dsl.toplevel

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
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
  Closure context = {Map args -> return new Context(args) }

  /**
   * Contributor definition
   */
  Closure contributor = {cts, Closure toDo ->
    if (cts instanceof Map) {
      cts = new Context(cts)
    }
    if (!(cts instanceof List)) {
      assert cts instanceof Context: "The contributor() argument must be a context"
      cts = [cts]
    }
    def contexts = cts.findAll { it != null } as List
    if (contexts) {
      def filters = contexts.collect { return it.filter }
      myExecutor.addClassEnhancer(filters, toDo)
    }
  }

  /**
   * Auxiliary methods for context definition
   */
  Closure ClosureScope = {Map args -> return new ClosureScope(args)}
  Closure ScriptScope = {Map args -> return new ScriptScope(args)}
  Closure ClassScope = {Map args -> return new ClassScope(args)}

  Closure hasAnnotation = { String annoQName -> PsiJavaPatterns.psiModifierListOwner().withAnnotation(annoQName) }
  Closure hasField = { ElementPattern fieldCondition -> PsiJavaPatterns.psiClass().withField(true, PsiJavaPatterns.psiField().and(fieldCondition)) }
  Closure hasMethod = { ElementPattern methodCondition -> PsiJavaPatterns.psiClass().withMethod(true, PsiJavaPatterns.psiMethod().and(methodCondition)) }


}

