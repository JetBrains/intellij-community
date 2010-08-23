package org.jetbrains.plugins.groovy.dsl.toplevel

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.ObjectPattern
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiType
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.Scope
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import static com.intellij.patterns.PlatformPatterns.psiFile
import static com.intellij.patterns.PlatformPatterns.virtualFile

/**
 * @author ilyas
 */
class Context {
  private List<ContextFilter> myFilters = []

  public Context(Map args) {
    // Named parameter processing
    if (!args) return

    // filetypes : [<file_ext>*]
    List<String> extensions = args.filetypes
    if (extensions instanceof List) {
      extensions = extensions.collect { StringUtil.trimStart(it, '.') }
      def vfilePattern = extensions.size() == 1 ? virtualFile().withExtension(extensions[0]) : virtualFile().withExtension(extensions as String[])
      addFilter new FileContextFilter(psiFile().withVirtualFile(vfilePattern))
    }

    // filter by scope first, then by ctype
    // scope: <scope>
    if (args.scope) {
      myFilters += ((Scope) args.scope).createFilters(args)
    }

    // ctype : <ctype>
    // Qualifier type to be augmented
    if (args.ctype instanceof String) {
      addFilter getClassTypeFilter(args.ctype)
    }
    else if (args.ctype instanceof ElementPattern) {
      addFilter new ClassContextFilter(args.ctype)
    }
  }

  static ContextFilter getClassTypeFilter(final String ctype) {
    return new ClassContextFilter(new ObjectPattern<Pair<PsiType, PsiElement>, ObjectPattern>(PsiType) {
       @Override
       boolean accepts(Object o, ProcessingContext context) {
         if (o instanceof Pair && o.second instanceof PsiFile && o.first instanceof PsiType) {
           def place = (PsiFile) o.second
           PsiType myType = JavaPsiFacade.getElementFactory(place.project).createTypeFromText(ctype, place)
           return TypesUtil.isAssignable(myType, (PsiType)o.first, place.manager, place.resolveScope, false)
         }
         return false
       }
    })
  }

  private void addFilter(ContextFilter cl) {
    myFilters << cl
  }

  ContextFilter getFilter() {
    if (myFilters.size() == 1) {
      return myFilters[0]
    }

    return CompositeContextFilter.compose(myFilters, true)
  }

}
