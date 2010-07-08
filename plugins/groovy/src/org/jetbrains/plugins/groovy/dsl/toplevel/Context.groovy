package org.jetbrains.plugins.groovy.dsl.toplevel

import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.PsiJavaPatterns
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.Scope
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
    } else if (args.ctype instanceof PsiElementPattern) {
      addFilter new ClassContextFilter(args.ctype)
    }
  }

  static ContextFilter getClassTypeFilter(String ctype) {
    new ClassContextFilter(PsiJavaPatterns.psiClass().inheritorOf(false, ctype))
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
