/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CollectConsumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.GrHighlightUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.*;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicMethodFix;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicPropertyFix;
import org.jetbrains.plugins.groovy.extensions.GroovyUnresolvedHighlightFilter;
import org.jetbrains.plugins.groovy.findUsages.MissingMethodAndPropertyUtil;
import org.jetbrains.plugins.groovy.highlighter.DefaultHighlighter;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GroovyDocPsiElement;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrInterfaceDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceResolveUtil.*;

/**
 * Created by Max Medvedev on 21/03/14
 */
public class GrUnresolvedAccessChecker {
  public static final Logger LOG = Logger.getInstance(GrUnresolvedAccessChecker.class);

  private static final LightCacheKey<Map<String, Boolean>> GROOVY_OBJECT_METHODS_CACHE = new LightCacheKey<Map<String, Boolean>>() {
    @Override
    protected long getModificationCount(PsiElement holder) {
      return holder.getManager().getModificationTracker().getModificationCount();
    }
  };


  private final HighlightDisplayKey myDisplayKey;
  private final boolean myInspectionEnabled;
  private final GrUnresolvedAccessInspection myInspection;

  public GrUnresolvedAccessChecker(@NotNull GroovyFileBase file, @NotNull Project project) {
    myInspectionEnabled = GrUnresolvedAccessInspection.isInspectionEnabled(file, project);
    myInspection = myInspectionEnabled ? GrUnresolvedAccessInspection.getInstance(file, project) : null;
    myDisplayKey = GrUnresolvedAccessInspection.findDisplayKey();
  }

  @Nullable
  public HighlightInfo checkCodeReferenceElement(GrCodeReferenceElement refElement) {
    HighlightInfo info = checkCodeRefInner(refElement);
    addEmptyIntentionIfNeeded(info);
    return info;
  }

  @Nullable
  public List<HighlightInfo> checkReferenceExpression(GrReferenceExpression ref) {
    List<HighlightInfo> info = checkRefInner(ref);
    addEmptyIntentionIfNeeded(ContainerUtil.getFirstItem(info));
    return info;
  }

  @Nullable
  private HighlightInfo checkCodeRefInner(GrCodeReferenceElement refElement) {
    if (PsiTreeUtil.getParentOfType(refElement, GroovyDocPsiElement.class) != null) return null;

    PsiElement nameElement = refElement.getReferenceNameElement();
    if (nameElement == null) return null;

    if (isResolvedStaticImport(refElement)) return null;

    GroovyResolveResult resolveResult = refElement.advancedResolve();
    final PsiElement resolved = resolveResult.getElement();

    if (!(refElement.getParent() instanceof GrPackageDefinition) && resolved == null) {
      String message = GroovyBundle.message("cannot.resolve", refElement.getReferenceName());
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(nameElement).descriptionAndTooltip(message).create();

      // todo implement for nested classes
      registerCreateClassByTypeFix(refElement, info, myDisplayKey);
      registerAddImportFixes(refElement, info, myDisplayKey);
      UnresolvedReferenceQuickFixProvider
        .registerReferenceFixes(refElement, new QuickFixActionRegistrarAdapter(info, myDisplayKey));
      OrderEntryFix.registerFixes(new QuickFixActionRegistrarAdapter(info, myDisplayKey), refElement);

      return info;
    }

    if (refElement.getParent() instanceof GrNewExpression) {

      boolean inStaticContext = isInStaticContext(refElement);

      if (!inStaticContext && GrUnresolvedAccessInspection.isSuppressed(refElement)) return null;

      if (!inStaticContext) {
        if (!myInspectionEnabled) return null;
        assert myInspection != null;
        if (!myInspection.myHighlightInnerClasses) return null;
      }

      GrNewExpression newExpression = (GrNewExpression)refElement.getParent();
      if (resolved instanceof PsiClass) {
        PsiClass clazz = (PsiClass)resolved;
        if (newExpression.getQualifier() == null) {
          final PsiClass outerClass = clazz.getContainingClass();
          if (com.intellij.psi.util.PsiUtil.isInnerClass(clazz) &&
              outerClass != null &&
              newExpression.getArgumentList() != null &&
              !PsiUtil.hasEnclosingInstanceInScope(outerClass, newExpression, true) &&
              !hasEnclosingInstanceInArgList(newExpression.getArgumentList(), outerClass)) {
            String qname = clazz.getQualifiedName();
            LOG.assertTrue(qname != null, clazz.getText());
            return createAnnotationForRef(refElement, inStaticContext, GroovyBundle.message("cannot.reference.non.static", qname));
          }
        }
      }
    }

    return null;
  }

  private static boolean hasEnclosingInstanceInArgList(@NotNull GrArgumentList list, @NotNull PsiClass enclosingClass) {
    if (PsiImplUtil.hasNamedArguments(list)) return false;

    GrExpression[] args = list.getExpressionArguments();
    if (args.length == 0) return false;

    PsiType type = args[0].getType();
    PsiClassType enclosingClassType = JavaPsiFacade.getElementFactory(list.getProject()).createType(enclosingClass);
    return TypesUtil.isAssignableByMethodCallConversion(enclosingClassType, type, list);
  }

  @Nullable
  private List<HighlightInfo> checkRefInner(GrReferenceExpression ref) {
    PsiElement refNameElement = ref.getReferenceNameElement();
    if (refNameElement == null) return null;

    boolean inStaticContext = PsiUtil.isCompileStatic(ref) || isPropertyAccessInStaticMethod(ref);
    GroovyResolveResult resolveResult = getBestResolveResult(ref);

    if (resolveResult.getElement() != null) {
      if (!GrUnresolvedAccessInspection.isInspectionEnabled(ref.getContainingFile(), ref.getProject())) return null;

      if (!isStaticOk(resolveResult)) {
        String message = GroovyBundle.message("cannot.reference.non.static", ref.getReferenceName());
        return Collections.singletonList(createAnnotationForRef(ref, inStaticContext, message));
      }

      return null;
    }

    if (ResolveUtil.isKeyOfMap(ref) || isClassReference(ref)) {
      return null;
    }

    if (!inStaticContext) {
      if (!GrUnresolvedAccessInspection.isInspectionEnabled(ref.getContainingFile(), ref.getProject())) return null;
      assert myInspection != null;

      if (!myInspection.myHighlightIfGroovyObjectOverridden && areGroovyObjectMethodsOverridden(ref)) return null;
      if (!myInspection.myHighlightIfMissingMethodsDeclared && areMissingMethodsDeclared(ref)) return null;

      if (GrUnresolvedAccessInspection.isSuppressed(ref)) return null;
    }

    if (inStaticContext || shouldHighlightAsUnresolved(ref)) {
      HighlightInfo info = createAnnotationForRef(ref, inStaticContext, GroovyBundle.message("cannot.resolve", ref.getReferenceName()));
      if (info == null) return null;

      ArrayList<HighlightInfo> result = ContainerUtil.newArrayList();
      result.add(info);
      if (ref.getParent() instanceof GrMethodCall) {
        ContainerUtil.addIfNotNull(result, registerStaticImportFix(ref, myDisplayKey));
      }
      else {
        registerCreateClassByTypeFix(ref, info, myDisplayKey);
        registerAddImportFixes(ref, info, myDisplayKey);
      }

      registerReferenceFixes(ref, info, inStaticContext, myDisplayKey);
      UnresolvedReferenceQuickFixProvider.registerReferenceFixes(ref, new QuickFixActionRegistrarAdapter(info, myDisplayKey));
      OrderEntryFix.registerFixes(new QuickFixActionRegistrarAdapter(info, myDisplayKey), ref);
      return result;
    }

    return null;
  }

  private static boolean areMissingMethodsDeclared(GrReferenceExpression ref) {
    PsiType qualifierType = getQualifierType(ref);
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
    PsiMethod patternMethod = findPatternMethod(ref);
    if (patternMethod == null) return false;

    GrExpression qualifier = ref.getQualifier();
    if (qualifier != null) {
      return checkGroovyObjectMethodsByQualifier(ref, patternMethod);
    }
    else {
      return checkMethodInPlace(ref, patternMethod);
    }
  }

  private static boolean checkMethodInPlace(GrReferenceExpression ref, PsiMethod patternMethod) {
    PsiElement container = PsiTreeUtil.getParentOfType(ref, GrClosableBlock.class, PsiMember.class, PsiFile.class);
    assert container != null;
    return checkContainer(patternMethod, container);
  }

  private static boolean checkContainer(@NotNull final PsiMethod patternMethod, @NotNull PsiElement container) {
    final String name = patternMethod.getName();

    Map<String, Boolean> cached = GROOVY_OBJECT_METHODS_CACHE.getCachedValue(container);
    if (cached == null) {
      GROOVY_OBJECT_METHODS_CACHE.putCachedValue(container, cached = ContainerUtil.newConcurrentMap());
    }

    Boolean cachedResult = cached.get(name);
    if (cachedResult != null) {
      return cachedResult.booleanValue();
    }

    boolean result = doCheckContainer(patternMethod, container, name);
    cached.put(name, result);

    return result;
  }

  private static boolean doCheckContainer(final PsiMethod patternMethod, PsiElement container, final String name) {
    final Ref<Boolean> result = new Ref<Boolean>(false);
    PsiScopeProcessor processor = new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (element instanceof PsiMethod &&
            name.equals(((PsiMethod)element).getName()) &&
            patternMethod.getParameterList().getParametersCount() == ((PsiMethod)element).getParameterList().getParametersCount() &&
            isNotFromGroovyObject((PsiMethod)element)) {
          result.set(true);
          return false;
        }
        return true;
      }

      @Nullable
      @Override
      public <T> T getHint(@NotNull Key<T> hintKey) {
        return null;
      }

      @Override
      public void handleEvent(@NotNull Event event, @Nullable Object associated) {
      }
    };
    ResolveUtil.treeWalkUp(container, processor, true);
    return result.get();
  }

  private static boolean checkGroovyObjectMethodsByQualifier(GrReferenceExpression ref, PsiMethod patternMethod) {
    PsiType qualifierType = getQualifierType(ref);
    if (!(qualifierType instanceof PsiClassType)) return false;

    PsiClass resolved = ((PsiClassType)qualifierType).resolve();
    if (resolved == null) return false;

    PsiMethod found = resolved.findMethodBySignature(patternMethod, true);
    if (found == null) return false;

    return isNotFromGroovyObject(found);
  }

  private static boolean isNotFromGroovyObject(@NotNull PsiMethod found) {
    PsiClass aClass = found.getContainingClass();
    if (aClass == null) return false;
    String qname = aClass.getQualifiedName();
    if (GroovyCommonClassNames.GROOVY_OBJECT.equals(qname)) return false;
    if (GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT.equals(qname)) return false;
    return true;
  }

  @Nullable
  private static PsiMethod findPatternMethod(@NotNull GrReferenceExpression ref) {
    PsiClass groovyObject = GroovyPsiManager.getInstance(ref.getProject()).findClassWithCache(GroovyCommonClassNames.GROOVY_OBJECT,
                                                                                              ref.getResolveScope());
    if (groovyObject == null) return null;

    String methodName = ref.getParent() instanceof GrCall ? "invokeMethod"
                        : PsiUtil.isLValue(ref)           ? "setProperty"
                                                          : "getProperty";

    PsiMethod[] patternMethods = groovyObject.findMethodsByName(methodName, false);
    if (patternMethods.length != 1) return null;
    return patternMethods[0];
  }

  private void addEmptyIntentionIfNeeded(@Nullable HighlightInfo info) {
    if (info != null) {
      int s1 = info.quickFixActionMarkers != null ? info.quickFixActionMarkers.size() : 0;
      int s2 = info.quickFixActionRanges != null ? info.quickFixActionRanges.size() : 0;

      if (s1 + s2 == 0) {
        EmptyIntentionAction emptyIntentionAction = new EmptyIntentionAction(GrUnresolvedAccessInspection.getDisplayText());
        QuickFixAction.registerQuickFixAction(info, emptyIntentionAction, myDisplayKey);
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

    return ((PsiModifierListOwner)resolved).hasModifierProperty(PsiModifier.STATIC);
  }

  @NotNull
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

  @Nullable
  private static HighlightInfo createAnnotationForRef(@NotNull GrReferenceElement ref,
                                                      boolean strongError,
                                                      @NotNull String message) {
    PsiElement refNameElement = ref.getReferenceNameElement();
    assert refNameElement != null;

    if (strongError) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refNameElement).descriptionAndTooltip(message).create();
    }

    HighlightDisplayLevel displayLevel = GrUnresolvedAccessInspection.getHighlightDisplayLevel(ref.getProject(), ref);

    if (displayLevel == HighlightDisplayLevel.ERROR) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refNameElement).descriptionAndTooltip(message).create();
    }

    if (displayLevel == HighlightDisplayLevel.WEAK_WARNING) {
      boolean isTestMode = ApplicationManager.getApplication().isUnitTestMode();
      HighlightInfoType infotype = isTestMode ? HighlightInfoType.WARNING : HighlightInfoType.INFORMATION;

      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(infotype).range(refNameElement);
      builder.descriptionAndTooltip(message);
      return builder.needsUpdateOnTyping(false).textAttributes(DefaultHighlighter.UNRESOLVED_ACCESS).create();
    }

    HighlightInfoType highlightInfoType = HighlightInfo.convertSeverity(displayLevel.getSeverity());
    return HighlightInfo.newHighlightInfo(highlightInfoType).range(refNameElement).descriptionAndTooltip(message).create();
  }

  @Nullable
  private static HighlightInfo registerStaticImportFix(@NotNull GrReferenceExpression referenceExpression,
                                                       @Nullable final HighlightDisplayKey key) {
    final String referenceName = referenceExpression.getReferenceName();
    if (StringUtil.isEmpty(referenceName)) return null;
    if (referenceExpression.getQualifier() != null) return null;

    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(
      referenceExpression.getParent()).createUnconditionally();
    QuickFixAction.registerQuickFixAction(info, new GroovyStaticImportMethodFix((GrMethodCall)referenceExpression.getParent()), key);
    return info;
  }

  private static void registerReferenceFixes(GrReferenceExpression refExpr,
                                             HighlightInfo info,
                                             boolean compileStatic,
                                             final HighlightDisplayKey key) {
    PsiClass targetClass = QuickfixUtil.findTargetClass(refExpr, compileStatic);
    if (targetClass == null) return;

    if (!(targetClass instanceof SyntheticElement) || (targetClass instanceof GroovyScriptClass)) {
      if (!compileStatic) {
        addDynamicAnnotation(info, refExpr, key);
      }

      QuickFixAction.registerQuickFixAction(info, new CreateFieldFromUsageFix(refExpr), key);

      if (PsiUtil.isAccessedForReading(refExpr)) {
        QuickFixAction.registerQuickFixAction(info, new CreateGetterFromUsageFix(refExpr, targetClass), key);
      }
      if (PsiUtil.isLValue(refExpr)) {
        QuickFixAction.registerQuickFixAction(info, new CreateSetterFromUsageFix(refExpr), key);
      }

      if (refExpr.getParent() instanceof GrCall && refExpr.getParent() instanceof GrExpression) {
        QuickFixAction.registerQuickFixAction(info, new CreateMethodFromUsageFix(refExpr), key);
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
    if (containingFile != null) {
      VirtualFile file = containingFile.getVirtualFile();
      if (file == null) return;
    }
    else {
      return;
    }

    if (QuickfixUtil.isCall(referenceExpression)) {
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

  private static void registerAddImportFixes(GrReferenceElement refElement, @Nullable HighlightInfo info, final HighlightDisplayKey key) {
    final String referenceName = refElement.getReferenceName();
    //noinspection ConstantConditions
    if (StringUtil.isEmpty(referenceName)) return;
    if (!(refElement instanceof GrCodeReferenceElement) && Character.isLowerCase(referenceName.charAt(0))) return;
    if (refElement.getQualifier() != null) return;

    QuickFixAction.registerQuickFixAction(info, new GroovyAddImportAction(refElement), key);
  }

  private static void registerCreateClassByTypeFix(@NotNull GrReferenceElement refElement,
                                                   @Nullable HighlightInfo info,
                                                   final HighlightDisplayKey key) {
    GrPackageDefinition packageDefinition = PsiTreeUtil.getParentOfType(refElement, GrPackageDefinition.class);
    if (packageDefinition != null) return;

    PsiElement parent = refElement.getParent();
    if (parent instanceof GrNewExpression &&
        refElement.getManager().areElementsEquivalent(((GrNewExpression)parent).getReferenceElement(), refElement)) {
      QuickFixAction.registerQuickFixAction(info, CreateClassFix.createClassFromNewAction((GrNewExpression)parent), key);
    }
    else if (canBeClassOrPackage(refElement)) {
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
        QuickFixAction.registerQuickFixAction(info, CreateClassFix.createClassFixAction(refElement, CreateClassKind.INTERFACE), key);
        QuickFixAction.registerQuickFixAction(info, CreateClassFix.createClassFixAction(refElement, CreateClassKind.ENUM), key);
        QuickFixAction.registerQuickFixAction(info, CreateClassFix.createClassFixAction(refElement, CreateClassKind.ANNOTATION), key);
      }
    }
  }

  private static boolean canBeClassOrPackage(@NotNull GrReferenceElement refElement) {
    return !(refElement instanceof GrReferenceExpression) || ResolveUtil.canBeClassOrPackage((GrReferenceExpression)refElement);
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
      if (!consumer.getResult().isEmpty()) return false;
    }

    return true;
  }

  private static boolean isRefToPackage(GrExpression expr) {
    return expr instanceof GrReferenceExpression && ((GrReferenceExpression)expr).resolve() instanceof PsiPackage;
  }
}
