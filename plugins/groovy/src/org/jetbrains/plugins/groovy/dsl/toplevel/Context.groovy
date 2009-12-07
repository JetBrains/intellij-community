package org.jetbrains.plugins.groovy.dsl.toplevel

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.*
import com.intellij.util.containers.HashSet
import org.jetbrains.plugins.groovy.GroovyFileType
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

  private List<Closure> myFilters = []

  private final Set<Pair<String, String>> ASSIGNABLE_TYPES = new ConcurrentHashSet<Pair<String, String>>();
  private final Set<Pair<String, String>> NON_ASSIGNABLE_TYPES = new ConcurrentHashSet<Pair<String, String>>();

  public Context(Map args) {
    // Basic filter, all contexts are applicable for reference expressions only
    myFilters << {PsiElement elem, fqn -> elem instanceof GrReferenceExpression}
    myFilters << {PsiElement element, fqn -> PsiTreeUtil.getParentOfType(element, PsiAnnotation) == null}
    init(args)
  }

  Closure getClassTypeFilter(ctype) {
    return {GrReferenceExpression ref, String fqn ->
      if (!(ctype instanceof String)) return false
      final def pair = new Pair(((String) ctype), fqn)

      if (NON_ASSIGNABLE_TYPES.contains(pair)) return false
      if (ASSIGNABLE_TYPES.contains(pair)) return true

      if (ctype.equals(fqn)) {
        ASSIGNABLE_TYPES.add(pair)
        return true
      }

      PsiManager manager = PsiManager.getInstance(ref.getProject())
      def scope = GlobalSearchScope.allScope(ref.getProject())
      PsiType superType = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().
              createTypeByFQClassName(((String) ctype), scope)
      if (!superType) return false
      def type = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeByFQClassName(fqn, scope)

      if (!type) return false
      if (!type.isAssignableFrom(superType) || !superType?.isAssignableFrom(type)) {
        NON_ASSIGNABLE_TYPES.add(pair)
        return false
      }
      else {
        ASSIGNABLE_TYPES.add(pair)
        return true
      }
    }
  }

  /**
   * Initialize context filters
   */
  private def init(Map args) {
    // Named parameter processing
    if (!args) return

    // ctype : <ctype>
    // Qualifier type to be augmented
    if (args.ctype) {
      addFilter getClassTypeFilter(args.ctype)
    }

    // filetypes : [<file_ext>*]
    if (args.filetypes && args.filetypes instanceof List) {
      addFilter {PsiElement elem, fqn ->
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
    else {
      addFilter {PsiElement elem, fqn -> elem?.getContainingFile()?.getFileType() instanceof GroovyFileType}
    }

    // scope: <scope>
    switch (args.scope) {
      case null: break

    // handling script scope
      case org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ScriptScope:
        def scope = (ScriptScope) args.scope
        addFilter {PsiElement elem, fqn ->
          def parent = PsiTreeUtil.getParentOfType(elem, GrTypeDefinition.class, GroovyFile.class)
          if (parent instanceof GroovyFile && ((GroovyFile) parent).isScript()) {
            return ((GroovyFile) parent).getName().matches(scope.getName())
          }
          return false
        }
        // Name matcher
        addFilter {PsiElement elem, fqn -> elem.getContainingFile().getName().matches(scope.getName())}
        // Process unqualified references only
        if (!args.ctype) {
          addFilter getClassTypeFilter(GroovyFileBase.SCRIPT_BASE_CLASS_NAME)
        }

        break
    // handling class scope
      case org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClassScope:
        addFilter {GrReferenceExpression elem, fqn ->
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
        addFilter {GrReferenceExpression elem, fqn ->
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
  boolean isApplicable(PsiElement place, String fqn) {
    for (f in myFilters) {
      try {
        if (!f(place, fqn)) return false
      }
      catch (ProcessCanceledException e) {
        return false
      }
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
