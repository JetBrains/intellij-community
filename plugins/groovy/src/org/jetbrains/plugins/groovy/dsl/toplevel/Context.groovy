package org.jetbrains.plugins.groovy.dsl.toplevel

import static com.intellij.patterns.PlatformPatterns.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntheticElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClassScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClosureScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ScriptScope
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns
import com.intellij.openapi.util.text.StringUtil

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
      addFilter new PlaceContextFilter(psiElement().inFile(psiFile().withOriginalFile(psiFile().withVirtualFile(vfilePattern))))
    }

    // filter by scope first, then by ctype
    // scope: <scope>
    switch (args.scope) {
      case null: break

    // handling script scope
      case org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ScriptScope:
        def scope = (ScriptScope) args.scope

        //first, it should be inside groovy script
        def scriptPattern = GroovyPatterns.groovyScript()
        if (scope.extension) {
          scriptPattern = scriptPattern.withVirtualFile(PlatformPatterns.virtualFile().withExtension(scope.extension))
        }
        addFilter new PlaceContextFilter(PlatformPatterns.psiElement().inFile(scriptPattern))

        // Name matcher
        def namePattern = scope.namePattern
        if (namePattern) {
          addFilter {PsiElement elem, fqn, ctx -> elem.containingFile.name.matches(namePattern)}
        }

        // Process unqualified references only
        if (!args.ctype) {
          addFilter new ClassContextFilter(PsiJavaPatterns.psiClass().and(StandardPatterns.instanceOf(SyntheticElement)))
        }

        break
    // handling class scope
      case org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClassScope:
        final def classScope = (ClassScope) args.scope
        def namePattern = classScope.getName()
        if (namePattern) {
          addFilter {GrReferenceExpression elem, fqn, ctx ->
            final GrTypeDefinition clazz = PsiTreeUtil.getParentOfType(elem, GrTypeDefinition)
            if (clazz) {
              final def qualName = clazz.getQualifiedName()
              return clazz.getName().matches(namePattern) || qualName && qualName.matches(namePattern)
            }
            return false
          }
        }
        break

    // handling closure scope
      case org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClosureScope:
        addFilter new PlaceContextFilter(PsiJavaPatterns.psiElement().inside(GrClosableBlock))

        if (((ClosureScope) args.scope).isArg()) {
          // Filter for call parameter
          addFilter {GrReferenceExpression elem, fqn, ctx ->
            def closParent = PsiTreeUtil.getParentOfType(elem, GrClosableBlock.class)
            assert closParent != null

            def parent = closParent.getParent()
            if (parent instanceof GrArgumentList) {
              parent = parent.parent
            }
            return parent instanceof GrCall
          }
        }

        // Enhance only unqualified expressions
        if (!args.ctype) {
          addFilter getClassTypeFilter("groovy.lang.Closure")
        }
        break

      default: break
    }

    // ctype : <ctype>
    // Qualifier type to be augmented
    if (args.ctype instanceof String) {
      addFilter getClassTypeFilter(args.ctype)
    } else if (args.ctype instanceof PsiElementPattern) {
      addFilter new ClassContextFilter(args.ctype)
    }
  }

  private ContextFilter getClassTypeFilter(String ctype) {
    new ClassContextFilter(PsiJavaPatterns.psiClass().inheritorOf(false, ctype))
  }

  private def addFilter(Closure cl) {
    addFilter (cl as ContextFilter)
  }
  private void addFilter(ContextFilter cl) {
    myFilters << cl
  }

  ContextFilter getFilter() {
    return CompositeContextFilter.compose(myFilters, true)
  }

}
