/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.light.JavaIdentifier;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.TIntStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.extensions.GroovyApplicabilityProvider;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.EmptyGroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrSpreadArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrAssertStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrPropertySelection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.*;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrNamedArgumentsOwner;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;

import java.util.*;

import static com.intellij.psi.GenericsUtil.isTypeArgumentsApplicable;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.DEFAULT_INSTANCE_EXTENSIONS;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.DEFAULT_STATIC_EXTENSIONS;

/**
 * @author ven
 */
public class PsiUtil {
  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil");
  public static final Key<JavaIdentifier> NAME_IDENTIFIER = new Key<>("Java Identifier");
  public static final Set<String> OPERATOR_METHOD_NAMES = ContainerUtil.newHashSet(
    "plus", "minus", "multiply", "power", "div", "mod", "or", "and", "xor", "next", "previous", "getAt", "putAt", "leftShift", "rightShift",
    "isCase", "bitwiseNegate", "negative", "positive", "call"
  );

  private PsiUtil() {
  }

  @Nullable
  public static String getMethodName(GrMethodCall methodCall) {
    GrExpression invokedExpression = methodCall.getInvokedExpression();
    if (!(invokedExpression instanceof GrReferenceExpression)) return null;

    return ((GrReferenceExpression)invokedExpression).getReferenceName();
  }

  @Nullable
  public static String getUnqualifiedMethodName(GrMethodCall methodCall) {
    GrExpression invokedExpression = methodCall.getInvokedExpression();
    if (!(invokedExpression instanceof GrReferenceExpression)) return null;

    if (((GrReferenceExpression)invokedExpression).isQualified()) return null;

    return ((GrReferenceExpression)invokedExpression).getReferenceName();
  }

  @Nullable
  public static String getQualifiedReferenceText(GrCodeReferenceElement referenceElement) {
    StringBuilder builder = new StringBuilder();
    if (!appendName(referenceElement, builder)) return null;

    return builder.toString();
  }

  private static boolean appendName(GrCodeReferenceElement referenceElement, StringBuilder builder) {
    String refName = referenceElement.getReferenceName();
    if (refName == null) return false;
    GrCodeReferenceElement qualifier = referenceElement.getQualifier();
    if (qualifier != null) {
      appendName(qualifier, builder);
      builder.append(".");
    }

    builder.append(refName);
    return true;
  }

  public static boolean isLValue(GroovyPsiElement element) {
    if (element instanceof GrExpression) {
      PsiElement parent = PsiTreeUtil.skipParentsOfType(element, GrParenthesizedExpression.class);
      if (parent instanceof GrTuple) return true;
      return parent instanceof GrAssignmentExpression &&
             PsiTreeUtil.isAncestor(((GrAssignmentExpression)parent).getLValue(), element, false);
    }
    return false;
  }

  public static boolean isLValueOfOperatorAssignment(@NotNull GrReferenceExpression element) {
    PsiElement parent = PsiTreeUtil.skipParentsOfType(element, GrParenthesizedExpression.class);
    return parent instanceof GrAssignmentExpression
           && ((GrAssignmentExpression)parent).getOperationTokenType() != GroovyTokenTypes.mASSIGN
           && PsiTreeUtil.isAncestor(((GrAssignmentExpression)parent).getLValue(), element, false);
  }

  public static boolean isApplicable(@Nullable PsiType[] argumentTypes,
                                     @NotNull PsiMethod method,
                                     PsiSubstitutor substitutor,
                                     PsiElement place,
                                     final boolean eraseParameterTypes) {
    return isApplicableConcrete(argumentTypes, method, substitutor, place, eraseParameterTypes) !=
           GrClosureSignatureUtil.ApplicabilityResult.inapplicable;
  }

  public static GrClosureSignatureUtil.ApplicabilityResult isApplicableConcrete(@Nullable PsiType[] argumentTypes,
                                                                                @NotNull PsiMethod method,
                                                                                PsiSubstitutor substitutor,
                                                                                PsiElement place,
                                                                                final boolean eraseParameterTypes) {
    if (argumentTypes == null) return GrClosureSignatureUtil.ApplicabilityResult.canBeApplicable;
    GrClosureSignature signature = GrClosureSignatureUtil.createSignature(method, substitutor, eraseParameterTypes);

    GrClosureSignatureUtil.ApplicabilityResult result =
      GroovyApplicabilityProvider.checkProviders(argumentTypes, method, substitutor, place, eraseParameterTypes);
    if (result != null) return result;

   result = GrClosureSignatureUtil.isSignatureApplicableConcrete(signature, argumentTypes, place);
    if (result != GrClosureSignatureUtil.ApplicabilityResult.inapplicable &&
        isTypeArgumentsApplicable(method.getTypeParameters(), substitutor, place)) {
      return result;
    }

    if (method instanceof GrBuilderMethod && !((GrBuilderMethod)method).hasObligatoryNamedArguments()) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length > 0 && parameters[0].getType() instanceof GrMapType &&
          (argumentTypes.length == 0 || !(argumentTypes[0] instanceof GrMapType))) {
        return GrClosureSignatureUtil.isSignatureApplicableConcrete(GrClosureSignatureUtil.removeParam(signature, 0), argumentTypes, place);
      }
    }
    return GrClosureSignatureUtil.ApplicabilityResult.inapplicable;
  }

  public static boolean isApplicable(@Nullable PsiType[] argumentTypes,
                                     GrClosureType type,
                                     GroovyPsiElement context) {
    return isApplicableConcrete(argumentTypes, type, context) != GrClosureSignatureUtil.ApplicabilityResult.inapplicable;
  }

  public static GrClosureSignatureUtil.ApplicabilityResult isApplicableConcrete(@Nullable PsiType[] argumentTypes,
                                                                                GrClosureType type,
                                                                                GroovyPsiElement context) {
    if (argumentTypes == null) return GrClosureSignatureUtil.ApplicabilityResult.canBeApplicable;

    GrSignature signature = type.getSignature();
    return GrClosureSignatureUtil.isSignatureApplicableConcrete(signature, argumentTypes, context);
  }

  @Nullable
  public static GrArgumentList getArgumentsList(@Nullable PsiElement methodRef) {
    if (methodRef == null) return null;

    if (methodRef instanceof GrEnumConstant) return ((GrEnumConstant)methodRef).getArgumentList();
    PsiElement parent = methodRef.getParent();
    if (parent instanceof GrCall) {
      return ((GrCall)parent).getArgumentList();
    }
    if (parent instanceof GrAnonymousClassDefinition) {
      return ((GrAnonymousClassDefinition)parent).getArgumentListGroovy();
    }
    return null;
  }

  @Nullable
  public static PsiType[] getArgumentTypes(@Nullable PsiElement place, boolean nullAsBottom) {
    return getArgumentTypes(place, nullAsBottom, null);
  }

  @Nullable
  public static PsiType[] getArgumentTypes(@Nullable PsiElement place,
                                           boolean nullAsBottom,
                                           @Nullable GrExpression stopAt) {
    PsiElement parent = place instanceof GrEnumConstant ? place : place != null ? place.getParent() : null;

    if (parent instanceof GrIndexProperty) {
      GrIndexProperty index = (GrIndexProperty)parent;
      GrArgumentList list = index.getArgumentList();
      PsiType[] argTypes = getArgumentTypes(list.getNamedArguments(), list.getExpressionArguments(), GrClosableBlock.EMPTY_ARRAY, nullAsBottom, stopAt);
      if (isLValue(index) && argTypes != null) {
        PsiType rawInitializer = TypeInferenceHelper.getInitializerTypeFor(index);

        PsiType initializer = notNullizeType(rawInitializer, nullAsBottom, index);
        return ArrayUtil.append(argTypes, initializer);
      }
      else {
        return argTypes;
      }
    }
    if (parent instanceof GrCall) {
      GrCall call = (GrCall)parent;
      GrNamedArgument[] namedArgs = call.getNamedArguments();
      GrExpression[] expressions = call.getExpressionArguments();
      GrClosableBlock[] closures = call.getClosureArguments();

      return getArgumentTypes(namedArgs, expressions, closures, nullAsBottom, stopAt);
    }
    else if (parent instanceof GrAnonymousClassDefinition) {
      final GrArgumentList argList = ((GrAnonymousClassDefinition)parent).getArgumentListGroovy();
      if (argList == null) {
        return getArgumentTypes(GrNamedArgument.EMPTY_ARRAY, GrExpression.EMPTY_ARRAY, GrClosableBlock.EMPTY_ARRAY, nullAsBottom, stopAt
        );
      }
      else {
        return getArgumentTypes(argList.getNamedArguments(), argList.getExpressionArguments(), GrClosableBlock.EMPTY_ARRAY, nullAsBottom, stopAt
        );
      }
    }
    else if (parent instanceof GrBinaryExpression) {
      GrExpression right = ((GrBinaryExpression)parent).getRightOperand();
      PsiType type = right != null ? right.getType() : null;
      return new PsiType[]{notNullizeType(type, nullAsBottom, parent)};
    } else if (parent instanceof GrAssignmentExpression) {
      PsiType type = ((GrAssignmentExpression)parent).getType();
      return new PsiType[]{notNullizeType(type, nullAsBottom, parent)};
    }

    return null;
  }

  @Contract("_, false, _ -> !null; !null, _, _ -> !null; null, true, _ -> null")
  private static PsiType notNullizeType(PsiType type, boolean acceptNull, PsiElement context) {
    return type != null || acceptNull ? type : TypesUtil.getJavaLangObject(context);
  }

  @Nullable
  public static PsiType[] getArgumentTypes(GrArgumentList argList) {
    return getArgumentTypes(argList, false, null);
  }

  @Nullable
  public static PsiType[] getArgumentTypes(@NotNull GrNamedArgument[] namedArgs,
                                           @NotNull GrExpression[] expressions,
                                           @NotNull GrClosableBlock[] closures,
                                           boolean nullAsBottom,
                                           @Nullable GrExpression stopAt) {
    List<PsiType> result = new ArrayList<>();

    if (namedArgs.length > 0) {
      GrNamedArgument context = namedArgs[0];
      result.add(GrMapType.createFromNamedArgs(context, namedArgs));
    }

    for (GrExpression expression : expressions) {
      PsiType type = expression.getType();
      if (expression instanceof GrSpreadArgument) {
        if (type instanceof GrTupleType) {
          result.addAll(Arrays.asList(((GrTupleType)type).getComponentTypes()));
        }
        else {
          return null;
        }
      }
      else {
        if (type == null) {
          result.add(nullAsBottom ? null : TypesUtil.getJavaLangObject(expression));
        }
        else {
          if (stopAt == expression) {
            type = TypeConversionUtil.erasure(type);
          }
          result.add(type);
        }
      }

      if (stopAt == expression) {
        return result.toArray(PsiType.createArray(result.size()));
      }
    }

    for (GrClosableBlock closure : closures) {
      PsiType closureType = closure.getType();
      if (closureType != null) {
        if (stopAt == closure) {
          closureType = TypeConversionUtil.erasure(closureType);
        }
        result.add(notNullizeType(closureType, nullAsBottom, closure));
      }
      if (stopAt == closure) {
        break;
      }
    }

    return result.toArray(PsiType.createArray(result.size()));
  }

  @Nullable
  public static PsiClass getJavaLangClass(PsiElement resolved, GlobalSearchScope scope) {
    return JavaPsiFacade.getInstance(resolved.getProject()).findClass(CommonClassNames.JAVA_LANG_CLASS, scope);
  }

  public static boolean isValidReferenceName(@NotNull String text) {
    final GroovyLexer lexer = new GroovyLexer();
    lexer.start(text);
    return TokenSets.REFERENCE_NAMES_WITHOUT_NUMBERS.contains(lexer.getTokenType()) && lexer.getTokenEnd() == text.length();
  }

  public static boolean isAccessible(@NotNull PsiElement place, @NotNull PsiMember member) {
    if (member instanceof LightElement) {
      return true;
    }

    if (place instanceof GrReferenceExpression && ((GrReferenceExpression)place).getQualifierExpression() == null) {
      if (member.getContainingClass() instanceof GroovyScriptClass) { //calling top level script members from the same script file
        return true;
      }
    }

    if (PsiTreeUtil.getParentOfType(place, GrDocComment.class) != null) return true;

    return com.intellij.psi.util.PsiUtil.isAccessible(member, place, null);
  }

  public static void reformatCode(final PsiElement element) {
    final TextRange textRange = element.getTextRange();

    PsiFile file = element.getContainingFile();
    FileViewProvider viewProvider = file.getViewProvider();

    if (viewProvider instanceof MultiplePsiFilesPerDocumentFileViewProvider) {
      MultiplePsiFilesPerDocumentFileViewProvider multiProvider = (MultiplePsiFilesPerDocumentFileViewProvider)viewProvider;
      file = multiProvider.getPsi(multiProvider.getBaseLanguage());
      LOG.assertTrue(file != null, element + " " + multiProvider.getBaseLanguage());
    }

    try {
      CodeStyleManager.getInstance(element.getProject())
        .reformatText(file, textRange.getStartOffset(), textRange.getEndOffset());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public static Iterable<PsiClass> iterateSupers(@NotNull final PsiClass psiClass, final boolean includeSelf) {
    return new Iterable<PsiClass>() {
      @Override
      public Iterator<PsiClass> iterator() {
        return new Iterator<PsiClass>() {
          TIntStack indices = new TIntStack();
          Stack<PsiClassType[]> superTypesStack = new Stack<>();
          PsiClass current;
          boolean nextObtained;
          Set<PsiClass> visited = new HashSet<>();

          {
            if (includeSelf) {
              current = psiClass;
              nextObtained = true;
            }
            else {
              current = null;
              nextObtained = false;
            }

            pushSuper(psiClass);
          }

          @Override
          public boolean hasNext() {
            nextElement();
            return current != null;
          }

          private void nextElement() {
            if (nextObtained) return;

            nextObtained = true;
            while (!superTypesStack.empty()) {
              assert indices.size() > 0;

              int i = indices.pop();
              PsiClassType[] superTypes = superTypesStack.peek();
              while (i < superTypes.length) {
                PsiClass clazz = superTypes[i].resolve();
                if (clazz != null && !visited.contains(clazz)) {
                  current = clazz;
                  visited.add(clazz);
                  indices.push(i + 1);
                  pushSuper(clazz);
                  return;
                }
                i++;
              }

              superTypesStack.pop();
            }

            current = null;
          }

          private void pushSuper(PsiClass clazz) {
            superTypesStack.push(clazz.getSuperTypes());
            indices.push(0);
          }

          @Override
          @NotNull
          public PsiClass next() {
            nextElement();
            nextObtained = false;
            if (current == null) throw new NoSuchElementException();
            return current;
          }

          @Override
          public void remove() {
            throw new IllegalStateException("should not be called");
          }
        };
      }
    };
  }

  @Nullable
  public static PsiClass getContextClass(@Nullable PsiElement context) {
    while (context != null) {
      if (context instanceof PsiClass && !isInDummyFile(context)) {
        return (PsiClass)context;
      }
      else if (context instanceof GroovyFileBase && !isInDummyFile(context)) {
        return ((GroovyFileBase)context).getScriptClass();
      }

      context = context.getContext();
    }
    return null;
  }

  public static boolean isInDummyFile(@NotNull PsiElement context) {
    PsiFile file = context.getContainingFile();
    if (file == null) return false;

    String name = file.getName();
    return name.startsWith(GroovyPsiElementFactory.DUMMY_FILE_NAME);
  }

  @Nullable
  public static GroovyPsiElement getFileOrClassContext(PsiElement context) {
    while (context != null) {
      if (context instanceof GrTypeDefinition) {
        return (GroovyPsiElement)context;
      }
      else if (context instanceof GroovyFileBase && context.isPhysical()) {
        return (GroovyPsiElement)context;
      }

      context = context.getContext();
    }

    return null;
  }

  public static boolean mightBeLValue(@Nullable GrExpression expr) {
    if (expr instanceof GrParenthesizedExpression) return mightBeLValue(((GrParenthesizedExpression)expr).getOperand());

    if (expr instanceof GrTuple ||
        expr instanceof GrReferenceExpression ||
        expr instanceof GrIndexProperty ||
        expr instanceof GrPropertySelection) {
      return true;
    }

    if ((isThisOrSuperRef(expr)) &&
        GroovyConfigUtils.getInstance().isVersionAtLeast(expr, GroovyConfigUtils.GROOVY1_8)) {
      return true;
    }
    return false;
  }

  public static boolean isRawMethodCall(GrMethodCallExpression call) {
    final GroovyResolveResult result = call.advancedResolve();
    final PsiElement element = result.getElement();
    if (element == null) return false;
    if (element instanceof PsiMethod) {
      PsiType returnType = getSmartReturnType((PsiMethod)element);
      final GrExpression expression = call.getInvokedExpression();
      if (expression instanceof GrReferenceExpression && result.isInvokedOnProperty()) {
        if (returnType instanceof GrClosureType) {
          return isRawClosureCall(call, result, (GrClosureType)returnType);
        }
      }
      else {
        return isRawType(returnType, result.getSubstitutor());
      }
    }
    if (element instanceof PsiVariable) {
      GrExpression expression = call.getInvokedExpression();
      final PsiType type = expression.getType();
      if (type instanceof GrClosureType) {
        return isRawClosureCall(call, result, (GrClosureType)type);
      }
    }

    return false;
  }

  private static boolean isRawClosureCall(GrMethodCallExpression call, GroovyResolveResult result, GrClosureType returnType) {
    final GrSignature signature = returnType.getSignature();
    GrClosureSignature _signature;
    if (signature instanceof GrClosureSignature) {
      _signature = (GrClosureSignature)signature;
    }
    else {
      final PsiType[] types = getArgumentTypes(call.getInvokedExpression(), true);
      final Trinity<GrClosureSignature, GrClosureSignatureUtil.ArgInfo<PsiType>[], GrClosureSignatureUtil.ApplicabilityResult>
        resultTrinity = types != null ? GrClosureSignatureUtil.getApplicableSignature(signature, types, call) : null;
      _signature = resultTrinity != null ? resultTrinity.first : null;
    }
    if (_signature != null) {
      return isRawType(_signature.getReturnType(), TypesUtil.composeSubstitutors(_signature.getSubstitutor(), result.getSubstitutor()));
    }
    return false;
  }

  public static boolean isRawFieldAccess(GrReferenceExpression ref) {
    PsiElement element = null;
    final GroovyResolveResult[] resolveResults = ref.multiResolve(false);
    if (resolveResults.length == 0) return false;
    final GroovyResolveResult resolveResult = resolveResults[0];
    if (resolveResult != null) {
      element = resolveResult.getElement();
    }
    if (element instanceof PsiField) {
      return isRawType(((PsiField)element).getType(), resolveResult.getSubstitutor());
    }
    else if (element instanceof GrAccessorMethod) {
      return isRawType(((GrAccessorMethod)element).getReturnType(), resolveResult.getSubstitutor());
    }
    return false;
  }

  private static boolean isRawIndexPropertyAccess(GrIndexProperty expr) {
    final GrExpression qualifier = expr.getInvokedExpression();
    final PsiType qualifierType = qualifier.getType();
    if (qualifierType instanceof PsiClassType) {

      if (InheritanceUtil.isInheritor(qualifierType, CommonClassNames.JAVA_UTIL_LIST)) {
        return com.intellij.psi.util.PsiUtil.extractIterableTypeParameter(qualifierType, false) == null;
      }

      if (InheritanceUtil.isInheritor(qualifierType, CommonClassNames.JAVA_UTIL_MAP)) {
        return com.intellij.psi.util.PsiUtil.substituteTypeParameter(qualifierType, CommonClassNames.JAVA_UTIL_MAP, 1, false) == null;
      }
      PsiClassType classType = (PsiClassType)qualifierType;
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      GrExpression[] arguments = expr.getArgumentList().getExpressionArguments();
      PsiType[] argTypes = PsiType.createArray(arguments.length);
      for (int i = 0; i < arguments.length; i++) {
        PsiType argType = arguments[i].getType();
        if (argType == null) argType = TypesUtil.getJavaLangObject(expr);
        argTypes[i] = argType;
      }

      MethodResolverProcessor processor = new MethodResolverProcessor("getAt", expr, false, qualifierType, argTypes, PsiType.EMPTY_ARRAY);

      final PsiClass qClass = resolveResult.getElement();
      final ResolveState state = ResolveState.initial().put(PsiSubstitutor.KEY, PsiSubstitutor.EMPTY);
      if (qClass != null) {
        qClass.processDeclarations(processor, state, null, expr);
      }

      ResolveUtil.processNonCodeMembers(qualifierType, processor, qualifier, state);
      final GroovyResolveResult[] candidates = processor.getCandidates();
      PsiType type = null;
      if (candidates.length == 1) {
        final PsiElement element = candidates[0].getElement();
        if (element instanceof PsiMethod) {
          type = getSmartReturnType((PsiMethod)element);
        }
      }
      return isRawType(type, resolveResult.getSubstitutor());
    }
    return false;
  }

  public static boolean isRawClassMemberAccess(GrExpression expr) {
    expr = (GrExpression)skipParentheses(expr, false);

    if (expr instanceof GrMethodCallExpression) {
      return isRawMethodCall((GrMethodCallExpression)expr);
    }
    if (expr instanceof GrReferenceExpression) {
      return isRawFieldAccess((GrReferenceExpression)expr);
    }
    if (expr instanceof GrIndexProperty) {
      return isRawIndexPropertyAccess((GrIndexProperty)expr);
    }
    return false;
  }

  public static boolean isRawType(@Nullable PsiType type, @NotNull PsiSubstitutor substitutor) {
    if (type instanceof PsiClassType) {
      final PsiClass returnClass = ((PsiClassType)type).resolve();
      if (returnClass instanceof PsiTypeParameter) {
        final PsiTypeParameter typeParameter = (PsiTypeParameter)returnClass;
        return substitutor.substitute(typeParameter) == null;
      }
    }
    return false;
  }

  public static boolean isWhiteSpaceOrLineFeed(PsiElement element) {
    if (element == null) return false;
    ASTNode node = element.getNode();
    if (node == null) return false;
    IElementType elementType = node.getElementType();
    return elementType == GroovyTokenTypes.mNLS || elementType == TokenType.WHITE_SPACE;
  }

  public static boolean isNewLine(PsiElement element) {
    return isWhiteSpaceOrLineFeed(element) && element.getText().contains("\n");
  }

  @Nullable
  public static PsiElement getPrevNonSpace(@NotNull final PsiElement elem) {
    PsiElement prevSibling = elem.getPrevSibling();
    while (prevSibling instanceof PsiWhiteSpace) {
      prevSibling = prevSibling.getPrevSibling();
    }
    return prevSibling;
  }

  @Nullable
  public static PsiElement getNextNonSpace(final PsiElement elem) {
    PsiElement nextSibling = elem.getNextSibling();
    while (nextSibling instanceof PsiWhiteSpace) {
      nextSibling = nextSibling.getNextSibling();
    }
    return nextSibling;
  }

  @NotNull
  public static PsiIdentifier getJavaNameIdentifier(@NotNull GrNamedElement namedElement) {
    final PsiElement element = namedElement.getNameIdentifierGroovy();
    JavaIdentifier identifier = element.getUserData(NAME_IDENTIFIER);
    if (identifier == null) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (element) {
        identifier = element.getUserData(NAME_IDENTIFIER);
        if (identifier != null) {
          return identifier;
        }

        identifier = new JavaIdentifier(element.getManager(), element);
        element.putUserData(NAME_IDENTIFIER, identifier);
      }
    }
    return identifier;
  }

  @Nullable
  public static GrStatement findEnclosingStatement(@Nullable PsiElement context) {
    while (context != null) {
      if (isExpressionStatement(context)) return (GrStatement)context;
      context = PsiTreeUtil.getParentOfType(context, GrStatement.class, true);
    }
    return null;
  }

  public static boolean isMethodCall(GrMethodCall call, String methodName) {
    final GrExpression expression = call.getInvokedExpression();
    return expression instanceof GrReferenceExpression && methodName.equals(expression.getText().trim());
  }
  public static boolean hasEnclosingInstanceInScope(@NotNull PsiClass clazz, @Nullable PsiElement scope, boolean isSuperClassAccepted) {
    return findEnclosingInstanceClassInScope(clazz, scope, isSuperClassAccepted) != null;
  }

  public static PsiClass findEnclosingInstanceClassInScope(@NotNull PsiClass clazz, @Nullable PsiElement scope, boolean isInheritorOfClazzAccepted) {
    PsiElement place = scope;
    while (place != null && place != clazz && !(place instanceof PsiFile && place.isPhysical())) {
      if (place instanceof PsiClass) {
        if (isInheritorOfClazzAccepted) {
          if (InheritanceUtil.isInheritorOrSelf((PsiClass)place, clazz, true)) return (PsiClass)place;
        }
        else {
          if (clazz.getManager().areElementsEquivalent(place, clazz)) return (PsiClass)place;
        }
      }
      if (place instanceof PsiModifierListOwner && ((PsiModifierListOwner)place).hasModifierProperty(PsiModifier.STATIC)) return null;
      place = place.getContext();
    }
    if (clazz instanceof GroovyScriptClass && place == clazz.getContainingFile() || place == clazz) {
      return clazz;
    }
    return null;
  }


  @Nullable
  public static PsiElement skipWhitespacesAndComments(@Nullable PsiElement elem, boolean forward, boolean skipNLs) {
    return skipSet(elem, forward, TokenSets.WHITE_SPACES_OR_COMMENTS, skipNLs);
  }


  @Nullable
  public static PsiElement skipWhitespacesAndComments(@Nullable PsiElement elem, boolean forward) {
    return skipSet(elem, forward, TokenSets.WHITE_SPACES_OR_COMMENTS, true);
  }

  @Nullable
  public static PsiElement skipSet(PsiElement elem, boolean forward, TokenSet set, boolean skipNLs) {
    while (elem != null &&
           elem.getNode() != null &&
           set.contains(elem.getNode().getElementType()) &&
           (skipNLs || elem.getText().indexOf('\n') == -1)) {
      if (forward) {
        elem = elem.getNextSibling();
      }
      else {
        elem = elem.getPrevSibling();
      }
    }
    return elem;
  }

  @Nullable
  public static PsiElement skipSet(@NotNull PsiElement element, boolean forward, @NotNull TokenSet set) {
    do {
      if (forward) {
        element = element.getNextSibling();
      }
      else {
        element = element.getPrevSibling();
      }
    }
    while (element != null && element.getNode() != null && set.contains(element.getNode().getElementType()));

    return element;
  }

  @Nullable
  public static PsiElement skipWhitespaces(@Nullable PsiElement elem, boolean forward) {
    return skipSet(elem, forward, TokenSets.WHITE_SPACES_SET, true);
  }


  @Nullable
  public static PsiType getSmartReturnType(@NotNull PsiMethod method) {
    if (method instanceof GrMethod) {
      return ((GrMethod)method).getInferredReturnType();
    }
    else if (method instanceof GrAccessorMethod) {
      return ((GrAccessorMethod)method).getInferredReturnType();
    }
    else if (method instanceof GrGdkMethod) {
      return getSmartReturnType(((GrGdkMethod)method).getStaticMethod());
    }
    else {
      return method.getReturnType();
    }
  }

  public static boolean isClosurePropertyGetter(PsiMethod method) {
    String methodName = method.getName();
    if (methodName.startsWith("get") && GroovyPropertyUtils.isGetterName(methodName)) { // exclude isXXX()
      PsiModifierList modifiers = method.getModifierList();

      if (!modifiers.hasModifierProperty(PsiModifier.STATIC)
          && !modifiers.hasModifierProperty(PsiModifier.PRIVATE)
          && !modifiers.hasModifierProperty(PsiModifier.PROTECTED)
          && method.getParameterList().getParametersCount() == 0) {
        final PsiType type = getSmartReturnType(method);
        if (type != null && (TypesUtil.isClassType(type, CommonClassNames.JAVA_LANG_OBJECT) || TypesUtil.isClassType(type,
                                                                                                                     GroovyCommonClassNames.GROOVY_LANG_CLOSURE))) {
          return true;
        }
      }
    }

    return false;
  }

  public static boolean isMethodUsage(PsiElement element) {
    if (element instanceof GrEnumConstant) return true;
    if (!(element instanceof GrReferenceElement)) return false;
    PsiElement parent = element.getParent();
    if (parent instanceof GrCall) {
      return true;
    }
    else if (parent instanceof GrAnonymousClassDefinition) {
      return element.equals(((GrAnonymousClassDefinition)parent).getBaseClassReferenceGroovy());
    }
    return false;
  }

  @NotNull
  public static GroovyResolveResult[] getConstructorCandidates(@NotNull PsiElement place,
                                                               @NotNull GroovyResolveResult classCandidate,
                                                               @Nullable PsiType[] argTypes) {
    final PsiElement element = classCandidate.getElement();
    if (!(element instanceof PsiClass)) return GroovyResolveResult.EMPTY_ARRAY;

    PsiClass clazz = (PsiClass)element;
    PsiSubstitutor substitutor = classCandidate.getSubstitutor();
    return ResolveUtil.getAllClassConstructors(clazz, substitutor, argTypes, place);
  }

  public static boolean isAccessedForReading(GrExpression expr) {
    return !isLValue(expr);
  }

  public static boolean isAccessedForWriting(GrExpression expr) {
    return isLValue(expr) || isUsedInIncOrDec(expr);
  }

  public static boolean isUsedInIncOrDec(GrExpression expr) {
    PsiElement parent = PsiTreeUtil.skipParentsOfType(expr, GrParenthesizedExpression.class);

    if (parent instanceof GrUnaryExpression) {
      IElementType tokenType = ((GrUnaryExpression)parent).getOperationTokenType();
      return tokenType == GroovyTokenTypes.mINC || tokenType == GroovyTokenTypes.mDEC;
    }
    return false;
  }

  public static GrReferenceExpression qualifyMemberReference(GrReferenceExpression refExpr, PsiMember member, String name) {
    assert refExpr.getQualifierExpression() == null;

    final PsiClass clazz = member.getContainingClass();
    assert clazz != null;

    final PsiElement replaced;
    if (member.hasModifierProperty(PsiModifier.STATIC)) {
      final GrReferenceExpression newRefExpr = GroovyPsiElementFactory.getInstance(member.getProject())
        .createReferenceExpressionFromText(clazz.getQualifiedName() + "." + name);
      replaced = refExpr.replace(newRefExpr);
    }
    else {
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(refExpr, PsiClass.class);
      if (member.getManager().areElementsEquivalent(containingClass, clazz)) {
        final GrReferenceExpression newRefExpr = GroovyPsiElementFactory.getInstance(member.getProject())
          .createReferenceExpressionFromText("this." + name);
        replaced = refExpr.replace(newRefExpr);
      }
      else {
        final GrReferenceExpression newRefExpr = GroovyPsiElementFactory.getInstance(member.getProject())
          .createReferenceExpressionFromText(clazz.getName() + ".this." + name);
        replaced = refExpr.replace(newRefExpr);
      }
    }
    return (GrReferenceExpression)replaced;
  }

  public static GroovyResolveResult[] getConstructorCandidates(PsiClassType classType, PsiType[] argTypes, GroovyPsiElement context) {
    final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
    final PsiClass psiClass = resolveResult.getElement();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    if (psiClass == null) {
      return GroovyResolveResult.EMPTY_ARRAY;
    }

    final GroovyResolveResult grResult = resolveResult instanceof GroovyResolveResult
                                         ? (GroovyResolveResult)resolveResult
                                         : new GroovyResolveResultImpl(psiClass, context, null, substitutor, true, true);
    return getConstructorCandidates(context, grResult, argTypes);
  }

  @Nullable
  public static PsiElement skipParentheses(@Nullable PsiElement element, boolean up) {
    if (element == null) return null;
    if (up) {
      PsiElement parent;
      while ((parent = element.getParent()) instanceof GrParenthesizedExpression) {
        element = parent;
      }
      return element;
    }
    else {
      while (element instanceof GrParenthesizedExpression) {
        element = ((GrParenthesizedExpression)element).getOperand();
      }
      return element;
    }
  }

  @NotNull
  public static PsiElement skipParenthesesIfSensibly(@NotNull PsiElement element, boolean up) {
    PsiElement res = skipParentheses(element, up);
    return res == null ? element : res;
  }


  @Nullable
  public static PsiElement getNamedArgumentValue(GrNamedArgument otherNamedArgument, String argumentName) {
    PsiElement parent = otherNamedArgument.getParent();

    if (!(parent instanceof GrNamedArgumentsOwner)) return null;

    GrNamedArgument namedArgument = ((GrNamedArgumentsOwner)parent).findNamedArgument(argumentName);
    if (namedArgument == null) return null;

    return namedArgument.getExpression();
  }

  @NotNull
  public static PsiClass getOriginalClass(@NotNull PsiClass aClass) {
    PsiFile file = aClass.getContainingFile();
    if (file == null) return aClass;

    PsiFile originalFile = file.getOriginalFile();
    if (originalFile == file) return aClass;

    if (!(originalFile instanceof PsiClassOwner)) return aClass;

    String name = aClass.getName();
    if (name == null) return aClass;

    for (PsiClass originalClass : ((PsiClassOwner)originalFile).getClasses()) {
      if (name.equals(originalClass.getName())) {
        return originalClass;
      }
    }

    return aClass;
  }


  private static final String[] visibilityModifiers = new String[]{PsiModifier.PRIVATE, PsiModifier.PROTECTED, PsiModifier.PUBLIC};

  public static void escalateVisibility(PsiMember owner, PsiElement place) {
    PsiModifierList modifierList = owner.getModifierList();
    LOG.assertTrue(modifierList != null);
    final String visibilityModifier = VisibilityUtil.getVisibilityModifier(modifierList);
    int index;
    for (index = 0; index < visibilityModifiers.length; index++) {
      String modifier = visibilityModifiers[index];
      if (modifier.equals(visibilityModifier)) break;
    }
    for (; index < visibilityModifiers.length && !isAccessible(place, owner); index++) {
      @PsiModifier.ModifierConstant
      String modifier = visibilityModifiers[index];
      com.intellij.psi.util.PsiUtil.setModifierProperty(owner, modifier, true);
    }
  }

  public static int getArgumentIndex(@NotNull GrCall call, @NotNull PsiElement argument) {
    GrArgumentList argumentList = call.getArgumentList();
    if (argumentList == null) return -1;

    GrExpression[] expressionArguments = argumentList.getExpressionArguments();

    for (int i = 0; i < expressionArguments.length; i++) {
      if (argument.equals(expressionArguments[i])) {
        int res = i;

        if (argumentList.getNamedArguments().length > 0) {
          res++; // first argument is map defined by named arguments
        }

        return res;
      }
    }

    if (argument instanceof GrClosableBlock) {
      GrClosableBlock[] closureArgs = call.getClosureArguments();

      for (int i = 0; i < closureArgs.length; i++) {
        if (argument.equals(closureArgs[i])) {
          int res = i + expressionArguments.length;

          if (argumentList.getNamedArguments().length > 0) {
            res++; // first argument is map defined by named arguments
          }

          return res;
        }
      }

    }

    return -1;
  }

  /**
   * Returns all arguments passed to method. First argument is null if Named Arguments is present.
   */
  public static GrExpression[] getAllArguments(@NotNull GrCall call) {
    GrArgumentList argumentList = call.getArgumentList();
    if (argumentList == null) return GrExpression.EMPTY_ARRAY;

    GrClosableBlock[] closureArguments = call.getClosureArguments();
    GrExpression[] expressionArguments = argumentList.getExpressionArguments();
    GrNamedArgument[] namedArguments = argumentList.getNamedArguments();

    int length = expressionArguments.length + closureArguments.length;
    int k = 0;
    if (namedArguments.length > 0) {
      length++;
      k = 1;
    }

    GrExpression[] res = new GrExpression[length];
    for (GrExpression expressionArgument : expressionArguments) {
      res[k++] = expressionArgument;
    }

    for (GrClosableBlock closureArgument : closureArguments) {
      res[k++] = closureArgument;
    }

    return res;
  }

  public static GrNamedArgument[] getFirstMapNamedArguments(@NotNull GrCall grCall) {
    GrNamedArgument[] res = grCall.getNamedArguments();
    if (res.length > 0) return res;

    GrExpression[] arguments = grCall.getExpressionArguments();
    if (arguments.length == 0) return GrNamedArgument.EMPTY_ARRAY;

    PsiElement firstArg = arguments[0];

    if (!(firstArg instanceof GrListOrMap)) return GrNamedArgument.EMPTY_ARRAY;

    return ((GrListOrMap)firstArg).getNamedArguments();
  }

  public static boolean isExpressionStatement(@Nullable PsiElement expr) {
    if (!(expr instanceof GrStatement)) return false;

    final PsiElement parent = expr.getParent();
    if (parent instanceof GrControlFlowOwner || parent instanceof GrCaseSection) return true;
    if (parent instanceof GrLabeledStatement) return true;
    if (parent instanceof GrIfStatement &&
        (expr == ((GrIfStatement)parent).getThenBranch() || expr == ((GrIfStatement)parent).getElseBranch())) {
      return true;
    }

    if (parent instanceof GrWhileStatement && expr == ((GrWhileStatement)parent).getBody()) {
      return true;
    }
    return false;
  }

  @Nullable
  public static GrMethodCall getMethodCallByNamedParameter(GrNamedArgument namedArgument) {
    GrCall res = getCallByNamedParameter(namedArgument);
    if (res instanceof GrMethodCall) return (GrMethodCall)res;

    return null;
  }

  @Nullable
  public static GrCall getCallByNamedParameter(GrNamedArgument namedArgument) {
    PsiElement parent = namedArgument.getParent();

    PsiElement eMethodCall;

    if (parent instanceof GrArgumentList) {
      eMethodCall = parent.getParent();
    }
    else {
      if (!(parent instanceof GrListOrMap)) return null;

      PsiElement eArgumentList = parent.getParent();
      if (!(eArgumentList instanceof GrArgumentList)) return null;

      GrArgumentList argumentList = (GrArgumentList)eArgumentList;

      if (argumentList.getNamedArguments().length > 0) return null;
      if (argumentList.getExpressionArgumentIndex((GrListOrMap)parent) != 0) return null;

      eMethodCall = eArgumentList.getParent();
    }

    if (!(eMethodCall instanceof GrCall)) return null;

    return (GrCall)eMethodCall;
  }

  public static String getAnnoAttributeValue(@NotNull PsiAnnotation annotation, final String attributeName, String defaultValue) {
    PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
    if (value instanceof GrExpression) {
      Object o = GroovyConstantExpressionEvaluator.evaluate((GrExpression)value);
      if (o instanceof String) {
        return (String)o;
      }
    }
    return defaultValue;
  }

  public static boolean getAnnoAttributeValue(@NotNull PsiAnnotation annotation, final String attributeName, boolean defaultValue) {
    PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
    if (value instanceof GrExpression) {
      Object o = GroovyConstantExpressionEvaluator.evaluate((GrExpression)value);
      if (o instanceof Boolean) {
        return (Boolean)o;
      }
    }
    return defaultValue;
  }

  public static boolean isExpressionUsed(PsiElement expr) {
    while (expr.getParent() instanceof GrParenthesizedExpression) expr = expr.getParent();

    PsiElement parent = expr.getParent();
    if (parent instanceof GrBinaryExpression ||
        parent instanceof GrUnaryExpression ||
        parent instanceof GrConditionalExpression ||
        parent instanceof GrAssignmentExpression ||
        parent instanceof GrInstanceOfExpression ||
        parent instanceof GrSafeCastExpression ||
        parent instanceof GrTuple ||
        parent instanceof GrArgumentList ||
        parent instanceof GrReturnStatement ||
        parent instanceof GrAssertStatement ||
        parent instanceof GrThrowStatement ||
        parent instanceof GrSwitchStatement ||
        parent instanceof GrVariable ||
        parent instanceof GrWhileStatement) {
      return true;
    }

    if (parent instanceof GrReferenceExpression) {
      if (ResolveUtil.isClassReference(parent)) {
        parent = parent.getParent();
      }
      if (parent instanceof GrReferenceExpression) {
        PsiElement resolved = ((GrReferenceExpression)parent).resolve();
        if (resolved instanceof PsiMember) {
          PsiClass containingClass = ((PsiMember)resolved).getContainingClass();
          return containingClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(containingClass.getQualifiedName());
        }
      }
      return true;
    }

    if (parent instanceof GrTraditionalForClause) {
      GrTraditionalForClause forClause = (GrTraditionalForClause)parent;
      return expr == forClause.getCondition();
    }

    return isReturnStatement(expr);
  }

  public static boolean isReturnStatement(@NotNull PsiElement statement) {
    final GrControlFlowOwner controlFlowOwner = ControlFlowUtils.findControlFlowOwner(statement);
    if (controlFlowOwner instanceof GrOpenBlock) {
      final PsiElement controlFlowOwnerParent = controlFlowOwner.getParent();
      if (controlFlowOwnerParent instanceof GrMethod && ((GrMethod)controlFlowOwnerParent).isConstructor()) {
        return false;
      }
      else if (controlFlowOwnerParent instanceof PsiMethod && PsiType.VOID.equals(((PsiMethod)controlFlowOwnerParent).getReturnType())) {
        return false;
      }
    }
    //noinspection SuspiciousMethodCalls
    return ControlFlowUtils.collectReturns(controlFlowOwner, true).contains(statement);
  }

  @Nullable
  public static PsiClass getContainingNotInnerClass(@Nullable PsiElement element) {
    PsiClass domainClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (domainClass == null) return null;

    while (true) {
      PsiClass c = domainClass.getContainingClass();
      if (c == null) return domainClass;
      domainClass = c;
    }
  }

  @NotNull
  public static ResolveResult getAccessObjectClass(GrExpression expression) {
    if (isThisOrSuperRef(expression)) return EmptyGroovyResolveResult.INSTANCE;
    PsiType type = expression.getType();
    if (type instanceof PsiClassType) {
      return ((PsiClassType)type).resolveGenerics();
    }
    if (type == null && expression instanceof GrReferenceExpression) {
      GroovyResolveResult resolveResult = ((GrReferenceExpression)expression).advancedResolve();
      if (resolveResult.getElement() instanceof PsiClass) {
        return resolveResult;
      }
    }
    return EmptyGroovyResolveResult.INSTANCE;
  }

  public static boolean isReferenceWithoutQualifier(@Nullable PsiElement element, @NotNull String name) {
    if (!(element instanceof GrReferenceExpression)) return false;

    GrReferenceExpression ref = (GrReferenceExpression)element;

    return !ref.isQualified() && name.equals(ref.getReferenceName());
  }

  public static boolean isProperty(@NotNull GrField field) {
    final PsiClass clazz = field.getContainingClass();
    if (clazz == null) return false;
    if (clazz.isInterface() && !GrTraitUtil.isTrait(clazz)) return false;
    final GrModifierList modifierList = field.getModifierList();
    return modifierList == null || !modifierList.hasExplicitVisibilityModifiers();
  }

  public static boolean isConstructorHasRequiredParameters(@NotNull PsiMethod constructor) {
    LOG.assertTrue(constructor.isConstructor());
    final PsiParameter[] parameters = constructor.getParameterList().getParameters();
    for (PsiParameter parameter : parameters) {
      if (!(parameter instanceof GrParameter && ((GrParameter)parameter).isOptional())) return true;
    }
    return false;
  }

  public static boolean isInMethodCallContext(PsiElement context) {
    return getArgumentsList(context) != null;
  }

  @NotNull
  public static List<GrImportStatement> getValidImportStatements(final GroovyFile file) {
    final List<GrImportStatement> oldImports = new ArrayList<>();
    for (GrImportStatement statement : file.getImportStatements()) {
      if (!ErrorUtil.containsError(statement)) {
        oldImports.add(statement);
      }
    }
    return oldImports;
  }

  public static boolean isCompileStatic(PsiElement e) {
    PsiMember containingMember = PsiTreeUtil.getParentOfType(e, PsiMember.class, false, GrAnnotation.class);
    return containingMember != null && GroovyPsiManager.getInstance(containingMember.getProject()).isCompileStatic(containingMember);
  }

  @Nullable
  public static PsiType extractIteratedType(GrForInClause forIn) {
    GrExpression iterated = forIn.getIteratedExpression();
    if (iterated == null) return null;
    return ClosureParameterEnhancer.findTypeForIteration(iterated, forIn);
  }

  public static boolean isThisReference(@Nullable PsiElement expression) {
    return isThisOrSuperRef(expression, GroovyTokenTypes.kTHIS, false);
  }

  public static boolean isSuperReference(@Nullable PsiElement expression) {
    return isThisOrSuperRef(expression, GroovyTokenTypes.kSUPER, true);
  }

  private static boolean isThisOrSuperRef(PsiElement expression, IElementType token, boolean superClassAccepted) {
    if (!(expression instanceof GrReferenceExpression)) return false;
    GrReferenceExpression ref = (GrReferenceExpression)expression;

    PsiElement nameElement = ref.getReferenceNameElement();
    if (nameElement == null) return false;

    IElementType type = nameElement.getNode().getElementType();
    if (type != token) return false;

    GrExpression qualifier = ref.getQualifier();
    if (qualifier == null) {
      return true;
    }
    else {
      PsiElement resolved = ref.resolve();
      if (resolved instanceof PsiClass) {
        if (hasEnclosingInstanceInScope(((PsiClass)resolved), ref, superClassAccepted)) return true;
        if (superClassAccepted && GrTraitUtil.isTrait((PsiClass)resolved) && scopeClassImplementsTrait(((PsiClass)resolved), ref)) return true;
      }
      return false;
    }
  }

  public static boolean scopeClassImplementsTrait(@NotNull final PsiClass trait, @NotNull final PsiElement place) {
    GrTypeDefinition scopeClass = PsiTreeUtil.getParentOfType(place, GrTypeDefinition.class, true);
    return scopeClass != null && ContainerUtil.find(scopeClass.getSuperTypes(),
                                                    type -> place.getManager().areElementsEquivalent(type.resolve(), trait)) != null;
  }

  public static boolean isThisOrSuperRef(@Nullable PsiElement qualifier) {
    return qualifier instanceof GrReferenceExpression && (isThisReference(qualifier) || isSuperReference(qualifier));
  }

  public static boolean isInstanceThisRef(PsiElement qualifier) {
    if (isThisReference(qualifier)) {
      GrReferenceExpression ref = (GrReferenceExpression)qualifier;

      PsiElement resolved = ref.resolve();
      if (!(resolved instanceof PsiClass)) return false;

      return hasEnclosingInstanceInScope((PsiClass)resolved, qualifier, false);
    }
    return false;
  }

  public static boolean isLineFeed(@Nullable PsiElement e) {
    return e != null &&
           PsiImplUtil.isWhiteSpaceOrNls(e) &&
           (e.getText().indexOf('\n') >= 0 || e.getText().indexOf('\r') >= 0);
  }

  public static boolean isSingleBindingVariant(GroovyResolveResult[] candidates) {
    return candidates.length == 1 && candidates[0].getElement() instanceof GrBindingVariable;
  }

  @Nullable
  public static GrConstructorInvocation getConstructorInvocation(@NotNull GrMethod constructor) {
    assert constructor.isConstructor();

    final GrOpenBlock body = constructor.getBlock();
    if (body == null) return null;

    final GrStatement[] statements = body.getStatements();
    if (statements.length > 0 && statements[0] instanceof GrConstructorInvocation) {
      return ((GrConstructorInvocation)statements[0]);
    }
    else {
      return null;
    }
  }

  public static boolean isDGMMethod(@Nullable PsiElement element) {
    if (!(element instanceof PsiMethod)) return false;

    final PsiMethod method = element instanceof GrGdkMethod ? ((GrGdkMethod)element).getStaticMethod() : (PsiMethod)element;
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return false;

    final String qname = aClass.getQualifiedName();
    return DEFAULT_INSTANCE_EXTENSIONS.contains(qname) || DEFAULT_STATIC_EXTENSIONS.contains(qname);
  }

  public static boolean isVoidMethodCall(@Nullable GrExpression expression) {
    if (expression instanceof GrMethodCall && PsiType.NULL.equals(expression.getType())) {
      final GroovyResolveResult resolveResult = ((GrMethodCall)expression).advancedResolve();
      final PsiType[] args = getArgumentTypes(((GrMethodCall)expression).getInvokedExpression(), true);
      return PsiType.VOID.equals(ResolveUtil.extractReturnTypeFromCandidate(resolveResult, expression, args));
    }

    return false;
  }

  public static boolean isVoidMethod(@NotNull PsiMethod method) {
    if (PsiType.VOID.equals(method.getReturnType())) {
      return true;
    }

    if (method instanceof GrMethod && ((GrMethod)method).getReturnTypeElementGroovy() == null) {
      GrOpenBlock block = ((GrMethod)method).getBlock();
      if (block != null && isBlockReturnVoid(block)) {
        return true;
      }
    }

    return false;
  }

  public static boolean isBlockReturnVoid(@NotNull final GrCodeBlock block) {
    return CachedValuesManager.getCachedValue(block, () -> CachedValueProvider.Result.create(ControlFlowUtils.visitAllExitPoints(block, new ControlFlowUtils.ExitPointVisitor() {
      @Override
      public boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue) {
        return returnValue == null || !(returnValue instanceof GrLiteral);
      }
    }), PsiModificationTracker.MODIFICATION_COUNT));
  }

  public static boolean checkPsiElementsAreEqual(PsiElement l, PsiElement r) {
    if (!l.getText().equals(r.getText())) return false;
    if (l.getNode().getElementType() != r.getNode().getElementType()) return false;

    final PsiElement[] lChildren = l.getChildren();
    final PsiElement[] rChildren = r.getChildren();

    if (lChildren.length != rChildren.length) return false;

    for (int i = 0; i < rChildren.length; i++) {
      if (!checkPsiElementsAreEqual(lChildren[i], rChildren[i])) return false;
    }
    return true;
  }

  public static boolean isCall(GrReferenceExpression referenceExpression) {
    return referenceExpression.getParent() instanceof GrCall;
  }

  public static boolean isLocalVariable(@Nullable PsiElement variable) {
    return variable instanceof GrVariable && !(variable instanceof GrField || variable instanceof GrParameter);
  }

  public static boolean isLocalOrParameter(@Nullable PsiElement variable) {
    return variable instanceof GrVariable && !(variable instanceof GrField);
  }

  @Nullable
  public static PsiElement getPreviousNonWhitespaceToken(PsiElement e) {
    PsiElement next = PsiTreeUtil.prevLeaf(e);
    while (next != null && next.getNode().getElementType() == TokenType.WHITE_SPACE) next = PsiTreeUtil.prevLeaf(next);
    return next;
  }

  @Nullable
  public static Object getLabelValue(@Nullable GrArgumentLabel label) {
    if (label == null) return null;
    final PsiElement element = label.getNameElement();
    if (element instanceof GrExpression) {
      final Object value = JavaPsiFacade.getInstance(label.getProject()).getConstantEvaluationHelper().computeConstantExpression(element);
      if (value != null) return value;
    }

    final IElementType elemType = element.getNode().getElementType();
    if (GroovyTokenTypes.mIDENT == elemType || TokenSets.KEYWORDS.contains(elemType)) {
      return element.getText();
    }

    return GrLiteralImpl.getLiteralValue(element);
  }
}
