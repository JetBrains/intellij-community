// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.dependency;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool;
import com.intellij.codeInspection.IntentionWrapper;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.AtomicClearableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.uast.UastVisitorAdapter;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ObjectUtils;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.siyeh.InspectionGadgetsBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class SuspiciousPackagePrivateAccessInspection extends AbstractBaseUastLocalInspectionTool {
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
    return new UastVisitorAdapter(new AbstractUastNonRecursiveVisitor() {
      @Override
      public boolean visitCallExpression(@NotNull UCallExpression node) {
        PsiMethod resolve = node.resolve();
        if (resolve != null) {
          UElement sourceNode = ObjectUtils.chooseNotNull(node.getMethodIdentifier(), node.getClassReference());
          checkAccess(Objects.requireNonNull(sourceNode), resolve, node.getReceiver());
        }
        UReferenceExpression classReference = node.getClassReference();
        if (resolve == null && classReference != null && node.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
          PsiElement element = classReference.resolve();
          if (element instanceof PsiClass) {
            checkClassReference(classReference, (PsiClass)element);
          }
        }
        return false;
      }

      @Override
      public boolean visitQualifiedReferenceExpression(@NotNull UQualifiedReferenceExpression node) {
        PsiElement resolve = node.resolve();
        if (resolve instanceof PsiField) {
          PsiField field = (PsiField)resolve;
          checkAccess(node.getSelector(), field, node.getReceiver()
          );
        }
        return false;
      }

      @Override
      public boolean visitTypeReferenceExpression(@NotNull UTypeReferenceExpression node) {
        PsiType type = node.getType();
        if (type instanceof PsiClassType) {
          checkClassReference(node, ((PsiClassType)type).resolve());
        }
        return false;
      }

      @Override
      public boolean visitCallableReferenceExpression(@NotNull UCallableReferenceExpression node) {
        PsiElement resolve = node.resolve();
        if (resolve instanceof PsiMember) {
          PsiMember member = (PsiMember)resolve;
          checkAccess(getReferenceNameElement(node), member, node.getQualifierExpression());
        }
        return false;
      }

      private void checkClassReference(UElement sourceNode, PsiClass targetClass) {
        if (targetClass != null) {
          checkAccess(sourceNode, targetClass, null);
        }
      }

      private void checkAccess(@NotNull UElement sourceNode, PsiMember target, @Nullable UExpression receiver) {
        if (target.hasModifier(JvmModifier.PACKAGE_LOCAL)) {
          checkPackageLocalAccess(sourceNode, target, "package-private");
        }
        else if (target.hasModifier(JvmModifier.PROTECTED) && receiver != null
                 && !(receiver instanceof UThisExpression) && !(receiver instanceof USuperExpression) && !canAccessProtectedMember(receiver, sourceNode, target)) {
          checkPackageLocalAccess(sourceNode, target, "protected and used not from a subclass here");
        }
      }

      private void checkPackageLocalAccess(@NotNull UElement sourceNode, PsiMember targetElement, final String accessType) {
        PsiElement sourcePsi = sourceNode.getSourcePsi();
        if (sourcePsi != null) {
          Module targetModule = ModuleUtilCore.findModuleForPsiElement(targetElement);
          Module sourceModule = ModuleUtilCore.findModuleForPsiElement(sourcePsi);
          if (isPackageLocalAccessSuspicious(sourceModule, targetModule)) {
            List<IntentionAction> fixes =
              JvmElementActionFactories.createModifierActions(targetElement, MemberRequestsKt.modifierRequest(JvmModifier.PUBLIC, true));
            holder.registerProblem(sourcePsi, StringUtil.removeHtmlTags(StringUtil.capitalize(RefactoringUIUtil.getDescription(targetElement, true))) + " is " + accessType +
                                              ", but declared in a different module '" + targetModule.getName() + "'",
                                   IntentionWrapper.wrapToQuickFixes(fixes.toArray(IntentionAction.EMPTY_ARRAY), targetElement.getContainingFile()));
          }
        }
      }
    }, true);
  }

  private UElement getReferenceNameElement(UCallableReferenceExpression node) {
    PsiElement psi = node.getSourcePsi();
    if (psi instanceof PsiReferenceExpression) {
      PsiElement nameElement = ((PsiReferenceExpression)psi).getReferenceNameElement();
      if (nameElement != null) {
        return UastContextKt.toUElement(nameElement);
      }
    }
    return node;
  }

  private static boolean canAccessProtectedMember(UExpression receiver, UElement sourceNode, PsiMember member) {
    PsiClass memberClass = member.getContainingClass();
    if (memberClass == null) return false;

    PsiClass accessObjectType;
    PsiType type = receiver.getExpressionType();
    if (type != null) {
      if (!(type instanceof PsiClassType)) return false;
      accessObjectType = ((PsiClassType)type).resolve();
      if (accessObjectType == null) return false;
    }
    else {
      PsiElement element = ((UReferenceExpression)receiver).resolve();
      if (!(element instanceof PsiClass)) return false;
      accessObjectType = (PsiClass)element;
    }

    UClass sourceClass = UastUtils.getParentOfType(sourceNode, UClass.class);
    if (sourceClass == null) return false;
    PsiClass sourceClassJava = sourceClass.getJavaPsi();
    return JavaResolveUtil.canAccessProtectedMember(member, memberClass, accessObjectType, sourceClassJava, member.hasModifierProperty(PsiModifier.STATIC));
  }

  private boolean isPackageLocalAccessSuspicious(Module sourceModule, Module targetModule) {
    if (targetModule == null || sourceModule == null || targetModule.equals(sourceModule)) {
      return false;
    }
    ModulesSet sourceGroup = myModuleSetByModuleName.getValue().get(sourceModule.getName());
    ModulesSet targetGroup = myModuleSetByModuleName.getValue().get(targetModule.getName());
    return sourceGroup == null || sourceGroup != targetGroup;
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
    JBTextArea component = new JBTextArea();
    component.setText(MODULES_SETS_LOADED_TOGETHER.stream().map(it -> String.join(",", it.modules)).collect(Collectors.joining("\n")));
    component.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        MODULES_SETS_LOADED_TOGETHER.clear();
        for (String line : StringUtil.splitByLines(component.getText())) {
          ModulesSet set = new ModulesSet();
          set.modules = new LinkedHashSet<>(StringUtil.split(line, ","));
          MODULES_SETS_LOADED_TOGETHER.add(set);
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
}
