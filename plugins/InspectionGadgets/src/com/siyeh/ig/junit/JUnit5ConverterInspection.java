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
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionEngine;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.actions.CleanupInspectionIntention;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringManager;
import com.intellij.refactoring.migration.MigrationManager;
import com.intellij.refactoring.migration.MigrationMap;
import com.intellij.refactoring.migration.MigrationProcessor;
import com.intellij.testIntegration.TestFramework;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JUnit5ConverterInspection extends BaseInspection {
  private static final List<String> ruleAnnotations = Arrays.asList(JUnitCommonClassNames.ORG_JUNIT_RULE, JUnitCommonClassNames.ORG_JUNIT_CLASS_RULE);

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("junit5.converter.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return "#ref can be JUnit 5 test";
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    if (!PsiUtil.isLanguageLevel8OrHigher(file)) return false;
    return super.shouldInspect(file);
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new MigrateToJUnit5();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {

      @Override
      public void visitClass(PsiClass aClass) {
        TestFramework framework = TestFrameworks.detectFramework(aClass);
        if (framework == null || !"JUnit4".equals(framework.getName())) {
          return;
        }

        if (AnnotationUtil.isAnnotated(aClass, TestUtils.RUN_WITH, true)) {
          return;
        }

        for (PsiField field : aClass.getAllFields()) {
          if (AnnotationUtil.isAnnotated(field, ruleAnnotations)) {
            return;
          }
        }

        for (PsiMethod method : aClass.getMethods()) {
          if (AnnotationUtil.isAnnotated(method, ruleAnnotations)) {
            return;
          }
        }

        registerClassError(aClass);
      }
    };
  }

  private static class MigrateToJUnit5 extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("junit5.converter.fix.name");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiClass psiClass = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class);
      if (psiClass != null) {
        MigrationManager manager = RefactoringManager.getInstance(project).getMigrateManager();
        MigrationMap migrationMap = manager.findMigrationMap("JUnit (4.x -> 5.0)");
        if (migrationMap != null) {
          new MyJUnit5MigrationProcessor(project, migrationMap, psiClass.getContainingFile()).run();
        }
      }
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    private static class MyJUnit5MigrationProcessor extends MigrationProcessor {

      private final Project myProject;
      private final PsiFile myFile;

      public MyJUnit5MigrationProcessor(Project project, MigrationMap migrationMap, PsiFile file) {
        super(project, migrationMap, GlobalSearchScope.fileScope(file));
        myProject = project;
        myFile = file;
      }

      @NotNull
      @Override
      protected UsageInfo[] findUsages() {
        UsageInfo[] usages = super.findUsages();
        InspectionManager inspectionManager = InspectionManager.getInstance(myProject);
        GlobalInspectionContext globalContext = inspectionManager.createNewGlobalContext(false);
        LocalInspectionToolWrapper assertionsConverter = new LocalInspectionToolWrapper(new JUnit5AssertionsConverterInspection("JUnit4"));
        UsageInfo[] descriptors = InspectionEngine.runInspectionOnFile(myFile, assertionsConverter, globalContext).stream().map(descriptor -> new MyDescriptionBasedUsageInfo(descriptor)).toArray(UsageInfo[]::new);
        return ArrayUtil.mergeArrays(usages, descriptors);
      }

      @Override
      protected void performRefactoring(@NotNull UsageInfo[] usages) {
        List<UsageInfo> migrateUsages = new ArrayList<>();
        List<ProblemDescriptor> descriptions = new ArrayList<>();
        for (UsageInfo usage : usages) {
          if (usage instanceof MyDescriptionBasedUsageInfo) {
            descriptions.add (((MyDescriptionBasedUsageInfo)usage).myDescriptor);
          }
          else {
            migrateUsages.add(usage);
          }
        }
        super.performRefactoring(migrateUsages.toArray(new UsageInfo[migrateUsages.size()]));
        CleanupInspectionIntention.applyFixes(myProject, "Convert Assertions", descriptions, JUnit5AssertionsConverterInspection.ReplaceObsoleteAssertsFix.class);
      }
    }
  }
  
  private static class MyDescriptionBasedUsageInfo extends UsageInfo {
    private final ProblemDescriptor myDescriptor;

    public MyDescriptionBasedUsageInfo(ProblemDescriptor descriptor) {
      super(descriptor.getPsiElement());
      myDescriptor = descriptor;
    }
  }
}
