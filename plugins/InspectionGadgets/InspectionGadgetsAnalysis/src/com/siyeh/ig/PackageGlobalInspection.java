// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.reference.RefJavaManager;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * A global inspection for Java packages without needing the reference graph (which can take a lot of time to build). For a regular
 * BaseGlobalInspection or GlobalJavaBatchInspectionTool packages are only checked when {@code isGraphNeeded()} returns {@code true}.
 * This does mean that references (e.g. children) for the checked packages are not available.
 *
 * @author Bas Leijdekkers
 */
public abstract class PackageGlobalInspection extends BaseGlobalInspection {
  @Override
  public boolean isGraphNeeded() {
    return false;
  }

  @Override
  public void runInspection(@NotNull AnalysisScope scope,
                            @NotNull InspectionManager manager,
                            @NotNull GlobalInspectionContext globalContext,
                            @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    final Set<String> packages = new HashSet<>();
    scope.accept(file -> {
      if (file.isDirectory()) return true;
      final String packageName = ReadAction.compute(() -> {
        final PsiFile element = PsiManager.getInstance(scope.getProject()).findFile(file);
        if (!(element instanceof PsiClassOwner)) return null;
        final PsiClassOwner classOwner = (PsiClassOwner)element;
        return classOwner.getPackageName();
      });
      if (packageName == null || !packages.add(packageName)) return true;
      final RefPackage aPackage = (RefPackage)globalContext.getRefManager().getReference(RefJavaManager.PACKAGE, packageName);
      if (aPackage == null) return true;
      CommonProblemDescriptor[] descriptors = checkPackage(aPackage, scope, manager, globalContext);
      if (descriptors != null) {
        problemDescriptionsProcessor.addProblemElement(aPackage, descriptors);
      }
      return true;
    });
  }

  public abstract CommonProblemDescriptor @Nullable [] checkPackage(@NotNull RefPackage refPackage,
                                                                    @NotNull AnalysisScope analysisScope,
                                                                    @NotNull InspectionManager inspectionManager,
                                                                    @NotNull GlobalInspectionContext globalInspectionContext);
}
