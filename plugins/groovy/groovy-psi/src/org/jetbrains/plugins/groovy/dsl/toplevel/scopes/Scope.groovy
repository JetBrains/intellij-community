/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.dsl.toplevel.scopes

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiClassPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.psi.SyntheticElement
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.dsl.toplevel.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

import static com.intellij.patterns.PlatformPatterns.*
import static com.intellij.patterns.StandardPatterns.*
import static org.jetbrains.plugins.groovy.lang.psi.patterns.GrAnnotationPattern.annotation
import static org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns.groovyScript
import static org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns.methodCall
/**
 * @author ilyas
 */
@CompileStatic
abstract class Scope {
  abstract List<ContextFilter> createFilters(Map args)
}

@CompileStatic
class ClassScope extends Scope {
  private final String namePattern

  ClassScope(Map args) {
    namePattern = args && args.name ? args.name : /.*/
  }

  List<ContextFilter> createFilters(Map args) {
    def result = []
    if (namePattern) {
      def match = string().matches(namePattern)
      result << new PlaceContextFilter(psiElement().inside(or(
        PsiJavaPatterns.psiClass().withQualifiedName(match),
        PsiJavaPatterns.psiClass().withName(match))
      ))
    }
    return result
  }
}

@CompileStatic
class ClosureScope extends Scope {
  final boolean arg
  final String methodName
  final String annotationName

  ClosureScope(Map args) {
    arg = args?.isArgument
    methodName = args?.methodName
    annotationName = args?.annotationName
  }

  List<ContextFilter> createFilters(Map args) {
    def result = []
    result << new PlaceContextFilter(psiElement().inside(GrClosableBlock))

    final scope = (ClosureScope)args.scope

    if (scope.isArg()) {
      result << new PlaceContextFilter(
        psiElement().inside(
          psiElement(GrClosableBlock).withParent(
            or(
              psiElement(GrCall),
              psiElement(GrArgumentList).withParent(GrCall)))))
    }

    if (scope.methodName != null) {
      result << new PlaceContextFilter(psiElement().inside(
        psiElement(GrClosableBlock).withParent(
          or(
            methodCall().withMethodName(scope.methodName),
            psiElement(GrArgumentList).withParent(
              methodCall().withMethodName(scope.methodName))))))

    }

    if (scope.annotationName != null) {
      result << new PlaceContextFilter(psiElement().inside(
        psiElement(GrClosableBlock).inside(
          annotation().withQualifiedName(scope.annotationName))))
    }

    // Enhance only unqualified expressions
    if (!args.ctype) {
      result << ClassContextFilter.subtypeOf(GroovyCommonClassNames.GROOVY_LANG_CLOSURE)
    }
    result
  }


}

@CompileStatic
class ScriptScope extends Scope {
  final String namePattern
  final String extension

  ScriptScope(Map args) {
    namePattern = args?.name
    extension = args?.extension
  }

  List<ContextFilter> createFilters(Map args) {
    def result = []
    def scriptPattern = groovyScript()
    if (extension) {
      scriptPattern = scriptPattern.withVirtualFile(virtualFile().withExtension(extension))
    }
    result << new FileContextFilter(scriptPattern)

    // Name matcher
    if (namePattern) {
      result << new FileContextFilter(psiFile().withName(string().matches(namePattern)))
    }

    // Process unqualified references only
    if (!args.ctype) {
      ElementPattern synt = instanceOf(SyntheticElement)

      PsiClassPattern psiClass = PsiJavaPatterns.psiClass()
      ElementPattern pattern = doAnd(psiClass, synt)
      result << ClassContextFilter.fromClassPattern(pattern)
    }

    return result
  }

  private PsiClassPattern doAnd(PsiClassPattern psiClass, ElementPattern synt) {
    psiClass.and(synt)
  }
}

@CompileStatic
class AnnotatedScope extends Scope {
  final String annoQName

  AnnotatedScope(Map args) {
    annoQName = args?.ctype
  }

  List<ContextFilter> createFilters(Map args) {
    def result = []
    if (annoQName) result << new AnnotatedContextFilter(annoQName)
    result
  }
}
