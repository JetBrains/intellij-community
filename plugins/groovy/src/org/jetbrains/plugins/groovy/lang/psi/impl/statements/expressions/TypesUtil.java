/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;

import java.util.Map;

import static com.intellij.psi.CommonClassNames.*;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

/**
 * @author ven
 */
public class TypesUtil {
  @NonNls
  public static final Map<String, PsiType> ourQNameToUnboxed = new HashMap<String, PsiType>();

  private TypesUtil() {
  }

  @Nullable
  public static PsiType getNumericResultType(GrBinaryExpression binaryExpression, PsiType lType) {
    final GrExpression rop = binaryExpression.getRightOperand();
    PsiType rType = rop == null ? null : rop.getType();
    if (lType == null || rType == null) return null;
    String lCanonical = lType.getCanonicalText();
    String rCanonical = rType.getCanonicalText();
    if (TYPE_TO_RANK.containsKey(lCanonical) && TYPE_TO_RANK.containsKey(rCanonical)) {
      int lRank = TYPE_TO_RANK.get(lCanonical);
      int rRank = TYPE_TO_RANK.get(rCanonical);
      int resultRank = Math.max(lRank, rRank);
      String qName = RANK_TO_TYPE.get(resultRank);
      GlobalSearchScope scope = binaryExpression.getResolveScope();
      if (qName == null) return null;
      return JavaPsiFacade.getInstance(binaryExpression.getProject()).getElementFactory().createTypeByFQClassName(qName, scope);
    }

    final PsiType type = getOverloadedOperatorType(lType, binaryExpression.getOperationTokenType(), binaryExpression, new PsiType[]{rType});
    if (type != null) {
      return type;
    }

    if (rType.equalsToText(GrStringUtil.GROOVY_LANG_GSTRING)) {
      PsiType gstringType = JavaPsiFacade.getInstance(binaryExpression.getProject()).getElementFactory()
        .createTypeByFQClassName(GrStringUtil.GROOVY_LANG_GSTRING, binaryExpression.getResolveScope());
      return getOverloadedOperatorType(lType, binaryExpression.getOperationTokenType(), binaryExpression, new PsiType[]{gstringType});
    }
    return null;
  }

  @Nullable
  public static PsiType getOverloadedOperatorType(PsiType thisType,
                                                  IElementType tokenType,
                                                  GroovyPsiElement place,
                                                  PsiType[] argumentTypes) {
    return getOverloadedOperatorType(thisType, ourOperationsToOperatorNames.get(tokenType), place, argumentTypes);
  }

  @Nullable
  public static PsiType getOverloadedOperatorType(PsiType thisType, String operatorName, GroovyPsiElement place, PsiType[] argumentTypes) {
    if (operatorName != null) {
      MethodResolverProcessor processor =
        new MethodResolverProcessor(operatorName, place, false, thisType, argumentTypes, PsiType.EMPTY_ARRAY);
      if (thisType instanceof PsiClassType) {
        final PsiClassType classtype = (PsiClassType)thisType;
        final PsiClassType.ClassResolveResult resolveResult = classtype.resolveGenerics();
        final PsiClass lClass = resolveResult.getElement();
        if (lClass != null) {
          lClass.processDeclarations(processor, ResolveState.initial().put(PsiSubstitutor.KEY, resolveResult.getSubstitutor()), null, place);
        }
      }

      ResolveUtil.processNonCodeMethods(thisType, processor, place.getProject(), place, false);
      final GroovyResolveResult[] candidates = processor.getCandidates();
      if (candidates.length == 1) {
        final PsiElement element = candidates[0].getElement();
        if (element instanceof PsiMethod) {
          return candidates[0].getSubstitutor().substitute(((PsiMethod)element).getReturnType());
        }
      }
    }
    return null;
  }
  private static final Map<IElementType, String> ourPrimitiveTypesToClassNames = new HashMap<IElementType, String>();
  private static final String NULL = "null";
  private static final String JAVA_MATH_BIG_DECIMAL = "java.math.BigDecimal";
  private static final String JAVA_MATH_BIG_INTEGER = "java.math.BigInteger";

  static {
    ourPrimitiveTypesToClassNames.put(mSTRING_LITERAL, JAVA_LANG_STRING);
    ourPrimitiveTypesToClassNames.put(mGSTRING_LITERAL, JAVA_LANG_STRING);
    ourPrimitiveTypesToClassNames.put(mREGEX_LITERAL, JAVA_LANG_STRING);
    ourPrimitiveTypesToClassNames.put(mNUM_INT, JAVA_LANG_INTEGER);
    ourPrimitiveTypesToClassNames.put(mNUM_LONG, JAVA_LANG_LONG);
    ourPrimitiveTypesToClassNames.put(mNUM_FLOAT, JAVA_LANG_FLOAT);
    ourPrimitiveTypesToClassNames.put(mNUM_DOUBLE, JAVA_LANG_DOUBLE);
    ourPrimitiveTypesToClassNames.put(mNUM_BIG_INT, JAVA_MATH_BIG_INTEGER);
    ourPrimitiveTypesToClassNames.put(mNUM_BIG_DECIMAL, JAVA_MATH_BIG_DECIMAL);
    ourPrimitiveTypesToClassNames.put(kFALSE, JAVA_LANG_BOOLEAN);
    ourPrimitiveTypesToClassNames.put(kTRUE, JAVA_LANG_BOOLEAN);
    ourPrimitiveTypesToClassNames.put(kNULL, NULL);
  }

  private static final Map<IElementType, String> ourOperationsToOperatorNames = new HashMap<IElementType, String>();

  static {
    ourOperationsToOperatorNames.put(mPLUS, "plus");
    ourOperationsToOperatorNames.put(mMINUS, "minus");
    ourOperationsToOperatorNames.put(mBAND, "and");
    ourOperationsToOperatorNames.put(mBOR, "or");
    ourOperationsToOperatorNames.put(mBXOR, "xor");
    ourOperationsToOperatorNames.put(mDIV, "div");
    ourOperationsToOperatorNames.put(mMOD, "mod");
    ourOperationsToOperatorNames.put(mSTAR, "multiply");
    ourOperationsToOperatorNames.put(mDEC, "previous");
    ourOperationsToOperatorNames.put(mINC, "next");
  }

  private static final TObjectIntHashMap<String> TYPE_TO_RANK = new TObjectIntHashMap<String>();

  static {
    TYPE_TO_RANK.put(JAVA_LANG_BYTE, 1);
    TYPE_TO_RANK.put(JAVA_LANG_SHORT, 2);
    TYPE_TO_RANK.put(JAVA_LANG_CHARACTER, 2);
    TYPE_TO_RANK.put(JAVA_LANG_INTEGER, 3);
    TYPE_TO_RANK.put(JAVA_LANG_LONG, 4);
    TYPE_TO_RANK.put(JAVA_MATH_BIG_INTEGER, 5);
    TYPE_TO_RANK.put(JAVA_MATH_BIG_DECIMAL, 6);
    TYPE_TO_RANK.put(JAVA_LANG_FLOAT, 7);
    TYPE_TO_RANK.put(JAVA_LANG_DOUBLE, 8);
    TYPE_TO_RANK.put(JAVA_LANG_NUMBER, 9);
  }

  static {
    ourQNameToUnboxed.put(JAVA_LANG_BOOLEAN, PsiType.BOOLEAN);
    ourQNameToUnboxed.put(JAVA_LANG_BYTE, PsiType.BYTE);
    ourQNameToUnboxed.put(JAVA_LANG_CHARACTER, PsiType.CHAR);
    ourQNameToUnboxed.put(JAVA_LANG_SHORT, PsiType.SHORT);
    ourQNameToUnboxed.put(JAVA_LANG_INTEGER, PsiType.INT);
    ourQNameToUnboxed.put(JAVA_LANG_LONG, PsiType.LONG);
    ourQNameToUnboxed.put(JAVA_LANG_FLOAT, PsiType.FLOAT);
    ourQNameToUnboxed.put(JAVA_LANG_DOUBLE, PsiType.DOUBLE);
  }


  private static final TIntObjectHashMap<String> RANK_TO_TYPE = new TIntObjectHashMap<String>();

  static {
    RANK_TO_TYPE.put(1, JAVA_LANG_INTEGER);
    RANK_TO_TYPE.put(2, JAVA_LANG_INTEGER);
    RANK_TO_TYPE.put(3, JAVA_LANG_INTEGER);
    RANK_TO_TYPE.put(4, JAVA_LANG_LONG);
    RANK_TO_TYPE.put(5, JAVA_MATH_BIG_INTEGER);
    RANK_TO_TYPE.put(6, JAVA_MATH_BIG_DECIMAL);
    RANK_TO_TYPE.put(7, JAVA_LANG_DOUBLE);
    RANK_TO_TYPE.put(8, JAVA_LANG_DOUBLE);
    RANK_TO_TYPE.put(9, JAVA_LANG_NUMBER);
  }

  public static boolean isAssignable(PsiType lType, PsiType rType, PsiManager manager, GlobalSearchScope scope) {
    //all numeric types are assignable
    if (isNumericType(lType)) {
      return isNumericType(rType) || rType.equals(PsiType.NULL);
    }
    if (rType instanceof GrTupleType) {
      final GrTupleType tuple = (GrTupleType)rType;
      if (tuple.getComponentTypes().length == 0) {
        if (lType instanceof PsiArrayType || InheritanceUtil.isInheritor(lType, JAVA_UTIL_LIST)) {
          return true;
        }
      }
    }

    if (lType.equalsToText(JAVA_LANG_STRING)) return true;

    rType = boxPrimitiveType(rType, manager, scope);
    lType = boxPrimitiveType(lType, manager, scope);

    return lType.isAssignableFrom(rType);
  }

  public static boolean isAssignableByMethodCallConversion(PsiType lType, PsiType rType, PsiManager manager, GlobalSearchScope scope) {
    if (lType == null || rType == null) return false;

    if (rType.equalsToText(GrStringUtil.GROOVY_LANG_GSTRING)) {
      final PsiClass javaLangString = JavaPsiFacade.getInstance(manager.getProject()).findClass(JAVA_LANG_STRING, scope);
      if (javaLangString != null &&
          isAssignable(lType, JavaPsiFacade.getElementFactory(manager.getProject()).createType(javaLangString), manager, scope)) {
        return true;
      }
    }

    if (isNumericType(lType) && isNumericType(rType)) {
      lType = unboxPrimitiveTypeWrapper(lType);
      if (lType.equalsToText(JAVA_MATH_BIG_DECIMAL)) lType = PsiType.DOUBLE;
      rType = unboxPrimitiveTypeWrapper(rType);
      if (rType.equalsToText(JAVA_MATH_BIG_DECIMAL)) rType = PsiType.DOUBLE;
    }
    else {
      rType = boxPrimitiveType(rType, manager, scope);
      lType = boxPrimitiveType(lType, manager, scope);
    }

    return TypeConversionUtil.isAssignable(lType, rType);

  }

  public static boolean isNumericType(PsiType type) {
    if (type instanceof PsiClassType) {
      return TYPE_TO_RANK.contains(type.getCanonicalText());
    }

    return type instanceof PsiPrimitiveType && TypeConversionUtil.isNumericType(type);
  }

  public static PsiType unboxPrimitiveTypeWraperAndEraseGenerics(PsiType result) {
    return TypeConversionUtil.erasure(unboxPrimitiveTypeWrapper(result));
  }

  public static PsiType unboxPrimitiveTypeWrapper(PsiType type) {
    if (type instanceof PsiClassType) {
      PsiType unboxed = ourQNameToUnboxed.get(type.getCanonicalText());
      if (unboxed != null) type = unboxed;
    }
    return type;
  }

  public static PsiType boxPrimitiveType(PsiType result, PsiManager manager, GlobalSearchScope resolveScope) {
    if (result instanceof PsiPrimitiveType && result != PsiType.VOID) {
      PsiPrimitiveType primitive = (PsiPrimitiveType)result;
      String boxedTypeName = primitive.getBoxedTypeName();
      if (boxedTypeName != null) {
        return JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeByFQClassName(boxedTypeName, resolveScope);
      }
    }

    return result;
  }

  @Nullable
  public static PsiType getTypeForIncOrDecExpression(GrUnaryExpression expr) {
    final GrExpression op = expr.getOperand();
    if (op != null) {
      final PsiType opType = op.getType();
      if (opType != null) {
        final PsiType overloaded = getOverloadedOperatorType(opType, expr.getOperationTokenType(), expr, PsiType.EMPTY_ARRAY);
        if (overloaded != null) {
          return overloaded;
        }
        if (isNumericType(opType)) {
          return opType;
        }
      }
    }

    return null;
  }

  public static PsiClassType createType(String fqName, PsiElement context) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getProject());
    return facade.getElementFactory().createTypeByFQClassName(fqName, context.getResolveScope());
  }

  public static PsiClassType getJavaLangObject(GroovyPsiElement context) {
    return PsiType.getJavaLangObject(context.getManager(), context.getResolveScope());
  }

  @Nullable
  public static PsiType getLeastUpperBound(@NotNull PsiType type1, @NotNull PsiType type2, PsiManager manager) {
    if (type1 instanceof GrTupleType && type2 instanceof GrTupleType) {
      GrTupleType tuple1 = (GrTupleType)type1;
      GrTupleType tuple2 = (GrTupleType)type2;
      PsiType[] components1 = tuple1.getComponentTypes();
      PsiType[] components2 = tuple2.getComponentTypes();
      PsiType[] components3 = new PsiType[Math.min(components1.length, components2.length)];
      for (int i = 0; i < components3.length; i++) {
        PsiType c1 = components1[i];
        PsiType c2 = components2[i];
        if (c1 == null || c2 == null) {
          components3[i] = null;
        }
        else {
          components3[i] = getLeastUpperBound(c1, c2, manager);
        }
      }
      return new GrTupleType(components3, JavaPsiFacade.getInstance(manager.getProject()),
                             tuple1.getScope().intersectWith(tuple2.getResolveScope()));
    }
    else if (type1 instanceof GrClosureType && type2 instanceof GrClosureType) {
      GrClosureType clType1 = (GrClosureType)type1;
      GrClosureType clType2 = (GrClosureType)type2;
      PsiType[] parameterTypes1 = clType1.getClosureParameterTypes();
      PsiType[] parameterTypes2 = clType2.getClosureParameterTypes();
      if (parameterTypes1.length == parameterTypes2.length) {
        PsiType[] paramTypes = new PsiType[parameterTypes1.length];
        boolean[] opts = new boolean[parameterTypes1.length];
        for (int i = 0; i < paramTypes.length; i++) {
          paramTypes[i] = GenericsUtil.getGreatestLowerBound(parameterTypes1[i], parameterTypes2[i]);
          opts[i] = clType1.isOptionalParameter(i) && clType2.isOptionalParameter(i);
        }
        final PsiType ret1 = clType1.getClosureReturnType();
        final PsiType ret2 = clType2.getClosureReturnType();
        PsiType returnType = ret1 == null ? ret2 : ret2 == null ? ret1 : getLeastUpperBound(ret1, ret2, manager);
        GlobalSearchScope scope = clType1.getResolveScope().intersectWith(clType2.getResolveScope());
        return GrClosureType.create(returnType, paramTypes, opts, manager, scope, LanguageLevel.JDK_1_5);
      }
    }
    else if (GrStringUtil.GROOVY_LANG_GSTRING.equals(type1.getCanonicalText()) &&
             CommonClassNames.JAVA_LANG_STRING.equals(type2.getInternalCanonicalText())) {
      return type2;
    }
    else if (GrStringUtil.GROOVY_LANG_GSTRING.equals(type2.getCanonicalText()) &&
             CommonClassNames.JAVA_LANG_STRING.equals(type1.getInternalCanonicalText())) {
      return type1;
    }

    return GenericsUtil.getLeastUpperBound(type1, type2, manager);
  }

  @Nullable
  public static PsiType getPsiType(PsiElement context, IElementType elemType) {
    if (elemType == kNULL) {
      return PsiType.NULL;
    }
    final String typeName = getPsiTypeName(elemType);
    if (typeName != null) {
      return JavaPsiFacade.getElementFactory(context.getProject()).createTypeByFQClassName(typeName, context.getResolveScope());
    }
    return null;
  }

  @Nullable
  private static String getPsiTypeName(IElementType elemType) {
    return ourPrimitiveTypesToClassNames.get(elemType);
  }
}
