// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HighlightVisitorInternalInspection extends AbstractBaseJavaLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance(HighlightVisitorInternalInspection.class);

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    final PsiFile file = holder.getFile();
    if (InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(file);
    if (virtualFile == null ||
        virtualFile.getFileType() != StdFileTypes.JAVA ||
        CompilerConfiguration.getInstance(holder.getProject()).isExcludedFromCompilation(virtualFile)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new HighlightVisitorImpl(JavaPsiFacade.getInstance(holder.getProject()).getResolveHelper()) {
      {
        prepareToRunAsInspection(new HighlightInfoHolder(file) {
          @Override
          public boolean add(@Nullable HighlightInfo info) {
            if (super.add(info)) {
              if (info != null && info.getSeverity() == HighlightSeverity.ERROR) {
                final int startOffset = info.getStartOffset();
                final PsiElement element = file.findElementAt(startOffset);
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
    };
  }
}
