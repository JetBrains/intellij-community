package org.jetbrains.plugins.groovy.dsl.toplevel

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
import org.jetbrains.plugins.groovy.dsl.GroovyDslExecutor
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.AnnotatedScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClassScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClosureScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ScriptScope
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl
import com.intellij.psi.PsiMethod
import com.intellij.util.Function

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

  Closure category = {Object[] params ->
    if (params.length == 1) {
      processCategoryMethods(params[0], new Function() {def fun(def param) {new GrGdkMethodImpl(param, false)}})
    }
    else if (params.length == 2) {
      if (params[1] instanceof Boolean) {
        processCategoryMethods(params[0], new Function() {def fun(def param) {new GrGdkMethodImpl(param, params[1])}})
      }
      else {
        processCategoryMethods(params[0], params[1])
      }
    }
    else throw new IllegalArgumentException("Incorrect aruments in method 'category': $params")
  }

  private def processCategoryMethods (def className, Function<PsiMethod, PsiMethod> converter) {
    contributor(context()) {
      if (!psiType) return;
      List methods = CategoryMethodProvider.provideMethods(psiType, project, className, resolveScope, converter)
      for (m in methods) add m
    }
  }


  /**
   * Auxiliary methods for context definition
   */
  Closure closureScope = {Map args -> return new ClosureScope(args)}
  Closure scriptScope = {Map args -> return new ScriptScope(args)}
  Closure classScope = {Map args -> return new ClassScope(args)}

   /**
    * @since 10
    */
  Closure annotatedScope = {Map args -> return new AnnotatedScope(args)}

  Closure hasAnnotation = { String annoQName -> TypeToClassPattern.create(PsiJavaPatterns.psiModifierListOwner().withAnnotation(annoQName)) }
  Closure hasField = { ElementPattern fieldCondition -> TypeToClassPattern.create(PsiJavaPatterns.psiClass().withField(true, PsiJavaPatterns.psiField().and(fieldCondition))) }
  Closure hasMethod = { ElementPattern methodCondition -> TypeToClassPattern.create(PsiJavaPatterns.psiClass().withMethod(true, PsiJavaPatterns.psiMethod().and(methodCondition))) }


}

