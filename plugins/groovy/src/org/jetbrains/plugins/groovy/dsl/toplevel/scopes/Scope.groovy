package org.jetbrains.plugins.groovy.dsl.toplevel.scopes

import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.SyntheticElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns
import org.jetbrains.plugins.groovy.dsl.toplevel.*

/**
 * @author ilyas
 */
abstract class Scope {
  abstract List<ContextFilter> createFilters(Map args)
}

class ClassScope extends Scope {
  private final String namePattern

  ClassScope(Map args) {
    namePattern = args && args.name ? args.name : /.*/
  }

  List<ContextFilter> createFilters(Map args) {
    if (namePattern) {
      def match = PlatformPatterns.string().matches(namePattern)
      return [new PlaceContextFilter(PsiJavaPatterns.psiElement().inside(
              PlatformPatterns.or(
                      PsiJavaPatterns.psiClass().withQualifiedName(match),
                      PsiJavaPatterns.psiClass().withName(match))))]
    }
    return []
  }


}

class ClosureScope extends Scope {
  private final boolean isArg
  private final boolean isTransparent

  ClosureScope(Map args) {
    isArg == args && args.isArgument ? args.isArgument : false
    isTransparent = args && args.transparent ? args.transparent : false
  }

  def isArg() {
    isArg
  }

  def transparent() {
    isTransparent
  }

  List<ContextFilter> createFilters(Map args) {
    def result = []
    result << new PlaceContextFilter(PsiJavaPatterns.psiElement().inside(GrClosableBlock))

    if (((ClosureScope) args.scope).isArg()) {
      // Filter for call parameter
      result << new PlaceContextFilter(PsiJavaPatterns.psiElement().inside(
              PsiJavaPatterns.psiElement(GrClosableBlock).withParent(
                      PlatformPatterns.or(
                              PsiJavaPatterns.psiElement(GrCall),
                              PsiJavaPatterns.psiElement(GrArgumentList).withParent(GrCall)))))

    }

    // Enhance only unqualified expressions
    if (!args.ctype) {
      result << ClassContextFilter.subtypeOf(GrClosableBlock.GROOVY_LANG_CLOSURE)
    }
    result
  }


}

class ScriptScope extends Scope {
  final String namePattern
  final String extension

  ScriptScope(Map args) {
    if (args) {
      if (args.name) {
        namePattern = args.name
      } else if (args.extension) {
        extension = args.extension
      }
    }
  }

  List<ContextFilter> createFilters(Map args) {
    def result = []
    def scriptPattern = GroovyPatterns.groovyScript()
    if (extension) {
      scriptPattern = scriptPattern.withVirtualFile(PlatformPatterns.virtualFile().withExtension(extension))
    }
    result << new FileContextFilter(scriptPattern)

    // Name matcher
    if (namePattern) {
      result << new FileContextFilter(PlatformPatterns.psiFile().withName(PlatformPatterns.string().matches(namePattern)))
    }

    // Process unqualified references only
    if (!args.ctype) {
      result << ClassContextFilter.fromClassPattern(PsiJavaPatterns.psiClass().and(StandardPatterns.instanceOf(SyntheticElement)))
    }

    return result
  }

}

class AnnotatedScope extends Scope {
  final String annoQName

  def AnnotatedScope(Map args) {
    if (args && args.ctype) {
      annoQName = args.ctype
    }
  }

  List<ContextFilter> createFilters(Map args) {
    annoQName ? [new AnnotatedContextFilter(annoQName)] : []
  }


}