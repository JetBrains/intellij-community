/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
    void visitElement(GroovyPsiElement element) {
      super.visitElement(element)

      element.references.each { PsiReference ref ->
        PsiElement resolved = ref.resolve();
        if (resolved != null) {
          myProcessor.process(ref.element, resolved);
        }
      }
    }

    @Override
    void visitImportStatement(GrImportStatement statement) {
      if (!myOptions.skipImports()) {
        visitElement(statement);
      }
    }

    @Override
    void visitDocComment(GrDocComment comment) {}

    @Override
    void visitLiteralExpression(GrLiteral literal) {}
  }
}
