// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefJavaManager;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * A global inspection for Java, Kotlin and Groovy packages without needing the reference graph (which can take a lot of time to build).
 * This does mean that references (e.g. children) for the inspected packages are not available.
 * A regular BaseGlobalInspection or GlobalJavaBatchInspectionTool only checks packages when {@code isGraphNeeded()} returns {@code true}.
 *
 * @author Bas Leijdekkers
 */
public abstract class PackageGlobalInspection extends BaseGlobalInspection {

  private final JobDescriptor myJobDescriptor = new JobDescriptor("");

  @Override
  public boolean isGraphNeeded() {
    return false;
  }

  @Override
  public final void runInspection(@NotNull AnalysisScope scope,
                                  @NotNull InspectionManager manager,
                                  @NotNull GlobalInspectionContext globalContext,
                                  @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    if (((RefManagerImpl)globalContext.getRefManager()).isDeclarationsFound()) {
      // if reference graph is available, use it.
      super.runInspection(scope, manager, globalContext, problemDescriptionsProcessor);
    }
    else {
      final AnalysisScope analysisScope = globalContext.getRefManager().getScope();
      assert analysisScope != null;
      myJobDescriptor.setTotalAmount(analysisScope.getFileCount());
      final Set<String> packages = new HashSet<>();
      final IntRef current = new IntRef();
      analysisScope.accept(file -> {
        current.inc();
        if (file.isDirectory() || !scope.contains(file)) return true;
        String packageName = ReadAction.nonBlocking(() -> {
          final PsiFile element = PsiManager.getInstance(scope.getProject()).findFile(file);
          if (!(element instanceof PsiClassOwner)) return null;
          final PsiClassOwner classOwner = (PsiClassOwner)element;
          return classOwner.getPackageName();
        }).executeSynchronously();
        if (packageName == null) {
          return true;
        }
        do {
          if (!packages.add(packageName)) {
            return true;
          }
          myJobDescriptor.setDoneAmount(current.get());
          globalContext.incrementJobDoneAmount(myJobDescriptor,
                                               InspectionGadgetsBundle.message("progress.text.analyzing.package.0", packageName));
          final RefPackage aPackage = (RefPackage)globalContext.getRefManager().getReference(RefJavaManager.PACKAGE, packageName);
          if (aPackage == null) return true;
          CommonProblemDescriptor[] descriptors = checkPackage(aPackage, scope, manager, globalContext);
          if (descriptors != null) {
            problemDescriptionsProcessor.addProblemElement(aPackage, descriptors);
          }
          packageName = StringUtil.getPackageName(packageName);
        }
        while (!packageName.isEmpty()); // the default package is not visited
        return true;
      });
    }
  }

  @Override
  public JobDescriptor @Nullable [] getAdditionalJobs(@NotNull GlobalInspectionContext context) {
    return new JobDescriptor[] { myJobDescriptor };
  }

  @Override
  public final CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                                 @NotNull AnalysisScope scope,
                                                                 @NotNull InspectionManager manager,
                                                                 @NotNull GlobalInspectionContext globalContext) {
    throw new AssertionError();
  }

  @Override
  public final CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                                 @NotNull AnalysisScope scope,
                                                                 @NotNull InspectionManager manager,
                                                                 @NotNull GlobalInspectionContext globalContext,
                                                                 @NotNull ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefPackage) {
      return checkPackage((RefPackage)refEntity, scope, manager, globalContext);
    }
    return null;
  }

  public abstract CommonProblemDescriptor @Nullable [] checkPackage(@NotNull RefPackage refPackage,
                                                                    @NotNull AnalysisScope analysisScope,
                                                                    @NotNull InspectionManager inspectionManager,
                                                                    @NotNull GlobalInspectionContext globalInspectionContext);
}
