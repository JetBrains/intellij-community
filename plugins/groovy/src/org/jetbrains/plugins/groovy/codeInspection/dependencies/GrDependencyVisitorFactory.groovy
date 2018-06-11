// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.dependencies

import com.intellij.packageDependencies.DependenciesBuilder
import com.intellij.packageDependencies.DependencyVisitorFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement

@CompileStatic
class GrDependencyVisitorFactory extends DependencyVisitorFactory {
  @NotNull
  @Override
  PsiElementVisitor getVisitor(@NotNull DependenciesBuilder.DependencyProcessor processor,
                               @NotNull DependencyVisitorFactory.VisitorOptions options) {
    GroovyElementVisitor visitor = new MyVisitor(processor, options)
    new PsiElementVisitor() {
      @Override
      void visitFile(PsiFile file) {
        if (file instanceof GroovyFile) {
          file.accept(visitor)
        }
      }
    }
  }

  @CompileStatic
  private static class MyVisitor extends GroovyRecursiveElementVisitor {
    private final DependenciesBuilder.DependencyProcessor myProcessor
    private final DependencyVisitorFactory.VisitorOptions myOptions

    MyVisitor(DependenciesBuilder.DependencyProcessor processor, DependencyVisitorFactory.VisitorOptions options) {
      myOptions = options
      myProcessor = processor
    }

    @Override
    void visitElement(@NotNull GroovyPsiElement element) {
      super.visitElement(element)

      element.references.each { PsiReference ref ->
        PsiElement resolved = ref.resolve()
        if (resolved != null) {
          myProcessor.process(ref.element, resolved)
        }
      }
    }

    @Override
    void visitImportStatement(@NotNull GrImportStatement statement) {
      if (!myOptions.skipImports()) {
        visitElement(statement)
      }
    }

    @Override
    void visitDocComment(@NotNull GrDocComment comment) {}

    @Override
    void visitLiteralExpression(@NotNull GrLiteral literal) {}
  }
}
