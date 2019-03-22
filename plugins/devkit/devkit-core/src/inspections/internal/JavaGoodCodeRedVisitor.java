// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiResolveHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class JavaGoodCodeRedVisitor implements GoodCodeRedVisitor {
  @NotNull
  @Override
  public PsiElementVisitor createVisitor(ProblemsHolder holder) {
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(holder.getProject()).getResolveHelper();
    return new MyHighlightVisitorImpl(holder, resolveHelper);
  }

  private static final class MyHighlightVisitorImpl extends HighlightVisitorImpl {
    private final PsiResolveHelper myResolveHelper;

    private MyHighlightVisitorImpl(ProblemsHolder holder, PsiResolveHelper resolveHelper) {
      myResolveHelper = resolveHelper;
      prepareToRunAsInspection(new HighlightInfoHolder(holder.getFile()) {
        @Override
        public boolean add(@Nullable HighlightInfo info) {
          if (super.add(info)) {
            if (info != null && info.getSeverity() == HighlightSeverity.ERROR) {
              final int startOffset = info.getStartOffset();
              final PsiElement element = holder.getFile().findElementAt(startOffset);
              if (element != null) {
                holder.registerProblem(element, info.getDescription());
              }
            }
            return true;
          }
          return false;
        }

        @Override
        public boolean hasErrorResults() {
          //accept multiple errors per file
          return false;
        }
      });
    }

    @NotNull
    @Override
    protected PsiResolveHelper getResolveHelper(@NotNull Project project) {
      return myResolveHelper;
    }
  }
}
