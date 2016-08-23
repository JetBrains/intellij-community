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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.scope.processor.VariablesProcessor;
import com.intellij.psi.util.*;
import com.intellij.refactoring.changeSignature.*;
import com.intellij.refactoring.rename.ResolveSnapshotProvider;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.codeStyle.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocParameterReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.refactoring.DefaultGroovyVariableNameValidator;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.List;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GrChangeSignatureUsageProcessor implements ChangeSignatureUsageProcessor {
  private static final Logger LOG =
    Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChangeSignatureUsageProcessor");

  @Override
  public UsageInfo[] findUsages(ChangeInfo info) {
    if (info instanceof JavaChangeInfo) {
      return new GrChageSignatureUsageSearcher((JavaChangeInfo)info).findUsages();
    }
    return UsageInfo.EMPTY_ARRAY;
  }

  @Override
  public MultiMap<PsiElement, String> findConflicts(ChangeInfo info, Ref<UsageInfo[]> refUsages) {
    if (info instanceof JavaChangeInfo) {
      return new GrChangeSignatureConflictSearcher((JavaChangeInfo)info).findConflicts(refUsages);
    }
    else {
      return new MultiMap<>();
    }
  }

  @Override
  public boolean processPrimaryMethod(ChangeInfo changeInfo) {
    if (!(changeInfo instanceof JavaChangeInfo)) return false;

    JavaChangeInfo info = (JavaChangeInfo)changeInfo;
    PsiMethod method = info.getMethod();
    if (!(method instanceof GrMethod)) return false;

    if (info.isGenerateDelegate()) {
      return generateDelegate(info);
    }

    return processPrimaryMethodInner(info, ((GrMethod)method), null);
  }

  @Override
  public boolean shouldPreviewUsages(ChangeInfo changeInfo, UsageInfo[] usages) {
    if (!StringUtil.isJavaIdentifier(changeInfo.getNewName())) return true;

    for (UsageInfo usage : usages) {
      if (usage instanceof GrMethodCallUsageInfo) {
        if (((GrMethodCallUsageInfo)usage).isPossibleUsage()) return true;
      }
    }
    return false;
  }

  @Override
  public boolean setupDefaultValues(ChangeInfo changeInfo, Ref<UsageInfo[]> refUsages, Project project) {
    if (!(changeInfo instanceof JavaChangeInfo)) return true;
    for (UsageInfo usageInfo : refUsages.get()) {
      if (usageInfo instanceof  GrMethodCallUsageInfo) {
        GrMethodCallUsageInfo methodCallUsageInfo = (GrMethodCallUsageInfo)usageInfo;
        if (methodCallUsageInfo.isToChangeArguments()){
          final PsiElement element = methodCallUsageInfo.getElement();
          if (element == null) continue;
          final PsiMethod caller = RefactoringUtil.getEnclosingMethod(element);
          final boolean needDefaultValue = !((JavaChangeInfo)changeInfo).getMethodsToPropagateParameters().contains(caller);
          final PsiMethod referencedMethod = methodCallUsageInfo.getReferencedMethod();
          if (needDefaultValue &&
              (caller == null || referencedMethod == null || !MethodSignatureUtil.isSuperMethod(referencedMethod, caller))) {
            final ParameterInfo[] parameters = changeInfo.getNewParameters();
            for (ParameterInfo parameter : parameters) {
              final String defaultValue = parameter.getDefaultValue();
              if (defaultValue == null && parameter.getOldIndex() == -1) {
                ((ParameterInfoImpl)parameter).setDefaultValue("");
                if (!ApplicationManager.getApplication().isUnitTestMode()) {
                  final PsiType type = ((ParameterInfoImpl)parameter).getTypeWrapper().getType(element);
                  final DefaultValueChooser chooser =
                    new DefaultValueChooser(project, parameter.getName(), PsiTypesUtil.getDefaultValueOfType(type));
                  if (chooser.showAndGet()) {
                    if (chooser.feelLucky()) {
                      parameter.setUseAnySingleVariable(true);
                    }
                    else {
                      ((ParameterInfoImpl)parameter).setDefaultValue(chooser.getDefaultValue());
                    }
                  }
                  else {
                    return false;
                  }
                }
              }
            }
          }
        }
      }
    }
    return true;
  }

  @Override
  public void registerConflictResolvers(List<ResolveSnapshotProvider.ResolveSnapshot> snapshots,
                                        @NotNull ResolveSnapshotProvider resolveSnapshotProvider,
                                        UsageInfo[] usages, ChangeInfo changeInfo) {
  }

  private static boolean generateDelegate(JavaChangeInfo grInfo) {
    final GrMethod method = (GrMethod)grInfo.getMethod();
    final PsiClass psiClass = method.getContainingClass();
    GrMethod newMethod = (GrMethod)method.copy();
    newMethod = (GrMethod)psiClass.addAfter(newMethod, method);
    StringBuilder buffer = new StringBuilder();
    buffer.append("\n");
    if (method.isConstructor()) {
      buffer.append("this");
    }
    else {
      if (!PsiType.VOID.equals(method.getReturnType())) {
        buffer.append("return ");
      }
      buffer.append(GrChangeSignatureUtil.getNameWithQuotesIfNeeded(grInfo.getNewName(), method.getProject()));
    }

    generateParametersForDelegateCall(grInfo, method, buffer);

    final GrCodeBlock codeBlock = GroovyPsiElementFactory.getInstance(method.getProject()).createMethodBodyFromText(buffer.toString());
    newMethod.setBlock(codeBlock);
    newMethod.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, false);

    CodeStyleManager.getInstance(method.getProject()).reformat(newMethod);
    return processPrimaryMethodInner(grInfo, method, null);
  }

  private static void generateParametersForDelegateCall(JavaChangeInfo grInfo, GrMethod method, StringBuilder buffer) {
    buffer.append("(");

    final GrParameter[] oldParameters = method.getParameterList().getParameters();
    final JavaParameterInfo[] parameters = grInfo.getNewParameters();

    String[] params = new String[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      JavaParameterInfo parameter = parameters[i];
      final int oldIndex = parameter.getOldIndex();
      if (oldIndex >= 0) {
        params[i] = oldParameters[oldIndex].getName();
      }
      else {
        params[i] = parameter.getDefaultValue();
      }
    }
    buffer.append(StringUtil.join(params, ","));
    buffer.append(");");
  }

  private static boolean processPrimaryMethodInner(JavaChangeInfo changeInfo, GrMethod method, @Nullable PsiMethod baseMethod) {
    if (changeInfo.isNameChanged()) {
      String newName = baseMethod == null ? changeInfo.getNewName() :
                       RefactoringUtil.suggestNewOverriderName(method.getName(), baseMethod.getName(), changeInfo.getNewName());
      if (newName != null && !newName.equals(method.getName())) {
        method.setName(changeInfo.getNewName());
      }
    }

    final GrModifierList modifierList = method.getModifierList();
    if (changeInfo.isVisibilityChanged()) {
      modifierList.setModifierProperty(changeInfo.getNewVisibility(), true);
    }

    PsiSubstitutor substitutor = baseMethod != null ? calculateSubstitutor(method, baseMethod) : PsiSubstitutor.EMPTY;

    final PsiMethod context = changeInfo.getMethod();
    GrTypeElement oldReturnTypeElement = method.getReturnTypeElementGroovy();
    if (changeInfo.isReturnTypeChanged()) {
      CanonicalTypes.Type newReturnType = changeInfo.getNewReturnType();
      if (newReturnType == null) {
        if (oldReturnTypeElement != null) {
          oldReturnTypeElement.delete();
          if (modifierList.getModifiers().length == 0) {
            modifierList.setModifierProperty(GrModifier.DEF, true);
          }
        }
      }
      else {
        PsiType type = newReturnType.getType(context, method.getManager());
        GrReferenceAdjuster.shortenAllReferencesIn(method.setReturnType(substitutor.substitute(type)));
        if (oldReturnTypeElement == null) {
          modifierList.setModifierProperty(GrModifier.DEF, false);
        }
      }
    }

    JavaParameterInfo[] newParameters = changeInfo.getNewParameters();
    final GrParameterList parameterList = method.getParameterList();
    GrParameter[] oldParameters = parameterList.getParameters();
    final PsiParameter[] oldBaseParams = baseMethod != null ? baseMethod.getParameterList().getParameters() : null;


    Set<GrParameter> toRemove = new HashSet<>(oldParameters.length);
    ContainerUtil.addAll(toRemove, oldParameters);

    GrParameter anchor = null;
    final GrDocComment docComment = method.getDocComment();
    final GrDocTag[] tags = docComment == null ? null : docComment.getTags();


    
    for (JavaParameterInfo newParameter : newParameters) {
      //if old parameter name differs from base method parameter name we don't change it
      final String newName;
      final int oldIndex = newParameter.getOldIndex();
      if (oldIndex >= 0 && oldBaseParams != null) {
        final String oldName = oldParameters[oldIndex].getName();
        if (oldName.equals(oldBaseParams[oldIndex].getName())) {
          newName = newParameter.getName();
        }
        else {
          newName = oldName;
        }
      }
      else {
        newName = newParameter.getName();
      }

      final GrParameter oldParameter = oldIndex >= 0 ? oldParameters[oldIndex] : null;

      if (docComment != null && oldParameter != null) {
        final String oldName = oldParameter.getName();
        for (GrDocTag tag : tags) {
          if ("@param".equals(tag.getName())) {
            final GrDocParameterReference parameterReference = tag.getDocParameterReference();
            if (parameterReference != null && oldName.equals(parameterReference.getText())) {
              parameterReference.handleElementRename(newName);
            }
          }
        }
      }

      GrParameter grParameter = createNewParameter(substitutor, context, parameterList, newParameter, newName);
      if (oldParameter != null) {
        grParameter.getModifierList().replace(oldParameter.getModifierList());
      }

      if ("def".equals(newParameter.getTypeText())) {
        grParameter.getModifierList().setModifierProperty(GrModifier.DEF, true);
      }
      else if (StringUtil.isEmpty(newParameter.getTypeText())) {
        grParameter.getModifierList().setModifierProperty(GrModifier.DEF, false);
      }

      anchor = (GrParameter)parameterList.addAfter(grParameter, anchor);
    }

    for (GrParameter oldParameter : toRemove) {
      oldParameter.delete();
    }
    JavaCodeStyleManager.getInstance(parameterList.getProject()).shortenClassReferences(parameterList);
    CodeStyleManager.getInstance(parameterList.getProject()).reformat(parameterList);

    if (changeInfo.isExceptionSetOrOrderChanged()) {
      final ThrownExceptionInfo[] infos = changeInfo.getNewExceptions();
      PsiClassType[] exceptionTypes = new PsiClassType[infos.length];
      for (int i = 0; i < infos.length; i++) {
        ThrownExceptionInfo info = infos[i];
        exceptionTypes[i] = (PsiClassType)info.createType(method, method.getManager());
      }

      PsiReferenceList thrownList = GroovyPsiElementFactory.getInstance(method.getProject()).createThrownList(exceptionTypes);
      thrownList = (PsiReferenceList)method.getThrowsList().replace(thrownList);
      JavaCodeStyleManager.getInstance(thrownList.getProject()).shortenClassReferences(thrownList);
      CodeStyleManager.getInstance(method.getProject()).reformat(method.getThrowsList());
    }
    return true;
  }

  private static GrParameter createNewParameter(@NotNull PsiSubstitutor substitutor,
                                                @NotNull PsiMethod context,
                                                @NotNull GrParameterList parameterList,
                                                @NotNull JavaParameterInfo newParameter,
                                                @NotNull String newName) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(parameterList.getProject());
    String typeText = newParameter.getTypeText();
    if (newParameter instanceof GrParameterInfo && (typeText.isEmpty() || "def".equals(typeText))) {
      return factory.createParameter(newName, null, getInitializer(newParameter), parameterList);
    }

    PsiType type = substitutor.substitute(newParameter.createType(context, parameterList.getManager()));
    return factory.createParameter(newName, type == null ? null : type.getCanonicalText(), getInitializer(newParameter), parameterList);
  }

  private static PsiSubstitutor calculateSubstitutor(PsiMethod derivedMethod, PsiMethod baseMethod) {
    PsiSubstitutor substitutor;
    if (derivedMethod.getManager().areElementsEquivalent(derivedMethod, baseMethod)) {
      substitutor = PsiSubstitutor.EMPTY;
    }
    else {
      final PsiClass baseClass = baseMethod.getContainingClass();
      final PsiClass derivedClass = derivedMethod.getContainingClass();
      if (baseClass != null && derivedClass != null && InheritanceUtil.isInheritorOrSelf(derivedClass, baseClass, true)) {
        final PsiSubstitutor superClassSubstitutor =
          TypeConversionUtil.getSuperClassSubstitutor(baseClass, derivedClass, PsiSubstitutor.EMPTY);
        final MethodSignature superMethodSignature = baseMethod.getSignature(superClassSubstitutor);
        final MethodSignature methodSignature = derivedMethod.getSignature(PsiSubstitutor.EMPTY);
        final PsiSubstitutor superMethodSubstitutor =
          MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature, superMethodSignature);
        substitutor = superMethodSubstitutor != null ? superMethodSubstitutor : superClassSubstitutor;
      }
      else {
        substitutor = PsiSubstitutor.EMPTY;
      }
    }
    return substitutor;
  }


  @Nullable
  private static <Type extends PsiElement, List extends PsiElement> Type getNextOfType(List parameterList,
                                                                                       PsiElement current,
                                                                                       Class<Type> type) {
    return current != null ? PsiTreeUtil.getNextSiblingOfType(current, type) : PsiTreeUtil.getChildOfType(parameterList, type);
  }

  @Nullable
  private static String getInitializer(JavaParameterInfo newParameter) {
    if (newParameter instanceof GrParameterInfo) return ((GrParameterInfo)newParameter).getDefaultInitializer();
    return null;
  }

  @Override
  public boolean processUsage(ChangeInfo changeInfo, UsageInfo usageInfo, boolean beforeMethodChange, UsageInfo[] usages) {
    if (!(changeInfo instanceof JavaChangeInfo)) return false;

    PsiElement element = usageInfo.getElement();
    if (element == null) return false;
    if (!GroovyLanguage.INSTANCE.equals(element.getLanguage())) return false;

    if (beforeMethodChange) {
      if (usageInfo instanceof OverriderUsageInfo) {
        processPrimaryMethodInner(((JavaChangeInfo)changeInfo), (GrMethod)((OverriderUsageInfo)usageInfo).getOverridingMethod(),
                                  ((OverriderUsageInfo)usageInfo).getBaseMethod());
      }
    }
    else {
      if (usageInfo instanceof GrMethodCallUsageInfo) {
        processMethodUsage(element, ((JavaChangeInfo)changeInfo), ((GrMethodCallUsageInfo)usageInfo).isToChangeArguments(),
                           ((GrMethodCallUsageInfo)usageInfo).isToCatchExceptions(),
                           ((GrMethodCallUsageInfo)usageInfo).getMapToArguments(), ((GrMethodCallUsageInfo)usageInfo).getSubstitutor());
        return true;
      }
      else if (usageInfo instanceof DefaultConstructorImplicitUsageInfo) {
        processConstructor(
          (GrMethod)((DefaultConstructorImplicitUsageInfo)usageInfo).getConstructor(),
          (JavaChangeInfo)changeInfo);
        return true;
      }
      else if (usageInfo instanceof NoConstructorClassUsageInfo) {
        processClassUsage((GrTypeDefinition)((NoConstructorClassUsageInfo)usageInfo).getPsiClass(), ((JavaChangeInfo)changeInfo));
        return true;
      }
      else if (usageInfo instanceof ChangeSignatureParameterUsageInfo) {
        String newName = ((ChangeSignatureParameterUsageInfo)usageInfo).newParameterName;
        ((PsiReference)element).handleElementRename(newName);
        return true;
      }
      else {
        PsiReference ref = element.getReference();
        if (ref != null && changeInfo.getMethod() != null) {
          ref.bindToElement(changeInfo.getMethod());
          return true;
        }
      }
    }
    return false;
  }

  private static void processClassUsage(GrTypeDefinition psiClass, JavaChangeInfo changeInfo) {
    String name = psiClass.getName();

    GrMethod constructor = GroovyPsiElementFactory.getInstance(psiClass.getProject())
      .createConstructorFromText(name, ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY, "{}", null);

    GrModifierList list = constructor.getModifierList();
    if (psiClass.hasModifierProperty(PsiModifier.PRIVATE)) list.setModifierProperty(PsiModifier.PRIVATE, true);
    if (psiClass.hasModifierProperty(PsiModifier.PROTECTED)) list.setModifierProperty(PsiModifier.PROTECTED, true);
    if (!list.hasExplicitVisibilityModifiers()) {
      list.setModifierProperty(GrModifier.DEF, true);
    }

    constructor = (GrMethod)psiClass.add(constructor);
    processConstructor(constructor, changeInfo);
  }

  private static void processConstructor(GrMethod constructor, JavaChangeInfo changeInfo) {
    final PsiClass containingClass = constructor.getContainingClass();
    final PsiClass baseClass = changeInfo.getMethod().getContainingClass();
    final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, containingClass, PsiSubstitutor.EMPTY);

    GrOpenBlock block = constructor.getBlock();
    GrConstructorInvocation invocation =
      GroovyPsiElementFactory.getInstance(constructor.getProject()).createConstructorInvocation("super()");
    invocation = (GrConstructorInvocation)block.addStatementBefore(invocation, getFirstStatement(block));
    processMethodUsage(invocation.getInvokedExpression(), changeInfo,
                       changeInfo.isParameterSetOrOrderChanged() || changeInfo.isParameterNamesChanged(),
                       changeInfo.isExceptionSetChanged(), GrClosureSignatureUtil.ArgInfo.<PsiElement>empty_array(), substitutor);
  }

  @Nullable
  private static GrStatement getFirstStatement(GrCodeBlock block) {
    GrStatement[] statements = block.getStatements();
    if (statements.length == 0) return null;
    return statements[0];
  }

  private static void processMethodUsage(PsiElement element,
                                         JavaChangeInfo changeInfo,
                                         boolean toChangeArguments,
                                         boolean toCatchExceptions,
                                         GrClosureSignatureUtil.ArgInfo<PsiElement>[] map,
                                         PsiSubstitutor substitutor) {
    if (map == null) return;
    if (changeInfo.isNameChanged()) {
      if (element instanceof GrReferenceElement) {
        element = ((GrReferenceElement)element).handleElementRename(changeInfo.getNewName());
      }
    }
    if (toChangeArguments) {
      JavaParameterInfo[] parameters = changeInfo.getNewParameters();
      GrArgumentList argumentList = PsiUtil.getArgumentsList(element);
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(element.getProject());
      if (argumentList == null) {
        if (element instanceof GrEnumConstant) {
          argumentList = factory.createArgumentList();
          argumentList = (GrArgumentList)element.add(argumentList);
        }
        else {
          return;
        }
      }
      Set<PsiElement> argsToDelete = new HashSet<>(map.length * 2);
      for (GrClosureSignatureUtil.ArgInfo<PsiElement> argInfo : map) {
        argsToDelete.addAll(argInfo.args);
      }

      GrExpression[] values = new GrExpression[parameters.length];
      for (int i = 0; i < parameters.length; i++) {
        JavaParameterInfo parameter = parameters[i];
        int index = parameter.getOldIndex();
        if (index >= 0) {
          argsToDelete.removeAll(map[index].args);
        }
        else {
          values[i] = createDefaultValue(factory, changeInfo, parameter, argumentList, substitutor);
        }
      }

      for (PsiElement arg : argsToDelete) {
        arg.delete();
      }


      boolean skipOptionals = false;
      PsiElement anchor = null; //PsiTreeUtil.getChildOfAnyType(argumentList, GrExpression.class, GrNamedArgument.class);
      for (int i = 0; i < parameters.length; i++) {
        JavaParameterInfo parameter = parameters[i];
        int index = parameter.getOldIndex();
        if (index >= 0) {
          GrClosureSignatureUtil.ArgInfo<PsiElement> argInfo = map[index];
          List<PsiElement> arguments = argInfo.args;
          if (argInfo.isMultiArg) { //arguments for Map and varArg
            if ((i != 0 || !(!arguments.isEmpty() && arguments.iterator().next() instanceof GrNamedArgument)) &&
                (i != parameters.length - 1 || !parameter.isVarargType())) {
              final PsiType type = parameter.createType(changeInfo.getMethod().getParameterList(), argumentList.getManager());
              final GrExpression arg = GroovyRefactoringUtil.generateArgFromMultiArg(substitutor, arguments, type, element.getProject());
              for (PsiElement argument : arguments) {
                argument.delete();
              }
              anchor = argumentList.addAfter(arg, anchor);
              JavaCodeStyleManager.getInstance(anchor.getProject()).shortenClassReferences(anchor);
            }
          }
          else {  //arguments for simple parameters
            if (arguments.size() == 1) { //arg exists
              PsiElement arg = arguments.iterator().next();
              if (i == parameters.length - 1 && parameter.isVarargType()) {
                if (arg instanceof GrSafeCastExpression) {
                  PsiElement expr = ((GrSafeCastExpression)arg).getOperand();
                  if (expr instanceof GrListOrMap && !((GrListOrMap)expr).isMap()) {
                    final PsiElement copy = expr.copy();
                    PsiElement[] newVarargs = ((GrListOrMap)copy).getInitializers();
                    for (PsiElement vararg : newVarargs) {
                      anchor = argumentList.addAfter(vararg, anchor);
                    }
                    arg.delete();
                    continue;
                  }
                }
              }

              PsiElement curArg = getNextOfType(argumentList, anchor, GrExpression.class);
              if (curArg == arg) {
                anchor = arg;
              }
              else {
                final PsiElement copy = arg.copy();
                anchor = argumentList.addAfter(copy, anchor);
                arg.delete();
              }
            }
            else { //arg is skipped. Parameter is optional
              skipOptionals = true;
            }
          }
        }
        else {
          if (skipOptionals && isParameterOptional(parameter)) continue;

          if (forceOptional(parameter)) {
            skipOptionals = true;
            continue;
          }
          try {
            final GrExpression value = values[i];
            if (i > 0 && (value == null || anchor == null)) {
              PsiElement comma = Factory.createSingleLeafElement(GroovyTokenTypes.mCOMMA, ",", 0, 1,
                                                                 SharedImplUtil.findCharTableByTree(argumentList.getNode()),
                                                                 argumentList.getManager()).getPsi();
              if (anchor == null) anchor = argumentList.getLeftParen();

              anchor = argumentList.addAfter(comma, anchor);
            }
            if (value != null) {
              anchor = argumentList.addAfter(value, anchor);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e.getMessage());
          }
        }
      }

      for (PsiElement arg : argsToDelete) {
        arg.delete();
      }

      GrCall call = GroovyRefactoringUtil.getCallExpressionByMethodReference(element);
      if (argumentList.getText().trim().isEmpty() && (call == null || !PsiImplUtil.hasClosureArguments(call))) {
        argumentList = argumentList.replaceWithArgumentList(factory.createArgumentList());
      }
      CodeStyleManager.getInstance(argumentList.getProject()).reformat(argumentList);
    }

    if (toCatchExceptions) {
      final ThrownExceptionInfo[] exceptionInfos = changeInfo.getNewExceptions();
      PsiClassType[] exceptions = getExceptions(exceptionInfos, element, element.getManager());
      fixExceptions(element, exceptions);
    }
  }

  @Nullable
  private static GrExpression createDefaultValue(GroovyPsiElementFactory factory,
                                                 JavaChangeInfo changeInfo,
                                                 JavaParameterInfo info,
                                                 final GrArgumentList list,
                                                 PsiSubstitutor substitutor) {
    if (info.isUseAnySingleVariable()) {
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(list.getProject()).getResolveHelper();
      final PsiType type = info.getTypeWrapper().getType(changeInfo.getMethod(), list.getManager());
      final VariablesProcessor processor = new VariablesProcessor(false) {
        @Override
        protected boolean check(PsiVariable var, ResolveState state) {
          if (var instanceof PsiField && !resolveHelper.isAccessible((PsiField)var, list, null)) return false;
          if (var instanceof GrVariable &&
              PsiUtil.isLocalVariable(var) &&
              list.getTextRange().getStartOffset() <= var.getTextRange().getStartOffset()) {
            return false;
          }
          if (PsiTreeUtil.isAncestor(var, list, false)) return false;
          final PsiType _type = var instanceof GrVariable ? ((GrVariable)var).getTypeGroovy() : var.getType();
          final PsiType varType = state.get(PsiSubstitutor.KEY).substitute(_type);
          return type.isAssignableFrom(varType);
        }

        @Override
        public boolean execute(@NotNull PsiElement pe, @NotNull ResolveState state) {
          super.execute(pe, state);
          return size() < 2;
        }
      };
      ResolveUtil.treeWalkUp(list, processor, false);
      if (processor.size() == 1) {
        final PsiVariable result = processor.getResult(0);
        return factory.createExpressionFromText(result.getName(), list);
      }
      if (processor.size() == 0) {
        final PsiClass parentClass = PsiTreeUtil.getParentOfType(list, PsiClass.class);
        if (parentClass != null) {
          PsiClass containingClass = parentClass;
          final Set<PsiClass> containingClasses = new HashSet<>();
          final PsiElementFactory jfactory = JavaPsiFacade.getElementFactory(list.getProject());
          while (containingClass != null) {
            if (type.isAssignableFrom(jfactory.createType(containingClass, PsiSubstitutor.EMPTY))) {
              containingClasses.add(containingClass);
            }
            containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class);
          }
          if (containingClasses.size() == 1) {
            return factory.createThisExpression(containingClasses.contains(parentClass) ? null : containingClasses.iterator().next());
          }
        }
      }
    }

    final PsiElement element = info.getActualValue(list.getParent(), substitutor);
    if (element instanceof GrExpression) {
      return (GrExpression)element;
    }
    final String value = info.getDefaultValue();
    return !StringUtil.isEmpty(value) ? factory.createExpressionFromText(value, list) : null;
  }

  protected static boolean forceOptional(JavaParameterInfo parameter) {
    return parameter instanceof GrParameterInfo && ((GrParameterInfo)parameter).forceOptional();
  }

  private static void fixExceptions(PsiElement element, PsiClassType[] exceptions) {
    if (exceptions.length == 0) return;
    final GroovyPsiElement context =
      PsiTreeUtil.getParentOfType(element, GrTryCatchStatement.class, GrClosableBlock.class, GrMethod.class, GroovyFile.class);
    if (context instanceof GrClosableBlock) {
      element = generateTryCatch(element, exceptions);
    }
    else if (context instanceof GrMethod) {
      final PsiClassType[] handledExceptions = ((GrMethod)context).getThrowsList().getReferencedTypes();
      final List<PsiClassType> psiClassTypes = filterOutExceptions(exceptions, context, handledExceptions);
      element = generateTryCatch(element, psiClassTypes.toArray(new PsiClassType[psiClassTypes.size()]));
    }
    else if (context instanceof GroovyFile) {
      element = generateTryCatch(element, exceptions);
    }
    else if (context instanceof GrTryCatchStatement) {
      final GrCatchClause[] catchClauses = ((GrTryCatchStatement)context).getCatchClauses();
      List<PsiClassType> referencedTypes = ContainerUtil.map(catchClauses, grCatchClause -> {
        final GrParameter grParameter = grCatchClause.getParameter();
        final PsiType type = grParameter != null ? grParameter.getType() : null;
        if (type instanceof PsiClassType) {
          return (PsiClassType)type;
        }
        else {
          return null;
        }
      });

      referencedTypes = ContainerUtil.skipNulls(referencedTypes);
      final List<PsiClassType> psiClassTypes =
        filterOutExceptions(exceptions, context, referencedTypes.toArray(new PsiClassType[referencedTypes.size()]));

      element = fixCatchBlock((GrTryCatchStatement)context, psiClassTypes.toArray(new PsiClassType[psiClassTypes.size()]));
    }

  //  CodeStyleManager.getInstance(element.getProject()).reformat(element);
  }

  private static PsiElement generateTryCatch(PsiElement element, PsiClassType[] exceptions) {
    if (exceptions.length == 0) return element;
    GrTryCatchStatement tryCatch =
      (GrTryCatchStatement)GroovyPsiElementFactory.getInstance(element.getProject()).createStatementFromText("try{} catch (Exception e){}");
    final GrStatement statement = PsiTreeUtil.getParentOfType(element, GrStatement.class);
    assert statement != null;
    tryCatch.getTryBlock().addStatementBefore(statement, null);
    tryCatch = (GrTryCatchStatement)statement.replace(tryCatch);
    tryCatch.getCatchClauses()[0].delete();
    fixCatchBlock(tryCatch, exceptions);
    return tryCatch;
  }

  private static PsiElement fixCatchBlock(GrTryCatchStatement tryCatch, PsiClassType[] exceptions) {
    if (exceptions.length == 0) return tryCatch;
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(tryCatch.getProject());

    final GrCatchClause[] clauses = tryCatch.getCatchClauses();
    List<String> restricted = ContainerUtil.map(clauses, grCatchClause -> {
      final GrParameter grParameter = grCatchClause.getParameter();
      return grParameter != null ? grParameter.getName() : null;
    });

    restricted = ContainerUtil.skipNulls(restricted);
    final DefaultGroovyVariableNameValidator nameValidator = new DefaultGroovyVariableNameValidator(tryCatch, restricted);

    GrCatchClause anchor = clauses.length == 0 ? null : clauses[clauses.length - 1];
    for (PsiClassType type : exceptions) {
      final String[] names = GroovyNameSuggestionUtil.suggestVariableNameByType(type, nameValidator);
      final GrCatchClause catchClause = factory.createCatchClause(type, names[0]);
      final GrStatement printStackTrace = factory.createStatementFromText(names[0] + ".printStackTrace()");
      catchClause.getBody().addStatementBefore(printStackTrace, null);
      anchor = tryCatch.addCatchClause(catchClause, anchor);
      JavaCodeStyleManager.getInstance(anchor.getProject()).shortenClassReferences(anchor);
    }
    return tryCatch;
  }

  private static List<PsiClassType> filterOutExceptions(PsiClassType[] exceptions,
                                                        final GroovyPsiElement context,
                                                        final PsiClassType[] handledExceptions) {
    return ContainerUtil.findAll(exceptions, o -> {
      if (!InheritanceUtil.isInheritor(o, CommonClassNames.JAVA_LANG_EXCEPTION)) return false;
      for (PsiClassType type : handledExceptions) {
        if (TypesUtil.isAssignableByMethodCallConversion(type, o, context)) return false;
      }
      return true;
    });
  }

  private static PsiClassType[] getExceptions(ThrownExceptionInfo[] infos, final PsiElement context, final PsiManager manager) {
    return ContainerUtil.map(infos, thrownExceptionInfo -> (PsiClassType)thrownExceptionInfo.createType(context, manager), new PsiClassType[infos.length]);
  }

  private static boolean isParameterOptional(JavaParameterInfo parameterInfo) {
    return parameterInfo instanceof GrParameterInfo && ((GrParameterInfo)parameterInfo).isOptional();
  }
}
