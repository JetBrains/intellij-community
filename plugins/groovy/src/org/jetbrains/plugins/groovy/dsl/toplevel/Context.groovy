package org.jetbrains.plugins.groovy.dsl.toplevel

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClosureScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ScriptScope
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.GroovyFileType

/**
 * @author ilyas
 */
class Context {

  private List<Closure> myFilters = []

  public Context(Map args) {
    // Basic filter, all contexts are applicable for reference expressions only
    myFilters << {PsiElement elem ->
      elem instanceof GrReferenceExpression
    }
    init(args)
  }

  /**
   * Initialize context filters
   */
  private def init(Map args) {
    // Named parameter processing
    if (!args) return

    // ctype : <ctype>
    // Qualifier type to be augmented
    if (args.ctype) addFilter {GrReferenceExpression ref ->
      PsiManager manager = PsiManager.getInstance(ref.getProject())
      def scope = GlobalSearchScope.allScope(ref.getProject())
      PsiType superType = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeByFQClassName(args.ctype, scope)
      def qual = ref.getQualifier()
      if (qual instanceof GrExpression) {
        final expr = (GrExpression) qual;
        def type = expr.getType()
        return type && superType?.isAssignableFrom(type)
      } else false
    }

    // filetypes : [<file_ext>*]
    if (args.filetypes && args.filetypes instanceof List) {
      addFilter {PsiElement elem ->
        def file = elem.getContainingFile()
        if (!file) return false
        def vFile = file.getVirtualFile()
        if (!vFile) return false
        def ext = vFile.getExtension()
        for (ft in args.filetypes) {
          if (ft && StringUtil.trimStart(ft, ".").equals(ext)) return true
        }
        return false
      }
    } else {
      addFilter {PsiElement elem -> elem?.getContainingFile()?.getFileType() instanceof GroovyFileType}
    }

    // scope: <scope>
    switch (args.scope) {
      case null: break

    // handling script scope
      case org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ScriptScope:
        def scope = (ScriptScope) args.scope
        addFilter {PsiElement elem ->
          def parent = PsiTreeUtil.getParentOfType(elem, GrTypeDefinition.class, GroovyFile.class)
          if (parent instanceof GroovyFile && ((GroovyFile) parent).isScript()) {
            return ((GroovyFile) parent).getName().matches(scope.getName())
          }
          return false
        }
        break

    // handling closure scope
      case org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClosureScope:
        // Enhance only unqualified expressions
        if (!args.ctype) {
          addFilter {GrReferenceExpression elem -> !elem.getQualifierExpression()}
        }

        // Enhance closure contexts only
        addFilter {GrReferenceExpression elem ->
          def closParent = PsiTreeUtil.getParentOfType(elem, GrClosableBlock.class)
          if (closParent == null) return false
          def scope = (ClosureScope) args.scope
          if (scope.isArg()) {
            def parent = closParent.getParent()
            if (parent instanceof GrArgumentList) {
              return parent.getParent() instanceof GrMethodCallExpression
            }
            else {
              return parent instanceof GrMethodCallExpression
            }
          }
          return true
        }
        break

      default: break
    }

    // Other conditions for context
    if (args.cond) {
      // Todo implement with method interception
      //cond.setDelegate(this)
      //ctx.addFilter {elem -> }
    }
  }

  private def addFilter(Closure cl) {
    myFilters << cl
  }

  /**
   * Check all available filters
   */
  boolean isApplicable(PsiElement place) {
    for (f in myFilters) {
      if (!f(place)) return false
    }
    return true
  }

  /**
   * Returns a map, representing given environment
   */
  def getEnv() {
    []
  }
}