package org.jetbrains.plugins.groovy.dsl.toplevel

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.PsiJavaPatterns

import com.intellij.psi.PsiElement

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.ConcurrentHashSet
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClassScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClosureScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ScriptScope
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

/**
 * @author ilyas
 */
class Context {

  private List<ContextFilter> myFilters = []

  public Context(Map args) {
    // Named parameter processing
    if (!args) return

    // ctype : <ctype>
    // Qualifier type to be augmented
    if (args.ctype instanceof String) {
      addFilter getClassTypeFilter(args.ctype)
    } else if (args.ctype instanceof PsiElementPattern) {
      addFilter new ClassContextFilter(args.ctype)
    }

    // filetypes : [<file_ext>*]
    if (args.filetypes && args.filetypes instanceof List) {
      addFilter {PsiElement elem, fqn, ctx ->
        def file = elem.getContainingFile()
        if (!file) return false
        final def name = file.getName()
        final def idx = name.lastIndexOf(".")
        if (idx < 0) return false;
        def ext = name.substring(idx + 1)
        for (ft in args.filetypes) {
          if (ft && StringUtil.trimStart(ft, ".").equals(ext)) return true
        }
        return false
      }
    }

    // scope: <scope>
    switch (args.scope) {
      case null: break

    // handling script scope
      case org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ScriptScope:
        def scope = (ScriptScope) args.scope
        addFilter {PsiElement elem, fqn, ctx ->
          def parent = PsiTreeUtil.getParentOfType(elem, GrTypeDefinition.class, GroovyFile.class)
          if (parent instanceof GroovyFile && ((GroovyFile) parent).isScript()) {
            return ((GroovyFile) parent).getName().matches(scope.namePattern)
          }
          return false
        }
        // Name matcher
        addFilter {PsiElement elem, fqn, ctx -> elem.containingFile.name.matches(scope.namePattern)}
        // Process unqualified references only
        if (!args.ctype) {
          addFilter getClassTypeFilter(GroovyFileBase.SCRIPT_BASE_CLASS_NAME)
        }

        break
    // handling class scope
      case org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClassScope:
        addFilter {GrReferenceExpression elem, fqn, ctx ->
          final def classScope = (ClassScope) args.scope
          if (!classScope.getName()) return false;
          final GrTypeDefinition clazz = PsiTreeUtil.getParentOfType(elem, GrTypeDefinition)
          if (clazz) {
            final def qualName = clazz.getQualifiedName()
            return clazz.getName().matches(classScope.getName()) ||
                   qualName && qualName.matches(classScope.getName())
          }
          return false
        }
        if (!args.ctype) {
          addFilter getClassTypeFilter("java.lang.Object")
        }
        break

    // handling closure scope
      case org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClosureScope:
        // Enhance only unqualified expressions
        if (!args.ctype) {
          addFilter getClassTypeFilter("groovy.lang.Closure")
        }

        // Enhance closure contexts only
        addFilter {GrReferenceExpression elem, fqn, ctx ->
          def closParent = PsiTreeUtil.getParentOfType(elem, GrClosableBlock.class)
          if (closParent == null) return false
          def scope = (ClosureScope) args.scope
          if (scope.isArg()) {
            def parent = closParent.getParent()
            if (parent instanceof GrArgumentList) {
              return parent.getParent() instanceof GrMethodCallExpression
            } else {
              return parent instanceof GrMethodCallExpression
            }
          }
          return true
        }
        break

      default: break
    }
  }

  private ContextFilter getClassTypeFilter(ctype) {
    new ClassContextFilter(PsiJavaPatterns.psiClass().withQualifiedName(ctype))
  }

  private def addFilter(Closure cl) {
    addFilter (cl as ContextFilter)
  }
  private def addFilter(ContextFilter cl) {
    myFilters << cl
  }

  ContextFilter getFilter() {
    return CompositeContextFilter.compose(myFilters, true)
  }

}
