package org.jetbrains.plugins.groovy.dsl.toplevel

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.dsl.augmenters.PsiClassCategory

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
      // turn on to augment POJO interfaces in PSI with new methods and properties
      // Warning! Leads to the significant memory consumption
      //ExpandoMetaClass.enableGlobally()

      myContexts.each {Context ctx ->
        if (ctx.isApplicable(elem)) {
          // todo refactor to add dynamically other categories!
          use(PsiClassCategory) {
            f(elem)
          }
        }
      }

      // switch off to reduce memory usage
      //ExpandoMetaClass.disableGlobally()
    }
  }

}
