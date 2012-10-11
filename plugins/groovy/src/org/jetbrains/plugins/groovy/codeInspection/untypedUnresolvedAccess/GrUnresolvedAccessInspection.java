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

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CollectConsumer;
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
import org.jetbrains.plugins.groovy.extensions.GroovyUnresolvedHighlightFilter;
import org.jetbrains.plugins.groovy.findUsages.MissingMethodAndPropertyUtil;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GroovyDocPsiElement;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
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
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceResolveUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.swing.*;

import static com.intellij.psi.PsiModifier.STATIC;
import static org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil.isCall;
import static org.jetbrains.plugins.groovy.highlighter.DefaultHighlighter.MAP_KEY_ATTRIBUTES;
import static org.jetbrains.plugins.groovy.highlighter.DefaultHighlighter.UNRESOLVED_ACCESS_ATTRIBUTES;

/**
 * @author Maxim.Medvedev
 */
public class GrUnresolvedAccessInspection extends GroovySuppressableInspectionTool {
  private static final Logger LOG = Logger.getInstance(GrUnresolvedAccessInspection.class);
  private static final String SHORT_NAME = "GrUnresolvedAccess";

  public boolean myHighlightIfGroovyObjectOverridden = true;
  public boolean myHighlightIfMissingMethodsDeclared = true;

  private static boolean shouldHighlightAsUnresolved(@NotNull GrReferenceExpression referenceExpression) {
    if (GrHighlightUtil.isDeclarationAssignment(referenceExpression)) return false;

    GrExpression qualifier = referenceExpression.getQualifier();
    if (qualifier != null && qualifier.getType() == null && !isRefToPackage(qualifier)) return false;

    if (qualifier != null &&
        referenceExpression.getDotTokenType() == GroovyTokenTypes.mMEMBER_POINTER &&
        referenceExpression.multiResolve(false).length > 0) {
      return false;
    }

    if (!GroovyUnresolvedHighlightFilter.shouldHighlight(referenceExpression)) return false;

    CollectConsumer<PomTarget> consumer = new CollectConsumer<PomTarget>();
    for (PomDeclarationSearcher searcher : PomDeclarationSearcher.EP_NAME.getExtensions()) {
      searcher.findDeclarationsAt(referenceExpression, 0, consumer);
      if (consumer.getResult().size() > 0) return false;
    }

    return true;
  }

  private static boolean isRefToPackage(GrExpression expr) {
    return expr instanceof GrReferenceExpression && ((GrReferenceExpression)expr).resolve() instanceof PsiPackage;
  }

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

  private static GrUnresolvedAccessInspection getInstance(PsiFile file, Project project) {
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    return (GrUnresolvedAccessInspection)profile.getUnwrappedTool(SHORT_NAME, file);
  }

  private static HighlightDisplayLevel getHighlightDisplayLevel(Project project, GrReferenceElement ref) {
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    return profile.getErrorLevel(HighlightDisplayKey.find(SHORT_NAME), ref);
  }

  @Nullable
  public static HighlightInfo checkCodeReferenceElement(GrCodeReferenceElement refElement) {
    HighlightInfo info = checkCodeRefInner(refElement);
    addEmptyIntentionIfNeeded(info);
    return info;
  }

  @Nullable
  public static HighlightInfo checkReferenceExpression(GrReferenceExpression ref) {
    HighlightInfo info = checkRefInner(ref);
    addEmptyIntentionIfNeeded(info);
    return info;
  }

  @Nullable
  private static HighlightInfo checkCodeRefInner(GrCodeReferenceElement refElement) {
    if (PsiTreeUtil.getParentOfType(refElement, GroovyDocPsiElement.class) != null) return null;

    PsiElement nameElement = refElement.getReferenceNameElement();
    if (nameElement == null) return null;

    if (isResolvedStaticImport(refElement)) return null;

    GroovyResolveResult resolveResult = refElement.advancedResolve();
    final PsiElement resolved = resolveResult.getElement();

    if (!(refElement.getParent() instanceof GrPackageDefinition) && resolved == null) {
      String message = GroovyBundle.message("cannot.resolve", refElement.getReferenceName());
      HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, nameElement, message);

      // todo implement for nested classes
      HighlightDisplayKey displayKey = HighlightDisplayKey.find(SHORT_NAME);
      registerCreateClassByTypeFix(refElement, info, displayKey);
      registerAddImportFixes(refElement, info, displayKey);
      UnresolvedReferenceQuickFixProvider.registerReferenceFixes(refElement, new QuickFixActionRegistrarAdapter(info, displayKey));
      OrderEntryFix.registerFixes(new QuickFixActionRegistrarAdapter(info, displayKey), refElement);

      return info;
    }

    return null;
  }

  @Nullable
  private static HighlightInfo checkRefInner(GrReferenceExpression ref) {
    PsiElement refNameElement = ref.getReferenceNameElement();
    if (refNameElement == null) return null;

    if (!isInspectionEnabled(ref.getContainingFile(), ref.getProject())) return null;
    GrUnresolvedAccessInspection inspection = getInstance(ref.getContainingFile(), ref.getProject());

    boolean cannotBeDynamic = PsiUtil.isCompileStatic(ref) || isPropertyAccessInStaticMethod(ref);
    GroovyResolveResult resolveResult = getBestResolveResult(ref);

    if (resolveResult.getElement() != null) {
      if (isStaticOk(resolveResult))  return null;
      String message = GroovyBundle.message("cannot.reference.nonstatic", ref.getReferenceName());
      return createAnnotationForRef(ref, cannotBeDynamic, message);
    }

    if (ResolveUtil.isKeyOfMap(ref)) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.INFORMATION, refNameElement, null, MAP_KEY_ATTRIBUTES);
    }

    if (!cannotBeDynamic) {
      if (!inspection.myHighlightIfGroovyObjectOverridden && areGroovyObjectMethodsOverridden(ref)) return null;
      if (!inspection.myHighlightIfMissingMethodsDeclared && areMissingMethodsDeclared(ref)) return null;

      if (GroovySuppressableInspectionTool.isElementToolSuppressedIn(ref, SHORT_NAME)) return null;
    }

    if (cannotBeDynamic || shouldHighlightAsUnresolved(ref)) {
      HighlightInfo info = createAnnotationForRef(ref, cannotBeDynamic, GroovyBundle.message("cannot.resolve", ref.getReferenceName()));

      HighlightDisplayKey displayKey = HighlightDisplayKey.find(SHORT_NAME);
      if (isCall(ref)) {
        registerStaticImportFix(ref, info, displayKey);
      }
      else {
        registerCreateClassByTypeFix(ref, info, displayKey);
        registerAddImportFixes(ref, info, displayKey);
      }

      registerReferenceFixes(ref, info, cannotBeDynamic, displayKey);
      UnresolvedReferenceQuickFixProvider.registerReferenceFixes(ref, new QuickFixActionRegistrarAdapter(info, displayKey));
      OrderEntryFix.registerFixes(new QuickFixActionRegistrarAdapter(info, displayKey), ref);
      return info;
    }

    return null;
  }

  private static boolean areMissingMethodsDeclared(GrReferenceExpression ref) {
    PsiType qualifierType = GrReferenceResolveUtil.getQualifierType(ref);
    if (!(qualifierType instanceof PsiClassType)) return false;

    PsiClass resolved = ((PsiClassType)qualifierType).resolve();
    if (resolved == null) return false;

    if (ref.getParent() instanceof GrCall) {
      PsiMethod[] found = resolved.findMethodsByName("methodMissing", true);
      for (PsiMethod method : found) {
        if (MissingMethodAndPropertyUtil.isMethodMissing(method)) return true;
      }
    }
    else {
      PsiMethod[] found = resolved.findMethodsByName("propertyMissing", true);
      for (PsiMethod method : found) {
        if (MissingMethodAndPropertyUtil.isPropertyMissing(method)) return true;
      }
    }

    return false;
  }

  private static boolean areGroovyObjectMethodsOverridden(GrReferenceExpression ref) {
    PsiType qualifierType = GrReferenceResolveUtil.getQualifierType(ref);
    if (!(qualifierType instanceof PsiClassType)) return false;

    PsiClass resolved = ((PsiClassType)qualifierType).resolve();
    if (resolved == null) return false;

    PsiClass groovyObject =
      JavaPsiFacade.getInstance(ref.getProject()).findClass(GroovyCommonClassNames.GROOVY_OBJECT, ref.getResolveScope());
    if (groovyObject == null) return false;


    String methodName;
    if (ref.getParent() instanceof GrCall) {
      methodName = "invokeMethod";
    }
    else if (PsiUtil.isLValue(ref)) {
      methodName = "setProperty";
    }
    else {
      methodName = "getProperty";
    }

    PsiMethod[] patternMethods = groovyObject.findMethodsByName(methodName, false);
    if (patternMethods.length != 1) return false;

    PsiMethod patternMethod = patternMethods[0];
    PsiMethod found = resolved.findMethodBySignature(patternMethod, true);
    if (found == null) return false;

    PsiClass aClass = found.getContainingClass();
    if (aClass == null) return false;
    String qname = aClass.getQualifiedName();
    if (GroovyCommonClassNames.GROOVY_OBJECT.equals(qname)) return false;
    if (GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT.equals(qname)) return false;

    return true;
  }

  private static void addEmptyIntentionIfNeeded(@Nullable HighlightInfo info) {
    if (info != null) {
      int s1 = info.quickFixActionMarkers != null ? info.quickFixActionMarkers.size() : 0;
      int s2 = info.quickFixActionRanges != null ? info.quickFixActionRanges.size() : 0;

      if (s1 + s2 == 0) {
        EmptyIntentionAction emptyIntentionAction = new EmptyIntentionAction("Access to unresolved expression");
        QuickFixAction.registerQuickFixAction(info, emptyIntentionAction, HighlightDisplayKey.find(SHORT_NAME));
      }
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
    if (referenceExpression.getParent() instanceof GrMethodCall || referenceExpression.getQualifier() != null) return false;
    GrMember context = PsiTreeUtil.getParentOfType(referenceExpression, GrMember.class, true, GrClosableBlock.class);
    return (context instanceof GrMethod || context instanceof GrClassInitializer) && context.hasModifierProperty(STATIC);
  }

  @Nullable
  private static HighlightInfo createAnnotationForRef(@NotNull GrReferenceExpression ref,
                                                      boolean compileStatic,
                                                      @Nullable final String message) {
    PsiElement refNameElement = ref.getReferenceNameElement();
    assert refNameElement != null;

    HighlightDisplayLevel displayLevel = getHighlightDisplayLevel(ref.getProject(), ref);

    if (compileStatic || displayLevel == HighlightDisplayLevel.ERROR) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, refNameElement, message);
    }

    if (displayLevel == HighlightDisplayLevel.WARNING) {
      boolean isTestMode = ApplicationManager.getApplication().isUnitTestMode();
      HighlightInfoType infotype = isTestMode ? HighlightInfoType.WARNING : HighlightInfoType.INFORMATION;

      return HighlightInfo.createHighlightInfo(infotype, refNameElement, message, UNRESOLVED_ACCESS_ATTRIBUTES);
    }

    HighlightInfoType highlightInfoType = HighlightInfo.convertSeverity(displayLevel.getSeverity());
    return HighlightInfo.createHighlightInfo(highlightInfoType, refNameElement, message);
  }

  private static void registerStaticImportFix(GrReferenceExpression referenceExpression, HighlightInfo info, final HighlightDisplayKey key) {
    final String referenceName = referenceExpression.getReferenceName();
    if (StringUtil.isEmpty(referenceName)) return;
    if (referenceExpression.getQualifier() != null) return;

    QuickFixAction.registerQuickFixAction(info, new GroovyStaticImportMethodFix((GrMethodCall)referenceExpression.getParent()), key);
  }

  private static void registerReferenceFixes(GrReferenceExpression refExpr,
                                             HighlightInfo info,
                                             boolean compileStatic, final HighlightDisplayKey key) {
    PsiClass targetClass = QuickfixUtil.findTargetClass(refExpr, compileStatic);
    if (targetClass == null) return;

    if (!compileStatic) {
      addDynamicAnnotation(info, refExpr, key);
    }
    if (targetClass.isWritable()) {
      QuickFixAction.registerQuickFixAction(info, new CreateFieldFromUsageFix(refExpr, targetClass), key);

      if (refExpr.getParent() instanceof GrCall && refExpr.getParent() instanceof GrExpression) {
        QuickFixAction.registerQuickFixAction(info, new CreateMethodFromUsageFix(refExpr, targetClass), key);
      }
    }

    if (!refExpr.isQualified()) {
      GrVariableDeclarationOwner owner = PsiTreeUtil.getParentOfType(refExpr, GrVariableDeclarationOwner.class);
      if (!(owner instanceof GroovyFileBase) || ((GroovyFileBase)owner).isScript()) {
        QuickFixAction.registerQuickFixAction(info, new CreateLocalVariableFromUsageFix(refExpr, owner), key);
      }
      if (PsiTreeUtil.getParentOfType(refExpr, GrMethod.class) != null) {
        QuickFixAction.registerQuickFixAction(info, new CreateParameterFromUsageFix(refExpr), key);
      }
    }
  }

  private static void addDynamicAnnotation(HighlightInfo info, GrReferenceExpression referenceExpression, HighlightDisplayKey key) {
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
        QuickFixAction.registerQuickFixAction(info, referenceExpression.getTextRange(),
                                              new DynamicMethodFix(referenceExpression, argumentTypes), key);
      }
    }
    else {
      QuickFixAction.registerQuickFixAction(info, referenceExpression.getTextRange(), new DynamicPropertyFix(referenceExpression), key);
    }
  }

  private static void registerAddImportFixes(GrReferenceElement refElement, HighlightInfo info, final HighlightDisplayKey key) {
    final String referenceName = refElement.getReferenceName();
    //noinspection ConstantConditions
    if (StringUtil.isEmpty(referenceName)) return;
    if (!(refElement instanceof GrCodeReferenceElement) && Character.isLowerCase(referenceName.charAt(0))) return;
    if (refElement.getQualifier() != null) return;

    QuickFixAction.registerQuickFixAction(info, new GroovyAddImportAction(refElement), key);
  }

  private static void registerCreateClassByTypeFix(GrReferenceElement refElement, HighlightInfo info, final HighlightDisplayKey key) {
    GrPackageDefinition packageDefinition = PsiTreeUtil.getParentOfType(refElement, GrPackageDefinition.class);
    if (packageDefinition != null) return;

    PsiElement parent = refElement.getParent();
    if (parent instanceof GrNewExpression &&
        refElement.getManager().areElementsEquivalent(((GrNewExpression)parent).getReferenceElement(), refElement)) {
      QuickFixAction.registerQuickFixAction(info, CreateClassFix.createClassFromNewAction((GrNewExpression)parent), key);
    }
    else {
      if (shouldBeInterface(refElement)) {
        QuickFixAction.registerQuickFixAction(info, CreateClassFix.createClassFixAction(refElement, CreateClassKind.INTERFACE), key);
      }
      else if (shouldBeClass(refElement)) {
        QuickFixAction.registerQuickFixAction(info, CreateClassFix.createClassFixAction(refElement, CreateClassKind.CLASS), key);
        QuickFixAction.registerQuickFixAction(info, CreateClassFix.createClassFixAction(refElement, CreateClassKind.ENUM), key);
      }
      else if (shouldBeAnnotation(refElement)) {
        QuickFixAction.registerQuickFixAction(info, CreateClassFix.createClassFixAction(refElement, CreateClassKind.ANNOTATION), key);
      }
      else {
        QuickFixAction.registerQuickFixAction(info, CreateClassFix.createClassFixAction(refElement, CreateClassKind.CLASS), key);
        QuickFixAction.registerQuickFixAction(info, CreateClassFix.createClassFixAction(refElement, CreateClassKind.INTERFACE),key);
        QuickFixAction.registerQuickFixAction(info, CreateClassFix.createClassFixAction(refElement, CreateClassKind.ENUM), key);
        QuickFixAction.registerQuickFixAction(info, CreateClassFix.createClassFixAction(refElement, CreateClassKind.ANNOTATION), key);
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
    private final HighlightInfo myInfo;
    private HighlightDisplayKey myKey;

    public QuickFixActionRegistrarAdapter(HighlightInfo info, HighlightDisplayKey displayKey) {
      myInfo = info;
      myKey = displayKey;
    }

    @Override
    public void register(IntentionAction action) {
      myKey = HighlightDisplayKey.find(SHORT_NAME);
      QuickFixAction.registerQuickFixAction(myInfo, action, myKey);
    }

    @Override
    public void register(TextRange fixRange, IntentionAction action, HighlightDisplayKey key) {
      QuickFixAction.registerQuickFixAction(myInfo, fixRange, action, key);
    }

    @Override
    public void unregister(Condition<IntentionAction> condition) {
      QuickFixAction.unregisterQuickFixAction(myInfo, condition);
    }
  }
}
