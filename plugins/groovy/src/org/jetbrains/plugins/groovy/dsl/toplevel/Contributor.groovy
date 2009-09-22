package org.jetbrains.plugins.groovy.dsl.toplevel

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.dsl.psi.PsiEnhancerCategory

/**
 * @author ilyas
 */
class Contributor {
  private final List myContexts
  private final Closure whatToDo

  Contributor(cts, Closure cl) {
    if (cts instanceof List) {
      myContexts = cts
    } else {
      myContexts = [cts]
    }
    whatToDo = cl
  }

  def getApplyFunction(delegate, PsiElement place, String fqn) {
    def f = whatToDo.clone()

    f.delegate = delegate
    f.resolveStrategy = Closure.DELEGATE_FIRST

    // Add categories to augment PSI
    final def catInstances = PsiEnhancerCategory.EP_NAME.getExtensions()
    def cats = new ArrayList();
    for (c in catInstances) {
      cats.add(c.class)
    }

    return {
      myContexts.each {Context ctx ->
        try {
          if (ctx.isApplicable(place, fqn)) {
            use(cats) {
              f()
            }
          }
        }
        catch (ProcessCanceledException e) {
          // do nothing
        }
      }
    }
  }

}
