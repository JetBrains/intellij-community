// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.dependencies;

import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.DependencyVisitorFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

final class GrDependencyVisitorFactory extends DependencyVisitorFactory {
  @Override
  public @NotNull PsiElementVisitor getVisitor(@NotNull DependenciesBuilder.DependencyProcessor processor,
                                               @NotNull DependencyVisitorFactory.VisitorOptions options) {
    GroovyElementVisitor visitor = new MyVisitor(processor, options);
    return new PsiElementVisitor() {
      @Override
      public void visitFile(@NotNull PsiFile psiFile) {
        if (psiFile instanceof GroovyFile) {
          ((GroovyFile)psiFile).accept(visitor);
        }
      }
    };
  }

  private static class MyVisitor extends GroovyRecursiveElementVisitor {
    private final DependenciesBuilder.DependencyProcessor myProcessor;
    private final DependencyVisitorFactory.VisitorOptions myOptions;

    MyVisitor(DependenciesBuilder.DependencyProcessor processor, DependencyVisitorFactory.VisitorOptions options) {
      myOptions = options;
      myProcessor = processor;
    }

    @Override
    public void visitElement(@NotNull GroovyPsiElement element) {
      super.visitElement(element);

      for (PsiReference ref : element.getReferences()) {
        PsiElement resolved = ref.resolve();
        if (resolved != null) {
          myProcessor.process(ref.getElement(), resolved);
        }
      }
    }

    @Override
    public void visitImportStatement(@NotNull GrImportStatement statement) {
      if (!myOptions.skipImports()) {
        visitElement(statement);
      }
    }

    @Override
    public void visitDocComment(@NotNull GrDocComment comment) {}

    @Override
    public void visitLiteralExpression(@NotNull GrLiteral literal) {}
  }
}
