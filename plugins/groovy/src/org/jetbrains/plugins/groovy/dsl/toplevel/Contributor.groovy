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

  Contributor(List cts, Closure cl) {
    myContexts = cts
    whatToDo = cl
  }

  def getApplyFunction(delegate) {
    def f = whatToDo.clone()
    f.delegate = delegate
    f.resolveStrategy = Closure.DELEGATE_ONLY
    final def catInstances = PsiEnhancerCategory.EP_NAME.getExtensions()
    def cats = new ArrayList();
    for (c in catInstances) {
      cats.add(c.class)
    }

    return {PsiElement elem ->
      // turn on to augment POJO interfaces in PSI with new methods and properties
      // Warning! Leads to the significant memory consumption
      //ExpandoMetaClass.enableGlobally()

      myContexts.each {Context ctx ->
        try {
          if (ctx.isApplicable(elem)) {
            use(cats) {
              f(elem)
            }
          }
        }
        catch (ProcessCanceledException e) {
          // do nothing
        }
      }

      // switch off to reduce memory usage
      //ExpandoMetaClass.disableGlobally()
    }
  }

}
