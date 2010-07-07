/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.*;
import com.intellij.refactoring.changeSignature.*;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.DefaultGroovyVariableNameValidator;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GrChangeSignatureUsageProcessor implements ChangeSignatureUsageProcessor {
  private static final Logger LOG =
    Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChangeSignatureUsageProcessor");

  public UsageInfo[] findUsages(ChangeInfo info) {
    if (info instanceof JavaChangeInfo) {
      return new GrChageSignatureUsageSearcher((JavaChangeInfo)info).findUsages();
    }
    return UsageInfo.EMPTY_ARRAY;
  }

  public MultiMap<PsiElement, String> findConflicts(ChangeInfo info, Ref<UsageInfo[]> refUsages) {
    if (info instanceof JavaChangeInfo) {
      return new GrChangeSignatureConflictSearcher((JavaChangeInfo)info).findConflicts(refUsages);
    }
    else {
      return new MultiMap<PsiElement, String>();
    }
  }

  public boolean processPrimaryMethod(ChangeInfo changeInfo) {
    if (!(changeInfo instanceof GrChangeInfoImpl)) return false;

    GrChangeInfoImpl grInfo = (GrChangeInfoImpl)changeInfo;
    GrMethod method = grInfo.getMethod();
    if (grInfo.isGenerateDelegate()) {
      return generateDelegate(grInfo);
    }

    return processPrimaryMethodInner(grInfo, method, null);
  }

  public boolean shouldPreviewUsages(ChangeInfo changeInfo, UsageInfo[] usages) {
    if (!StringUtil.isJavaIdentifier(changeInfo.getNewName())) return true;

    for (UsageInfo usage : usages) {
      if (usage instanceof GrMethodCallUsageInfo) {
        if (((GrMethodCallUsageInfo)usage).isPossibleUsage()) return true;
      }
    }
    return false;
  }

  private static boolean generateDelegate(GrChangeInfoImpl grInfo) {
    final GrMethod method = grInfo.getMethod();
    final PsiClass psiClass = method.getContainingClass();
    GrMethod newMethod = (GrMethod)method.copy();
    newMethod = (GrMethod)psiClass.addAfter(newMethod, method);
    StringBuffer buffer = new StringBuffer();
    buffer.append("\n");
    if (method.isConstructor()) {
      buffer.append("this");
    }
    else {
      if (!PsiType.VOID.equals(method.getReturnType())) {
        buffer.append("return ");
      }
      buffer.append(method.getName());
    }

    generateParametersForDelegateCall(grInfo, method, buffer);

    final GrCodeBlock codeBlock = GroovyPsiElementFactory.getInstance(method.getProject()).createMethodBodyFromText(buffer.toString());
    newMethod.setBlock(codeBlock);
    newMethod.getModifierList().setModifierProperty(GrModifier.ABSTRACT, false);

    CodeStyleManager.getInstance(method.getProject()).reformat(newMethod);
    return processPrimaryMethodInner(grInfo, method, null);
  }

  private static void generateParametersForDelegateCall(GrChangeInfoImpl grInfo, GrMethod method, StringBuffer buffer) {
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

  private static boolean processPrimaryMethodInner(JavaChangeInfo changeInfo, GrMethod method, PsiMethod baseMethod) {
    if (changeInfo.isNameChanged()) {
      String newName = baseMethod == null ? changeInfo.getNewName() :
                       RefactoringUtil.suggestNewOverriderName(method.getName(), baseMethod.getName(), changeInfo.getNewName());
      if (newName != null && !newName.equals(method.getName())) {
        method.setName(changeInfo.getNewName());
      }
    }

    if (changeInfo.isVisibilityChanged()) {
      method.getModifierList().setModifierProperty(changeInfo.getNewVisibility(), true);
    }

    PsiSubstitutor substitutor = baseMethod != null ? calculateSubstitutor(method, baseMethod) : PsiSubstitutor.EMPTY;

    final PsiMethod context = changeInfo.getMethod();
    if (changeInfo.isReturnTypeChanged()) {
      CanonicalTypes.Type newReturnType = changeInfo.getNewReturnType();
      GrTypeElement element = method.getReturnTypeElementGroovy();
      if (newReturnType == null) {
        if (element != null) {
          element.delete();
          GrModifierList modifierList = method.getModifierList();
          if (modifierList.getModifiers().length == 0) {
            modifierList.setModifierProperty(GrModifier.DEF, true);
          }
        }
      }
      else {
        PsiType type = newReturnType.getType(context, method.getManager());
        final PsiType oldReturnType = method.getReturnType();
        if (!TypesUtil
          .isAssignable(type, oldReturnType, context.getManager(), context.getResolveScope())) { //todo ask for replace covariant type
          method.setReturnType(substitutor.substitute(type));
        }
      }
    }

    JavaParameterInfo[] newParameters = changeInfo.getNewParameters();
    final GrParameterList parameterList = method.getParameterList();
    GrParameter[] oldParameters = parameterList.getParameters();

    Set<GrParameter> toRemove = new HashSet<GrParameter>(oldParameters.length);
    ContainerUtil.addAll(toRemove, oldParameters);

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(method.getProject());
    GrParameter anchor = null;
    for (JavaParameterInfo newParameter : newParameters) {
      PsiType type;
      if (newParameter instanceof GrParameterInfo && ((GrParameterInfo)newParameter).hasNoType()) {
        type = null;
      }
      else {
        type = substitutor.substitute(newParameter.createType(context, method.getManager()));
      }

      GrParameter grParameter = factory
        .createParameter(newParameter.getName(), type == null ? null : type.getCanonicalText(), getInitializer(newParameter),
                         parameterList);
      anchor = (GrParameter)parameterList.addAfter(grParameter, anchor);
    }

    for (GrParameter oldParameter : toRemove) {
      oldParameter.delete();
    }
    PsiUtil.shortenReferences(parameterList);
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
      PsiUtil.shortenReferences((GroovyPsiElement)thrownList);
      CodeStyleManager.getInstance(method.getProject()).reformat(method.getThrowsList());
    }
    return true;
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

  public boolean processUsage(ChangeInfo changeInfo, UsageInfo usageInfo, boolean beforeMethodChange, UsageInfo[] usages) {
    if (!(changeInfo instanceof JavaChangeInfo)) return false;

    PsiElement element = usageInfo.getElement();
    if (element == null) return false;
    if (!GroovyFileType.GROOVY_LANGUAGE.equals(element.getLanguage())) return false;

    if (beforeMethodChange) {
      if (usageInfo instanceof OverriderUsageInfo) {
        processPrimaryMethodInner(((JavaChangeInfo)changeInfo), (GrMethod)((OverriderUsageInfo)usageInfo).getElement(),
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

    GrConstructor constructor = ((GrConstructor)GroovyPsiElementFactory.getInstance(psiClass.getProject())
      .createConstructorFromText(name, ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY, "{}", null));

    GrModifierList list = constructor.getModifierList();
    if (psiClass.hasModifierProperty(GrModifier.PRIVATE)) list.setModifierProperty(GrModifier.PRIVATE, true);
    if (psiClass.hasModifierProperty(GrModifier.PROTECTED)) list.setModifierProperty(GrModifier.PROTECTED, true);
    if (!list.hasExplicitVisibilityModifiers()) {
      list.setModifierProperty(GrModifier.DEF, true);
    }

    constructor = (GrConstructor)psiClass.add(constructor);
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
    processMethodUsage(invocation.getThisOrSuperKeyword(), changeInfo,
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
      Set<PsiElement> argsToDelete = new HashSet<PsiElement>(map.length * 2);
      for (GrClosureSignatureUtil.ArgInfo<PsiElement> argInfo : map) {
        argsToDelete.addAll(argInfo.args);
      }

      for (JavaParameterInfo parameter : parameters) {
        int index = parameter.getOldIndex();
        if (index >= 0) {
          argsToDelete.removeAll(map[index].args);
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
            if ((i != 0 || !(arguments.size() > 0 && arguments.iterator().next() instanceof GrNamedArgument)) &&
                (i != parameters.length - 1 || !parameter.isVarargType())) {
              StringBuilder argText = new StringBuilder();
              argText.append("[");
              for (PsiElement argument : arguments) {
                argText.append(argument.getText()).append(", ");
                argument.delete();
              }
              argText.replace(argText.length() - 2, argText.length(), "]");
              PsiType type = parameter.createType(changeInfo.getMethod().getParameterList(), argumentList.getManager());
              if (type instanceof PsiArrayType) {
                type = substitutor.substitute(type);
                String typeText = type.getCanonicalText();
                if (type instanceof PsiEllipsisType) {
                  typeText = typeText.replace("...", "[]");
                }
                argText.append(" as ").append(typeText);
              }
              anchor = argumentList.addAfter(factory.createExpressionFromText(argText.toString()), anchor);
              PsiUtil.shortenReferences((GroovyPsiElement)anchor);
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
          GrExpression fromText = null;
          try {
            fromText = factory.createExpressionFromText(parameter.getDefaultValue());
          }
          catch (IncorrectOperationException e) {
            LOG.error(e.getMessage());
          }
          anchor = argumentList.addAfter(fromText, anchor);
        }
      }

      CodeStyleManager.getInstance(argumentList.getProject()).reformat(argumentList);
    }

    if (toCatchExceptions) {
      final ThrownExceptionInfo[] exceptionInfos = changeInfo.getNewExceptions();
      PsiClassType[] exceptions = getExceptions(exceptionInfos, element, element.getManager());
      fixExceptions(element, exceptions);
    }
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
      List<PsiClassType> referencedTypes = ContainerUtil.map(catchClauses, new Function<GrCatchClause, PsiClassType>() {
        @Nullable
        public PsiClassType fun(GrCatchClause grCatchClause) {
          final GrParameter grParameter = grCatchClause.getParameter();
          final PsiType type = grParameter != null ? grParameter.getType() : null;
          if (type instanceof PsiClassType) {
            return (PsiClassType)type;
          }
          else {
            return null;
          }
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
    List<String> restricted = ContainerUtil.map(clauses, new Function<GrCatchClause, String>() {
      @Nullable
      public String fun(GrCatchClause grCatchClause) {
        final GrParameter grParameter = grCatchClause.getParameter();
        return grParameter != null ? grParameter.getName() : null;
      }
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
      PsiUtil.shortenReferences(anchor);
    }
    return tryCatch;
  }

  private static List<PsiClassType> filterOutExceptions(PsiClassType[] exceptions,
                                                        final GroovyPsiElement context,
                                                        final PsiClassType[] handledExceptions) {
    return ContainerUtil.findAll(exceptions, new Condition<PsiClassType>() {
      public boolean value(PsiClassType o) {
        if (!InheritanceUtil.isInheritor(o, CommonClassNames.JAVA_LANG_EXCEPTION)) return false;
        for (PsiClassType type : handledExceptions) {
          if (TypesUtil.isAssignable(type, o, context.getManager(), context.getResolveScope(), false)) return false;
        }
        return true;
      }
    });
  }

  private static PsiClassType[] getExceptions(ThrownExceptionInfo[] infos, final PsiElement context, final PsiManager manager) {
    return ContainerUtil.map(infos, new Function<ThrownExceptionInfo, PsiClassType>() {
      @Nullable
      public PsiClassType fun(ThrownExceptionInfo thrownExceptionInfo) {
        return (PsiClassType)thrownExceptionInfo.createType(context, manager);
      }
    }, new PsiClassType[infos.length]);
  }

  private static boolean isParameterOptional(JavaParameterInfo parameterInfo) {
    return parameterInfo instanceof GrParameterInfo && ((GrParameterInfo)parameterInfo).isOptional();
  }
}
