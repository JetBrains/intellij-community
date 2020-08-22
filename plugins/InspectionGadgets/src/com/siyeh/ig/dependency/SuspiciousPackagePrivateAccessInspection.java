// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.dependency;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor;
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public final class SuspiciousPackagePrivateAccessInspection extends AbstractBaseUastLocalInspectionTool {
  private static final Key<SuspiciousPackagePrivateAccessInspection> INSPECTION_KEY = Key.create("SuspiciousPackagePrivateAccess");
  @XCollection
  public List<ModulesSet> MODULES_SETS_LOADED_TOGETHER = new ArrayList<>();
  private final ClearableLazyValue<Map<String, ModulesSet>> myModuleSetByModuleName = ClearableLazyValue.createAtomic(() -> {
    Map<String, ModulesSet> result = new HashMap<>();
    for (ModulesSet modulesSet : MODULES_SETS_LOADED_TOGETHER) {
      for (String module : modulesSet.modules) {
        result.put(module, modulesSet);
      }
    }
    return result;
  });

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return ApiUsageUastVisitor.createPsiElementVisitor(
      new SuspiciousApiUsageProcessor(holder, myModuleSetByModuleName.getValue())
    );
  }

  private static final class SuspiciousApiUsageProcessor implements ApiUsageProcessor {

    private final ProblemsHolder myProblemsHolder;
    private final Map<String, ModulesSet> myModuleNameToModulesSet;

    private SuspiciousApiUsageProcessor(ProblemsHolder problemsHolder, Map<String, ModulesSet> moduleNameToModulesSet) {
      myProblemsHolder = problemsHolder;
      myModuleNameToModulesSet = moduleNameToModulesSet;
    }

    @Override
    public void processReference(@NotNull UElement sourceNode, @NotNull PsiModifierListOwner target, @Nullable UExpression qualifier) {
      PsiClass accessObjectType = getAccessObjectType(qualifier);
      if (target instanceof PsiJvmMember) {
        checkAccess(sourceNode, (PsiJvmMember)target, accessObjectType);
        if (!(target instanceof PsiClass)) {
          if (accessObjectType != null) {
            checkAccess(sourceNode, accessObjectType, null);
          }
        }
      }
    }

    @Override
    public void processMethodOverriding(@NotNull UMethod method, @NotNull PsiMethod targetElement) {
      checkOverridePackageLocal(method, targetElement);
    }

    @Override
    public void processConstructorInvocation(@NotNull UElement sourceNode,
                                             @NotNull PsiClass instantiatedClass,
                                             @Nullable PsiMethod constructor,
                                             @Nullable UClass subclassDeclaration) {
      if (subclassDeclaration == null && constructor != null) {
        checkAccess(sourceNode, constructor, null);
      }
    }

    @Nullable
    private static PsiClass getAccessObjectType(@Nullable UExpression qualifier) {
      if (qualifier == null || qualifier instanceof UThisExpression || qualifier instanceof USuperExpression) {
        return null;
      }

      PsiType type = qualifier.getExpressionType();
      if (type instanceof PsiClassType) {
        return ((PsiClassType)type).resolve();
      }
      if (qualifier instanceof UReferenceExpression) {
        return ObjectUtils.tryCast(((UReferenceExpression)qualifier).resolve(), PsiClass.class);
      }
      return null;
    }

    private void checkAccess(@NotNull UElement sourceNode, @NotNull PsiJvmMember target, @Nullable PsiClass accessObjectType) {
      if (target.hasModifier(JvmModifier.PACKAGE_LOCAL)) {
        checkPackageLocalAccess(sourceNode, target, "package-private");
      }
      else if (target.hasModifier(JvmModifier.PROTECTED) && !canAccessProtectedMember(sourceNode, target, accessObjectType)) {
        checkPackageLocalAccess(sourceNode, target, "protected and used not through a subclass here");
      }
    }

    private void checkPackageLocalAccess(@NotNull UElement sourceNode, PsiJvmMember targetElement, final String accessType) {
      PsiElement sourcePsi = sourceNode.getSourcePsi();
      if (sourcePsi != null) {
        Module targetModule = ModuleUtilCore.findModuleForPsiElement(targetElement);
        Module sourceModule = ModuleUtilCore.findModuleForPsiElement(sourcePsi);
        if (isPackageLocalAccessSuspicious(sourceModule, targetModule) &&
            PsiTreeUtil.getParentOfType(sourcePsi, PsiComment.class) == null) {
          List<IntentionAction> fixes =
            JvmElementActionFactories.createModifierActions(targetElement, MemberRequestsKt.modifierRequest(JvmModifier.PUBLIC, true));
          String elementDescription =
            StringUtil.removeHtmlTags(StringUtil.capitalize(RefactoringUIUtil.getDescription(targetElement, true)));
          LocalQuickFix[] quickFixes =
            IntentionWrapper.wrapToQuickFixes(fixes.toArray(IntentionAction.EMPTY_ARRAY), targetElement.getContainingFile());
          myProblemsHolder.registerProblem(sourcePsi, InspectionGadgetsBundle
                                             .message("inspection.suspicious.package.private.access.description", elementDescription, accessType, targetModule.getName()),
                                           ArrayUtil.append(quickFixes,
                                                            new MarkModulesAsLoadedTogetherFix(sourceModule.getName(),
                                                                                               targetModule.getName())));
        }
      }
    }

    private void checkOverridePackageLocal(@NotNull UMethod sourceNode, @NotNull PsiJvmMember targetElement) {
      PsiElement sourcePsi = sourceNode.getSourcePsi();
      PsiElement nameIdentifier = UElementKt.getSourcePsiElement(sourceNode.getUastAnchor());
      if (sourcePsi != null && nameIdentifier != null && targetElement.hasModifier(JvmModifier.PACKAGE_LOCAL)) {
        Module targetModule = ModuleUtilCore.findModuleForPsiElement(targetElement);
        Module sourceModule = ModuleUtilCore.findModuleForPsiElement(sourcePsi);
        if (isPackageLocalAccessSuspicious(sourceModule, targetModule)) {
          List<IntentionAction> fixes =
            JvmElementActionFactories.createModifierActions(targetElement, MemberRequestsKt.modifierRequest(JvmModifier.PUBLIC, true));
          String elementDescription =
            StringUtil.removeHtmlTags(StringUtil.capitalize(RefactoringUIUtil.getDescription(targetElement, false)));
          final String classDescription =
            StringUtil.removeHtmlTags(RefactoringUIUtil.getDescription(targetElement.getParent(), false));
          LocalQuickFix[] quickFixes =
            IntentionWrapper.wrapToQuickFixes(fixes.toArray(IntentionAction.EMPTY_ARRAY), targetElement.getContainingFile());
          String problem = InspectionGadgetsBundle
            .message("inspection.suspicious.package.private.access.problem", elementDescription, classDescription, targetModule.getName());
          myProblemsHolder.registerProblem(nameIdentifier, problem,
                                           ArrayUtil.append(quickFixes,
                                                            new MarkModulesAsLoadedTogetherFix(sourceModule.getName(),
                                                                                               targetModule.getName())));
        }
      }
    }

    private boolean isPackageLocalAccessSuspicious(Module sourceModule, Module targetModule) {
      if (targetModule == null || sourceModule == null || targetModule.equals(sourceModule)) {
        return false;
      }
      ModulesSet sourceGroup = myModuleNameToModulesSet.get(sourceModule.getName());
      ModulesSet targetGroup = myModuleNameToModulesSet.get(targetModule.getName());
      return sourceGroup == null || sourceGroup != targetGroup;
    }
  }

  private static boolean canAccessProtectedMember(UElement sourceNode, PsiMember member, PsiClass accessObjectType) {
    PsiClass memberClass = member.getContainingClass();
    if (memberClass == null) return false;

    PsiClass contextClass = getContextClass(sourceNode, member instanceof PsiClass);
    if (contextClass == null) return false;

    return canAccessProtectedMember(member, memberClass, accessObjectType, member.hasModifierProperty(PsiModifier.STATIC), contextClass);
  }

  /**
   * Returns {@code true} if protected {@code member} can be accessed from {@code contextClass} at runtime if code compiles successfully.
   */
  private static boolean canAccessProtectedMember(@NotNull PsiMember member, @NotNull PsiClass memberClass,
                                                  @Nullable PsiClass accessObjectClass, boolean isStatic, @NotNull PsiClass contextClass) {
    if (!ClassUtils.inSamePackage(memberClass, contextClass)) {
      //in this case if the code compiles ok javac will generate required bridge methods so there will be no problems at runtime
      return true;
    }

    /*
     since classes are located in the same package javac won't generate bridge methods for members inherited by enclosing class, so
     local and inner classes won't have access to protected methods inherited by enclosing class and therefore we shouldn't check
     enclosing classes here like JavaResolveUtil.canAccessProtectedMember does.
    */
    if (InheritanceUtil.isInheritorOrSelf(contextClass, memberClass, true)) {
      if (member instanceof PsiClass || isStatic || accessObjectClass == null
          || InheritanceUtil.isInheritorOrSelf(accessObjectClass, contextClass, true)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static PsiClass getContextClass(@NotNull UElement sourceNode, boolean forClassReference) {
    PsiElement sourcePsi = sourceNode.getSourcePsi();
    UClass sourceClass = UastUtils.findContaining(sourcePsi, UClass.class);
    if (sourceClass == null) return null;
    if (isReferenceBelongsToEnclosingClass(sourceNode, sourceClass, forClassReference)) {
      UClass parentClass = UastUtils.getContainingUClass(sourceClass);
      return parentClass != null ? parentClass.getJavaPsi() : null;
    }
    return sourceClass.getJavaPsi();
  }

  private static boolean isReferenceBelongsToEnclosingClass(@NotNull UElement sourceNode, @NotNull UClass sourceClass,
                                                            boolean forClassReference) {
    UElement parent = sourceClass.getUastParent();
    if (parent instanceof UObjectLiteralExpression) {
      if (((UCallExpression)parent).getValueArguments().stream().anyMatch(it -> UastUtils.isPsiAncestor(it, sourceNode))) {
        return true;
      }
    }
    return forClassReference && sourceClass.getUastSuperTypes().stream().anyMatch(it -> UastUtils.isPsiAncestor(it, sourceNode));
  }

  @Tag("modules-set")
  public static class ModulesSet {
    @XCollection(elementName = "module", valueAttributeName = "name")
    @Property(surroundWithTag = false)
    public Set<String> modules = new LinkedHashSet<>();
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    JBTextArea component = new JBTextArea(5, 80);
    component.setText(MODULES_SETS_LOADED_TOGETHER.stream().map(it -> String.join(",", it.modules)).collect(Collectors.joining("\n")));
    component.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        MODULES_SETS_LOADED_TOGETHER.clear();
        for (String line : StringUtil.splitByLines(component.getText())) {
          ModulesSet set = new ModulesSet();
          set.modules = new LinkedHashSet<>(StringUtil.split(line, ","));
          if (!set.modules.isEmpty()) {
            MODULES_SETS_LOADED_TOGETHER.add(set);
          }
        }
        myModuleSetByModuleName.drop();
      }
    });
    JPanel panel = new JPanel(new BorderLayout());
    JPanel labels = new JPanel(new FlowLayout(FlowLayout.LEFT));
    labels.add(new JBLabel(InspectionGadgetsBundle.message("groups.of.modules.loaded.together.label")));
    labels.add(ContextHelpLabel.create(InspectionGadgetsBundle.message("groups.of.modules.loaded.together.description")));
    panel.add(labels, BorderLayout.NORTH);
    panel.add(new JBScrollPane(component), BorderLayout.CENTER);
    return panel;
  }

  @Override
  public void readSettings(@NotNull Element node) {
    super.readSettings(node);
    myModuleSetByModuleName.drop();
  }

  private static final class MarkModulesAsLoadedTogetherFix implements LocalQuickFix {
    private final String myModule1;
    private final String myModule2;

    private MarkModulesAsLoadedTogetherFix(String module1, String module2) {
      myModule1 = module1;
      myModule2 = module2;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("mark.modules.as.loaded.together.fix.text", myModule1, myModule2);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("mark.modules.as.loaded.together.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      InspectionProfileImpl profile = InspectionProfileManager.getInstance(project).getCurrentProfile();
      PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement != null) {
        profile.modifyToolSettings(INSPECTION_KEY, psiElement, inspection -> {
          Map<String, ModulesSet> moduleSetByModule = inspection.myModuleSetByModuleName.getValue();
          ModulesSet module1Set = moduleSetByModule.get(myModule1);
          ModulesSet module2Set = moduleSetByModule.get(myModule2);
          if (module1Set == null) {
            if (module2Set == null) {
              ModulesSet modulesSet = new ModulesSet();
              modulesSet.modules.add(myModule1);
              modulesSet.modules.add(myModule2);
              inspection.MODULES_SETS_LOADED_TOGETHER.add(modulesSet);
            }
            else {
              module2Set.modules.add(myModule1);
            }
          }
          else if (module2Set == null) {
            module1Set.modules.add(myModule2);
          }
          else if (module1Set != module2Set) {
            module1Set.modules.addAll(module2Set.modules);
            inspection.MODULES_SETS_LOADED_TOGETHER.remove(module2Set);
          }
          inspection.myModuleSetByModuleName.drop();
        });
      }
    }
  }
}
