// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.AtomicClearableLazyValue;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.uast.UastVisitorAdapter;
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

public class SuspiciousPackagePrivateAccessInspection extends AbstractBaseUastLocalInspectionTool {
  private static final Key<SuspiciousPackagePrivateAccessInspection> INSPECTION_KEY = Key.create("SuspiciousPackagePrivateAccess");
  @XCollection
  public List<ModulesSet> MODULES_SETS_LOADED_TOGETHER = new ArrayList<>();
  private final AtomicClearableLazyValue<Map<String, ModulesSet>> myModuleSetByModuleName = AtomicClearableLazyValue.create(() -> {
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

  private static class SuspiciousApiUsageProcessor implements ApiUsageProcessor {

    private final ProblemsHolder myProblemsHolder;
    private final Map<String, ModulesSet> myModuleNameToModulesSet;

    private SuspiciousApiUsageProcessor(ProblemsHolder problemsHolder, Map<String, ModulesSet> moduleNameToModulesSet) {
      myProblemsHolder = problemsHolder;
      myModuleNameToModulesSet = moduleNameToModulesSet;
    }

    @Override
    public void processReference(@NotNull UElement sourceNode, @NotNull PsiModifierListOwner target, @Nullable UExpression qualifier) {
      PsiClass accessObjectType = getAccessObjectType(qualifier);
      if (target instanceof PsiMember) {
        checkAccess(sourceNode, (PsiMember)target, accessObjectType);
      }
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

    private void checkAccess(@NotNull UElement sourceNode, @NotNull PsiMember target, @Nullable PsiClass accessObjectType) {
      if (target.hasModifier(JvmModifier.PACKAGE_LOCAL)) {
        checkPackageLocalAccess(sourceNode, target, "package-private");
      }
      else if (target.hasModifier(JvmModifier.PROTECTED) && !canAccessProtectedMember(sourceNode, target, accessObjectType)) {
        checkPackageLocalAccess(sourceNode, target, "protected and used not through a subclass here");
      }
    }

    private void checkPackageLocalAccess(@NotNull UElement sourceNode, PsiMember targetElement, final String accessType) {
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
          myProblemsHolder.registerProblem(sourcePsi, elementDescription + " is " + accessType + ", but declared in a different module '"
                                                      + targetModule.getName() + "'",
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

    PsiElement sourcePsi = sourceNode.getSourcePsi();
    UClass sourceClass = UastUtils.findContaining(sourcePsi, UClass.class);
    if (sourceClass == null) return false;
    return canAccessProtectedMember(member, memberClass, accessObjectType, member.hasModifierProperty(PsiModifier.STATIC),
                                    sourceClass);
  }

  /**
   * The implementation was copied from {@link com.intellij.psi.impl.source.resolve.JavaResolveUtil#canAccessProtectedMember} but uses UAST
   * to find outer class as a workaround for bugs in Kotlin Light PSI (KT-30759, KT-30752)
   */
  private static boolean canAccessProtectedMember(@NotNull PsiMember member, @NotNull PsiClass memberClass,
                                                  @Nullable PsiClass accessObjectClass, boolean isStatic, @Nullable UClass contextClass) {
    while (contextClass != null) {
      PsiClass javaPsiClass = contextClass.getJavaPsi();
      if (InheritanceUtil.isInheritorOrSelf(javaPsiClass, memberClass, true)) {
        if (member instanceof PsiClass || isStatic || accessObjectClass == null
            || InheritanceUtil.isInheritorOrSelf(accessObjectClass, javaPsiClass, true)) {
          return true;
        }
      }

      contextClass = getOuterClass(contextClass);
    }
    return false;
  }

  private static UClass getOuterClass(@NotNull UClass aClass) {
    UElement uastParent = aClass.getUastParent();
    if (uastParent == null) return null;
    PsiElement sourcePsi = uastParent.getSourcePsi();
    while (sourcePsi == null) {
      uastParent = uastParent.getUastParent();
      if (uastParent == null) return null;
      sourcePsi = uastParent.getSourcePsi();
    }
    return UastUtils.findContaining(sourcePsi, UClass.class);
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

  private static class MarkModulesAsLoadedTogetherFix implements LocalQuickFix {
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
      return "Mark '" + myModule1 + "' and '" + myModule2 + "' modules as loaded together";
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return "Mark modules as loaded together";
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
