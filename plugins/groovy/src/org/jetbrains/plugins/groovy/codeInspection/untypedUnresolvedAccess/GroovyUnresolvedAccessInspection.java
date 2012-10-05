/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.GrHighlightUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.*;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicMethodFix;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicPropertyFix;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.highlighter.DefaultHighlighter;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GroovyDocPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrInterfaceDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.swing.*;

import static com.intellij.psi.PsiModifier.STATIC;
import static org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil.isCall;

/**
 * @author Maxim.Medvedev
 */
public class GroovyUnresolvedAccessInspection extends GroovySuppressableInspectionTool {
  private static final Logger LOG = Logger.getInstance(GroovyUnresolvedAccessInspection.class);
  private static final String SHORT_NAME = "GroovyUnresolvedAccess";

  public boolean myHighlightIfGroovyObjectOverridden = true;
  public boolean myHighlightIfMissingMethodsDeclared = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(GroovyInspectionBundle.message("highlight.if.groovy.object.methods.overridden"), "myHighlightIfGroovyObjectOverridden");
    optionsPanel.addCheckbox(GroovyInspectionBundle.message("highlight.if.missing.methods.declared"), "myHighlightIfMissingMethodsDeclared");
    return optionsPanel;
  }

  private static boolean isInspectionEnabled(PsiFile file, Project project) {
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    final HighlightDisplayKey unusedDefKey = HighlightDisplayKey.find(SHORT_NAME);
    return profile.isToolEnabled(unusedDefKey, file);
  }

  private static GroovyUnresolvedAccessInspection getInstance(PsiFile file, Project project) {
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    return (GroovyUnresolvedAccessInspection)profile.getUnwrappedTool(UnusedDeclarationInspection.SHORT_NAME, file);
  }

  public static void checkCodeReferenceElement(GrCodeReferenceElement refElement, final AnnotationHolder holder) {
    if (PsiTreeUtil.getParentOfType(refElement, GroovyDocPsiElement.class) != null) return;

    if (!isInspectionEnabled(refElement.getContainingFile(), refElement.getProject())) return;
    GroovyUnresolvedAccessInspection inspection = getInstance(refElement.getContainingFile(), refElement.getProject());

    PsiElement nameElement = refElement.getReferenceNameElement();
    if (nameElement == null) return;

    if (isResolvedStaticImport(refElement)) return;

    GroovyResolveResult resolveResult = refElement.advancedResolve();
    final PsiElement resolved = resolveResult.getElement();

    if (refElement.getParent() instanceof GrPackageDefinition) {
      checkPackage((GrPackageDefinition)refElement.getParent(), holder);
    }
    else if (resolved == null) {
      final Annotation annotation = holder.createErrorAnnotation(nameElement, GroovyBundle
        .message("cannot.resolve", refElement.getReferenceName()));
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);

      // todo implement for nested classes
      registerCreateClassByTypeFix(refElement, annotation);
      registerAddImportFixes(refElement, annotation);
      UnresolvedReferenceQuickFixProvider.registerReferenceFixes(refElement, new QuickFixActionRegistrarAdapter(annotation));
      OrderEntryFix.registerFixes(new QuickFixActionRegistrarAdapter(annotation), refElement);
    }
  }

  public static void checkReferenceExpression(GrReferenceExpression referenceExpression, AnnotationHolder holder) {
    PsiElement refNameElement = referenceExpression.getReferenceNameElement();
    if (refNameElement == null) return;

    if (!isInspectionEnabled(referenceExpression.getContainingFile(), referenceExpression.getProject())) return;
    GroovyUnresolvedAccessInspection inspection = getInstance(referenceExpression.getContainingFile(), referenceExpression.getProject());

    boolean cannotBeDynamic = PsiUtil.isCompileStatic(referenceExpression) || isPropertyAccessInStaticMethod(referenceExpression);
    GroovyResolveResult resolveResult = getBestResolveResult(referenceExpression);

    if (resolveResult.getElement() != null) {
      if (!isStaticOk(resolveResult)) {
        createAnnotationForRef(holder, referenceExpression, cannotBeDynamic,
                               GroovyBundle.message("cannot.reference.nonstatic", referenceExpression.getReferenceName()));
      }
      return;
    }

    if (ResolveUtil.isKeyOfMap(referenceExpression)) {
      Annotation annotation = holder.createInfoAnnotation(refNameElement, null);
      annotation.setTextAttributes(DefaultHighlighter.MAP_KEY);
      return;
    }

    if (GrHighlightUtil.shouldHighlightAsUnresolved(referenceExpression)) {
      Annotation annotation = createAnnotationForRef(holder, referenceExpression, cannotBeDynamic,
                                                     GroovyBundle.message("cannot.resolve", referenceExpression.getReferenceName()));
      if (isCall(referenceExpression)) {
        registerStaticImportFix(referenceExpression, annotation);
      }
      else {
        registerCreateClassByTypeFix(referenceExpression, annotation);
        registerAddImportFixes(referenceExpression, annotation);
      }

      registerReferenceFixes(referenceExpression, annotation, cannotBeDynamic);
      UnresolvedReferenceQuickFixProvider.registerReferenceFixes(referenceExpression, new QuickFixActionRegistrarAdapter(annotation));
      OrderEntryFix.registerFixes(new QuickFixActionRegistrarAdapter(annotation), referenceExpression);
    }
  }

  private static boolean isResolvedStaticImport(GrCodeReferenceElement refElement) {
    final PsiElement parent = refElement.getParent();
    return parent instanceof GrImportStatement &&
           ((GrImportStatement)parent).isStatic() &&
           refElement.multiResolve(false).length > 0;
  }

  private static boolean isStaticOk(GroovyResolveResult resolveResult) {
    if (resolveResult.isStaticsOK()) return true;

    PsiElement resolved = resolveResult.getElement();
    LOG.assertTrue(resolved != null);
    LOG.assertTrue(resolved instanceof PsiModifierListOwner, resolved + " : " + resolved.getText());

    return (((PsiModifierListOwner)resolved).hasModifierProperty(STATIC));
  }

  private static GroovyResolveResult getBestResolveResult(GrReferenceExpression ref) {
    GroovyResolveResult[] results = ref.multiResolve(false);
    if (results.length == 0) return GroovyResolveResult.EMPTY_RESULT;
    if (results.length == 1) return results[0];

    for (GroovyResolveResult result : results) {
      if (result.isAccessible() && result.isStaticsOK()) return result;
    }

    for (GroovyResolveResult result : results) {
      if (result.isStaticsOK()) return result;
    }

    return results[0];
  }

  private static boolean isPropertyAccessInStaticMethod(GrReferenceExpression referenceExpression) {
    if (referenceExpression.getParent() instanceof GrMethodCall) return false;
    GrMember context = PsiTreeUtil.getParentOfType(referenceExpression, GrMember.class, true, GrClosableBlock.class);
    return (context instanceof GrMethod || context instanceof GrClassInitializer) && context.hasModifierProperty(STATIC);
  }

  private static Annotation createAnnotationForRef(AnnotationHolder holder,
                                                   GrReferenceExpression referenceExpression,
                                                   boolean compileStatic,
                                                   final String message) {
    PsiElement refNameElement = referenceExpression.getReferenceNameElement();
    assert refNameElement != null;

    Annotation annotation;
    if (compileStatic) {
      annotation = holder.createErrorAnnotation(refNameElement, message);
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
    }
    else {
      annotation = holder.createInfoAnnotation(refNameElement, message);
      annotation.setTextAttributes(DefaultHighlighter.UNRESOLVED_ACCESS);
    }

    return annotation;
  }

  private static void registerStaticImportFix(GrReferenceExpression referenceExpression, Annotation annotation) {
    final String referenceName = referenceExpression.getReferenceName();
    if (StringUtil.isEmpty(referenceName)) return;
    if (referenceExpression.getQualifier() != null) return;

    annotation.registerFix(new GroovyStaticImportMethodFix((GrMethodCall)referenceExpression.getParent()));
  }

  private static void checkPackage(GrPackageDefinition packageDefinition, final AnnotationHolder holder) {
    final PsiFile file = packageDefinition.getContainingFile();
    assert file != null;

    PsiDirectory psiDirectory = file.getContainingDirectory();
    if (psiDirectory != null && file instanceof GroovyFile) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
      if (aPackage != null) {
        String expectedPackage = aPackage.getQualifiedName();
        String actualPackage = packageDefinition.getPackageName();
        if (!expectedPackage.equals(actualPackage)) {
          final Annotation annotation = holder.createWarningAnnotation(packageDefinition, GroovyBundle
            .message("wrong.package.name", actualPackage, aPackage.getQualifiedName()));
          annotation.registerFix(new ChangePackageQuickFix((GroovyFile)packageDefinition.getContainingFile(), expectedPackage));
          annotation.registerFix(new GrMoveToDirFix(actualPackage));
        }
      }
    }
  }

  private static void registerReferenceFixes(GrReferenceExpression refExpr, Annotation annotation, boolean compileStatic) {
    PsiClass targetClass = QuickfixUtil.findTargetClass(refExpr, compileStatic);
    if (targetClass == null) return;

    if (!compileStatic) {
      addDynamicAnnotation(annotation, refExpr);
    }
    if (targetClass.isWritable()) {
      annotation.registerFix(new CreateFieldFromUsageFix(refExpr, targetClass));

      if (refExpr.getParent() instanceof GrCall && refExpr.getParent() instanceof GrExpression) {
        annotation.registerFix(new CreateMethodFromUsageFix(refExpr, targetClass));
      }
    }

    if (!refExpr.isQualified()) {
      GrVariableDeclarationOwner owner = PsiTreeUtil.getParentOfType(refExpr, GrVariableDeclarationOwner.class);
      if (!(owner instanceof GroovyFileBase) || ((GroovyFileBase)owner).isScript()) {
        annotation.registerFix(new CreateLocalVariableFromUsageFix(refExpr, owner));
      }
      if (PsiTreeUtil.getParentOfType(refExpr, GrMethod.class) != null) {
        annotation.registerFix(new CreateParameterFromUsageFix(refExpr));
      }
    }
  }

  private static void addDynamicAnnotation(Annotation annotation, GrReferenceExpression referenceExpression) {
    final PsiFile containingFile = referenceExpression.getContainingFile();
    VirtualFile file;
    if (containingFile != null) {
      file = containingFile.getVirtualFile();
      if (file == null) return;
    }
    else {
      return;
    }

    if (isCall(referenceExpression)) {
      PsiType[] argumentTypes = PsiUtil.getArgumentTypes(referenceExpression, false);
      if (argumentTypes != null) {
        annotation.registerFix(new DynamicMethodFix(referenceExpression, argumentTypes), referenceExpression.getTextRange());
      }
    }
    else {
      annotation.registerFix(new DynamicPropertyFix(referenceExpression), referenceExpression.getTextRange());
    }
  }

  private static void registerAddImportFixes(GrReferenceElement refElement, Annotation annotation) {
    final String referenceName = refElement.getReferenceName();
    //noinspection ConstantConditions
    if (StringUtil.isEmpty(referenceName)) return;
    if (!(refElement instanceof GrCodeReferenceElement) && Character.isLowerCase(referenceName.charAt(0))) return;
    if (refElement.getQualifier() != null) return;

    annotation.registerFix(new GroovyAddImportAction(refElement));
  }

  private static void registerCreateClassByTypeFix(GrReferenceElement refElement, Annotation annotation) {
    GrPackageDefinition packageDefinition = PsiTreeUtil.getParentOfType(refElement, GrPackageDefinition.class);
    if (packageDefinition != null) return;

    PsiElement parent = refElement.getParent();
    if (parent instanceof GrNewExpression &&
        refElement.getManager().areElementsEquivalent(((GrNewExpression)parent).getReferenceElement(), refElement)) {
      annotation.registerFix(CreateClassFix.createClassFromNewAction((GrNewExpression)parent));
    }
    else {
      if (shouldBeInterface(refElement)) {
        annotation.registerFix(CreateClassFix.createClassFixAction(refElement, CreateClassKind.INTERFACE));
      }
      else if (shouldBeClass(refElement)) {
        annotation.registerFix(CreateClassFix.createClassFixAction(refElement, CreateClassKind.CLASS));
        annotation.registerFix(CreateClassFix.createClassFixAction(refElement, CreateClassKind.ENUM));
      }
      else if (shouldBeAnnotation(refElement)) {
        annotation.registerFix(CreateClassFix.createClassFixAction(refElement, CreateClassKind.ANNOTATION));
      }
      else {
        annotation.registerFix(CreateClassFix.createClassFixAction(refElement, CreateClassKind.CLASS));
        annotation.registerFix(CreateClassFix.createClassFixAction(refElement, CreateClassKind.INTERFACE));
        annotation.registerFix(CreateClassFix.createClassFixAction(refElement, CreateClassKind.ENUM));
        annotation.registerFix(CreateClassFix.createClassFixAction(refElement, CreateClassKind.ANNOTATION));
      }
    }
  }

  private static boolean shouldBeAnnotation(GrReferenceElement element) {
    return element.getParent() instanceof GrAnnotation;
  }

  private static boolean shouldBeInterface(GrReferenceElement myRefElement) {
    PsiElement parent = myRefElement.getParent();
    return parent instanceof GrImplementsClause || parent instanceof GrExtendsClause && parent.getParent() instanceof GrInterfaceDefinition;
  }

  private static boolean shouldBeClass(GrReferenceElement myRefElement) {
    PsiElement parent = myRefElement.getParent();
    return parent instanceof GrExtendsClause && !(parent.getParent() instanceof GrInterfaceDefinition);
  }

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return BaseInspection.PROBABLE_BUGS;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Access to unresolved expression";
  }

  private static class QuickFixActionRegistrarAdapter implements QuickFixActionRegistrar {
    private final Annotation myAnnotation;

    public QuickFixActionRegistrarAdapter(Annotation annotation) {
      myAnnotation = annotation;
    }

    @Override
    public void register(IntentionAction action) {
      myAnnotation.registerFix(action);
    }

    @Override
    public void register(TextRange fixRange, IntentionAction action, HighlightDisplayKey key) {
      myAnnotation.registerFix(action, fixRange, key);
    }

    @Override
    public void unregister(Condition<IntentionAction> condition) {
      throw new UnsupportedOperationException();
    }
  }
}
