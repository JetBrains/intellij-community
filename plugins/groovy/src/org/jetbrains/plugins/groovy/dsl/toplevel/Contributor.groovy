package org.jetbrains.plugins.groovy.dsl.toplevel

import com.intellij.psi.PsiElement

/**
 * @author ilyas
 */
class Contributor {
  private final List myContexts
  private final Closure whatToDo

  Contributor(List cts, Closure cl) {
    myContexts = cts
    whatToDo = cl
  }

  def getApplyFunction(delegate) {
    def f = whatToDo.clone()
    f.delegate = delegate
    f.resolveStrategy = Closure.DELEGATE_ONLY
    return {PsiElement elem ->
      myContexts.each {Context ctx ->
        if (ctx.isApplicable(elem)) f(elem)
      }
    }
  }

}
