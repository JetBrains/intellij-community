// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.intention.impl.AddOnDemandStaticImportAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.actions.CleanupInspectionUtil;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringManager;
import com.intellij.refactoring.migration.MigrationManager;
import com.intellij.refactoring.migration.MigrationMap;
import com.intellij.refactoring.migration.MigrationProcessor;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.testIntegration.TestFramework;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_HIERARCHY;

public class JUnit5ConverterInspection extends BaseInspection {
  private static final List<String> ruleAnnotations = Arrays.asList(JUnitCommonClassNames.ORG_JUNIT_RULE, JUnitCommonClassNames.ORG_JUNIT_CLASS_RULE);

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return "#ref can be JUnit 5 test";
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    if (!JavaVersionService.getInstance().isAtLeast(file, JavaSdkVersion.JDK_1_8)) return false;
    if (JavaPsiFacade.getInstance(file.getProject()).findClass(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS, file.getResolveScope()) == null) {
      return false;
    }
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

        if (!canBeConvertedToJUnit5(aClass)) return;

        registerClassError(aClass);
      }
    };
  }

  protected static boolean canBeConvertedToJUnit5(PsiClass aClass) {
    if (AnnotationUtil.isAnnotated(aClass, TestUtils.RUN_WITH, CHECK_HIERARCHY)) {
      return false;
    }

    for (PsiField field : aClass.getAllFields()) {
      if (AnnotationUtil.isAnnotated(field, ruleAnnotations, 0)) {
        return false;
      }
    }

    for (PsiMethod method : aClass.getMethods()) {
      if (AnnotationUtil.isAnnotated(method, ruleAnnotations, 0)) {
        return false;
      }

      PsiAnnotation testAnnotation = AnnotationUtil.findAnnotation(method, true, JUnitCommonClassNames.ORG_JUNIT_TEST);
      if (testAnnotation != null && testAnnotation.getParameterList().getAttributes().length > 0) {
        return false;
      }
    }
    return true;
  }

  private static class MigrateToJUnit5 extends InspectionGadgetsFix implements BatchQuickFix {
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
          new MyJUnit5MigrationProcessor(project, migrationMap, Collections.singleton(psiClass.getContainingFile())).run();
        }
      }
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project,
                         CommonProblemDescriptor @NotNull [] descriptors,
                         @NotNull List psiElementsToIgnore,
                         @Nullable Runnable refreshViews) {
      Set<PsiFile> files = Arrays.stream(descriptors).map(descriptor -> ((ProblemDescriptor)descriptor).getPsiElement())
        .filter(Objects::nonNull)
        .map(element -> element.getContainingFile()).collect(Collectors.toSet());
      if (!files.isEmpty()) {
        MigrationManager manager = RefactoringManager.getInstance(project).getMigrateManager();
        MigrationMap migrationMap = manager.findMigrationMap("JUnit (4.x -> 5.0)");
        if (migrationMap != null) {
          new MyJUnit5MigrationProcessor(project, migrationMap, files).run();
          if (refreshViews != null) {
            refreshViews.run();
          }
        }
      }
    }

    private static class MyJUnit5MigrationProcessor extends MigrationProcessor {

      private final Project myProject;
      private final Set<? extends PsiFile> myFiles;

      MyJUnit5MigrationProcessor(Project project, MigrationMap migrationMap, Set<? extends PsiFile> files) {
        super(project, migrationMap, GlobalSearchScope.filesWithoutLibrariesScope(project, ContainerUtil.map(files, file -> file.getVirtualFile())));
        setPrepareSuccessfulSwingThreadCallback(EmptyRunnable.INSTANCE);
        myProject = project;
        myFiles = files;
      }

      @Override
      protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
        final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
        for (PsiFile file : myFiles) {
          for (PsiClass psiClass : ((PsiClassOwner)file).getClasses()) {
            Set<PsiClass> inheritors = new HashSet<>();
            ClassInheritorsSearch.search(psiClass).forEach(inheritor -> {
              if (!canBeConvertedToJUnit5(inheritor)) {
                inheritors.add(inheritor);
                return false;
              }
              return true;
            });
            if (!inheritors.isEmpty()) {
              conflicts.putValue(psiClass, "Class " + RefactoringUIUtil.getDescription(psiClass, true) + " can't be converted to JUnit 5, cause there are incompatible inheritor(s): " +
                                           StringUtil.join(inheritors, aClass -> aClass.getQualifiedName(), ", "));
            }
          }
        }
        setPreviewUsages(true);
        return showConflicts(conflicts, refUsages.get());
      }

      @Override
      protected UsageInfo @NotNull [] findUsages() {
        UsageInfo[] usages = super.findUsages();
        InspectionManager inspectionManager = InspectionManager.getInstance(myProject);
        GlobalInspectionContext globalContext = inspectionManager.createNewGlobalContext();
        LocalInspectionToolWrapper assertionsConverter = new LocalInspectionToolWrapper(new JUnit5AssertionsConverterInspection("JUnit4"));

        Stream<ProblemDescriptor> stream = myFiles.stream().flatMap(file -> InspectionEngine.runInspectionOnFile(file, assertionsConverter, globalContext).stream());
        UsageInfo[] descriptors = stream.map(descriptor -> new MyDescriptionBasedUsageInfo(descriptor)).toArray(UsageInfo[]::new);
        return ArrayUtil.mergeArrays(usages, descriptors);
      }

      List<SmartPsiElementPointer<PsiElement>> myReplacedRefs = new ArrayList<>();

      @Override
      protected void performRefactoring(UsageInfo @NotNull [] usages) {
        List<UsageInfo> migrateUsages = new ArrayList<>();
        List<ProblemDescriptor> descriptions = new ArrayList<>();
        SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
        for (UsageInfo usage : usages) {
          if (usage instanceof MyDescriptionBasedUsageInfo) {
            ProblemDescriptor descriptor = ((MyDescriptionBasedUsageInfo)usage).myDescriptor;
            descriptions.add (descriptor);
            markUsagesImportedThroughStaticImport(smartPointerManager, descriptor);
          }
          else {
            migrateUsages.add(usage);
          }
        }
        super.performRefactoring(migrateUsages.toArray(UsageInfo.EMPTY_ARRAY));
        CleanupInspectionUtil.getInstance().applyFixes(myProject, "Convert Assertions", descriptions, JUnit5AssertionsConverterInspection.ReplaceObsoleteAssertsFix.class, false);
      }

      @Override
      protected void performPsiSpoilingRefactoring() {
        super.performPsiSpoilingRefactoring();
        tryToRestoreStaticImportsOnNewAssertions();
      }

      private void markUsagesImportedThroughStaticImport(SmartPointerManager smartPointerManager, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
        if (callExpression != null) {
          PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
          PsiElement scope = methodExpression.getQualifierExpression() == null
                             ? methodExpression.advancedResolve(false).getCurrentFileResolveScope()
                             : null;
          if (scope instanceof PsiImportStaticStatement && ((PsiImportStaticStatement)scope).isOnDemand()) {
            myReplacedRefs.add(smartPointerManager.createSmartPsiElementPointer(callExpression));
          }
        }
      }

      private void tryToRestoreStaticImportsOnNewAssertions() {
        for (SmartPsiElementPointer<PsiElement> ref : myReplacedRefs) {
          PsiElement element = ref.getElement();
          if (element instanceof PsiMethodCallExpression) {
            PsiExpression qualifierExpression = ((PsiMethodCallExpression)element).getMethodExpression().getQualifierExpression();
            if (qualifierExpression != null) {
              PsiElement referenceNameElement = ((PsiReferenceExpression)qualifierExpression).getReferenceNameElement();
              PsiClass aClass = referenceNameElement != null ? AddOnDemandStaticImportAction
                .getClassToPerformStaticImport(referenceNameElement) : null;
              PsiFile containingFile = element.getContainingFile();
              if (aClass != null && !AddOnDemandStaticImportAction.invoke(myProject, containingFile, null, referenceNameElement)) {
                PsiImportStatementBase importReferenceTo = PsiTreeUtil
                  .getParentOfType(((PsiJavaFile)containingFile).findImportReferenceTo(aClass), PsiImportStatementBase.class);
                if (importReferenceTo != null) importReferenceTo.delete();
              }
            }
          }
        }
      }
    }
  }

  private static class MyDescriptionBasedUsageInfo extends UsageInfo {
    private final ProblemDescriptor myDescriptor;

    MyDescriptionBasedUsageInfo(ProblemDescriptor descriptor) {
      super(descriptor.getPsiElement());
      myDescriptor = descriptor;
    }
  }
}
