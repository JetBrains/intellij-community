// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInspection.ProblemsHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

/**
 * @author ven
 */
public abstract class GroovyLocalInspectionBase extends GroovyLocalInspectionTool {

  @Override
  public final @NotNull GroovyElementVisitor buildGroovyVisitor(@NotNull ProblemsHolder problemsHolder, boolean isOnTheFly) {
    return new GroovyElementVisitor() {
      @Override
      public void visitClosure(@NotNull GrClosableBlock closure) {
        check(closure, problemsHolder);
      }

      @Override
      public void visitLambdaBody(@NotNull GrLambdaBody body) {
        check(body, problemsHolder);
      }

      @Override
      public void visitMethod(@NotNull GrMethod method) {
        final GrOpenBlock block = method.getBlock();
        if (block != null) {
          check(block, problemsHolder);
        }
      }

      @Override
      public void visitFile(@NotNull GroovyFileBase file) {
        check(file, problemsHolder);
      }

      @Override
      public void visitClassInitializer(@NotNull GrClassInitializer initializer) {
        check(initializer.getBlock(), problemsHolder);
      }
    };
  }

  protected abstract void check(@NotNull GrControlFlowOwner owner, @NotNull ProblemsHolder problemsHolder);
}
