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

import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSubstitutorImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ComparatorUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Iterator;
import java.util.Map;

import static com.intellij.psi.CommonClassNames.*;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.JAVA_MATH_BIG_INTEGER;

/**
 * @author ven
 */
public class TypesUtil {
  @NonNls
  public static final Map<String, PsiType> ourQNameToUnboxed = new HashMap<String, PsiType>();

  private TypesUtil() {
  }

  @Nullable
  public static PsiType getNumericResultType(GrBinaryExpression binaryExpression) {
    PsiType lType = binaryExpression.getLeftOperand().getType();
    final GrExpression rop = binaryExpression.getRightOperand();
    PsiType rType = rop == null ? null : rop.getType();
    if (lType == null || rType == null) return null;
    return getLeastUpperBoundForNumericType(lType, rType);
  }

  @Nullable
  private static PsiType getLeastUpperBoundForNumericType(@NotNull PsiType lType, @NotNull PsiType rType) {
    String lCanonical = lType.getCanonicalText();
    String rCanonical = rType.getCanonicalText();
    if (JAVA_LANG_FLOAT.equals(lCanonical)) lCanonical = JAVA_LANG_DOUBLE;
    if (JAVA_LANG_FLOAT.equals(rCanonical)) rCanonical = JAVA_LANG_DOUBLE;
    if (TYPE_TO_RANK.containsKey(lCanonical) && TYPE_TO_RANK.containsKey(rCanonical)) {
      return TYPE_TO_RANK.get(lCanonical) > TYPE_TO_RANK.get(rCanonical) ? lType : rType;
    }
    return null;
  }

  @NotNull
  public static GroovyResolveResult[] getOverloadedOperatorCandidates(@NotNull PsiType thisType,
                                                                      IElementType tokenType,
                                                                      @NotNull GroovyPsiElement place,
                                                                      PsiType[] argumentTypes) {
    return ResolveUtil.getMethodCandidates(thisType, ourOperationsToOperatorNames.get(tokenType), place, argumentTypes);
  }

  public static GroovyResolveResult[] getOverloadedUnaryOperatorCandidates(@NotNull PsiType thisType,
                                                                      IElementType tokenType,
                                                                      @NotNull GroovyPsiElement place,
                                                                      PsiType[] argumentTypes) {
    return ResolveUtil.getMethodCandidates(thisType, ourUnaryOperationsToOperatorNames.get(tokenType), place, argumentTypes);
  }

  private static final Map<IElementType, String> ourPrimitiveTypesToClassNames = new HashMap<IElementType, String>();
  private static final String NULL = "null";

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
  private static final Map<IElementType, String> ourUnaryOperationsToOperatorNames = new HashMap<IElementType, String>();

  static {
    ourOperationsToOperatorNames.put(mPLUS, "plus");
    ourOperationsToOperatorNames.put(mMINUS, "minus");
    ourOperationsToOperatorNames.put(mBAND, "and");
    ourOperationsToOperatorNames.put(mBOR, "or");
    ourOperationsToOperatorNames.put(mBXOR, "xor");
    ourOperationsToOperatorNames.put(mDIV, "div");
    ourOperationsToOperatorNames.put(mMOD, "mod");
    ourOperationsToOperatorNames.put(mSTAR, "multiply");
    ourOperationsToOperatorNames.put(kAS, "asType");
    ourOperationsToOperatorNames.put(mCOMPARE_TO, "compareTo");
    ourOperationsToOperatorNames.put(mGT, "compareTo");
    ourOperationsToOperatorNames.put(mGE, "compareTo");
    ourOperationsToOperatorNames.put(mLT, "compareTo");
    ourOperationsToOperatorNames.put(mLE, "compareTo");

    ourUnaryOperationsToOperatorNames.put(mLNOT, "asBoolean");
    ourUnaryOperationsToOperatorNames.put(mPLUS, "positive");
    ourUnaryOperationsToOperatorNames.put(mMINUS, "negative");
    ourUnaryOperationsToOperatorNames.put(mDEC, "previous");
    ourUnaryOperationsToOperatorNames.put(mINC, "next");
    ourUnaryOperationsToOperatorNames.put(mBNOT, "bitwiseNegate");
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
    return isAssignable(lType, rType, manager, scope, true);
  }

  public static boolean isAssignable(PsiType lType, PsiType rType, PsiManager manager, GlobalSearchScope scope, boolean allowConversion) {
    if (allowConversion && isAssignableByMethodCallConversion(lType, rType, manager, scope)) {
      return true;
    }

    return _isAssignable(lType, rType, manager, scope, allowConversion);
  }

  public static boolean isAssignable(@NotNull PsiType lType, @NotNull PsiType rType, GroovyPsiElement context) {
    return isAssignable(lType, rType, context, true);
  }

  public static boolean isAssignable(@NotNull PsiType lType, @NotNull PsiType rType, GroovyPsiElement context, boolean allowConversion) {
    if (rType instanceof PsiIntersectionType) {
      for (PsiType child : ((PsiIntersectionType)rType).getConjuncts()) {
        if (isAssignable(lType, child, context, allowConversion)) {
          return true;
        }
      }
      return false;
    }
    if (lType instanceof PsiIntersectionType) {
      for (PsiType child : ((PsiIntersectionType)lType).getConjuncts()) {
        if (!isAssignable(child, rType, context, allowConversion)) {
          return false;
        }
      }
      return true;
    }

    if (allowConversion) {
      for (GrTypeConverter converter : GrTypeConverter.EP_NAME.getExtensions()) {
        final Boolean result = converter.isConvertible(lType, rType, context);
        if (result != null) {
          return result;
        }
      }
    }
    return (allowConversion && isAssignableByMethodCallConversion(lType, rType, context)) ||
           _isAssignable(lType, rType, context.getManager(), context.getResolveScope(), true);
  }

  private static boolean _isAssignable(PsiType lType, PsiType rType, PsiManager manager, GlobalSearchScope scope, boolean allowConversion) {
    if (lType == null || rType == null) {
      return false;
    }

    if (rType == PsiType.NULL && lType instanceof PsiPrimitiveType) return false;

    if (allowConversion) {
      //all numeric types are assignable
      if (isNumericType(lType)) {
        return isNumericType(rType) || rType == PsiType.NULL;
      }
      else if (typeEqualsToText(lType, JAVA_LANG_STRING)) {
        return true;
      }
    }

    rType = boxPrimitiveType(rType, manager, scope);
    lType = boxPrimitiveType(lType, manager, scope);

    return lType.isAssignableFrom(rType);
  }

  public static boolean isAssignableByMethodCallConversion(PsiType lType, PsiType rType, GroovyPsiElement context) {
    if (lType == null || rType == null) return false;

    if (isAssignableByMethodCallConversion(lType, rType, context.getManager(), context.getResolveScope())) {
      return true;
    }

    for (GrTypeConverter converter : GrTypeConverter.EP_NAME.getExtensions()) {
      final Boolean result = converter.isConvertible(lType, rType, context);
      if (result != null) {
        return result;
      }
    }

    return false;
  }

  public static boolean isAssignableByMethodCallConversion(PsiType lType, PsiType rType, PsiManager manager, GlobalSearchScope scope) {
    if (lType == null || rType == null) return false;

    if (rType instanceof GrTupleType) {
      final GrTupleType tuple = (GrTupleType)rType;
      if (tuple.getComponentTypes().length == 0) {
        if (lType instanceof PsiArrayType ||
            InheritanceUtil.isInheritor(lType, JAVA_UTIL_LIST) ||
            InheritanceUtil.isInheritor(lType, JAVA_UTIL_SET)) {
          return true;
        }
      }
    }

    if (typeEqualsToText(rType, GroovyCommonClassNames.GROOVY_LANG_GSTRING)) {
      if (isAssignable(lType, GroovyPsiManager.getInstance(manager.getProject()).createTypeByFQClassName(JAVA_LANG_STRING, scope), manager, scope)) {
        return true;
      }
    }

    if (isNumericType(lType) && isNumericType(rType)) {
      lType = unboxPrimitiveTypeWrapper(lType);
      if (typeEqualsToText(lType, JAVA_MATH_BIG_DECIMAL)) lType = PsiType.DOUBLE;
      rType = unboxPrimitiveTypeWrapper(rType);
      if (typeEqualsToText(rType, JAVA_MATH_BIG_DECIMAL)) rType = PsiType.DOUBLE;
    }
    else {
      if (rType == PsiType.NULL && lType instanceof PsiPrimitiveType) return false;

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

  public static PsiType unboxPrimitiveTypeWrapperAndEraseGenerics(PsiType result) {
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
        return GroovyPsiManager.getInstance(manager.getProject()).createTypeByFQClassName(boxedTypeName, resolveScope);
      }
    }

    return result;
  }

  @NotNull
  public static PsiClassType createType(String fqName, @NotNull PsiElement context) {
    return createTypeByFQClassName(fqName, context);
  }

  public static PsiClassType getJavaLangObject(PsiElement context) {
    return PsiType.getJavaLangObject(context.getManager(), context.getResolveScope());
  }

  @Nullable
  public static PsiType getLeastUpperBoundNullable(@Nullable PsiType type1, @Nullable PsiType type2, PsiManager manager) {
    if (type1 == null) return type2;
    if (type2 == null) return type1;
    if (type1.isAssignableFrom(type2)) return type1;
    if (type2.isAssignableFrom(type1)) return type2;
    return getLeastUpperBound(type1, type2, manager);
  }

  @Nullable
  public static PsiType getLeastUpperBoundNullable(@NotNull Iterable<PsiType> collection, @NotNull PsiManager manager) {
    Iterator<PsiType> iterator = collection.iterator();
    if (!iterator.hasNext()) return null;
    PsiType result = iterator.next();
    while (iterator.hasNext()) {
      result = getLeastUpperBoundNullable(result, iterator.next(), manager);
    }
    return result;
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
      GrClosureSignature signature1=clType1.getSignature();
      GrClosureSignature signature2=clType2.getSignature();

      GrClosureParameter[] parameters1 = signature1.getParameters();
      GrClosureParameter[] parameters2 = signature2.getParameters();

      if (parameters1.length == parameters2.length) {
        final GrClosureSignature signature = GrClosureSignatureImpl.getLeastUpperBound(signature1, signature2, manager);
        if (signature != null) {
          GlobalSearchScope scope = clType1.getResolveScope().intersectWith(clType2.getResolveScope());
          final LanguageLevel languageLevel = ComparatorUtil.max(clType1.getLanguageLevel(), clType2.getLanguageLevel());
          return GrClosureType.create(signature, manager, scope, languageLevel, true);
        }
      }
    }
    else if (GroovyCommonClassNames.GROOVY_LANG_GSTRING.equals(type1.getCanonicalText()) &&
             CommonClassNames.JAVA_LANG_STRING.equals(type2.getInternalCanonicalText())) {
      return type2;
    }
    else if (GroovyCommonClassNames.GROOVY_LANG_GSTRING.equals(type2.getCanonicalText()) &&
             CommonClassNames.JAVA_LANG_STRING.equals(type1.getInternalCanonicalText())) {
      return type1;
    }
    final PsiType result = getLeastUpperBoundForNumericType(type1, type2);
    if (result != null) return result;
    return GenericsUtil.getLeastUpperBound(type1, type2, manager);
  }

  @Nullable
  public static PsiType getPsiType(PsiElement context, IElementType elemType) {
    if (elemType == kNULL) {
      return PsiType.NULL;
    }
    final String typeName = getPsiTypeName(elemType);
    if (typeName != null) {
      return createTypeByFQClassName(typeName, context);
    }
    return null;
  }

  @Nullable
  public static String getPsiTypeName(IElementType elemType) {
    return ourPrimitiveTypesToClassNames.get(elemType);
  }

  @NotNull
  public static PsiType getLeastUpperBound(PsiClass[] classes, PsiManager manager) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());

    if (classes.length == 0) return factory.createTypeByFQClassName(JAVA_LANG_OBJECT);

    PsiType type = factory.createType(classes[0]);

    for (int i = 1; i < classes.length; i++) {
      PsiType t = getLeastUpperBound(type, factory.createType(classes[i]), manager);
      if (t != null) {
        type = t;
      }
    }

    return type;
  }


  public static boolean typeEqualsToText(@NotNull PsiType type, @NotNull String text) {
    return text.endsWith(type.getPresentableText()) && text.equals(type.getCanonicalText());
  }

  public static PsiSubstitutor composeSubstitutors(PsiSubstitutor s1, PsiSubstitutor s2) {
    final Map<PsiTypeParameter, PsiType> map = s1.getSubstitutionMap();
    Map<PsiTypeParameter, PsiType> result = new THashMap<PsiTypeParameter, PsiType>(map.size());
    for (PsiTypeParameter parameter : map.keySet()) {
      result.put(parameter, s2.substitute(map.get(parameter)));
    }
    return PsiSubstitutorImpl.createSubstitutor(result);
  }

  @NotNull
  public static PsiClassType createTypeByFQClassName(@NotNull String fqName, @NotNull PsiElement context) {
    return GroovyPsiManager.getInstance(context.getProject()).createTypeByFQClassName(fqName, context.getResolveScope());
  }

  @Nullable
  public static PsiType createJavaLangClassType(@Nullable PsiType type,
                                         Project project,
                                         GlobalSearchScope resolveScope) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiType result = null;
    PsiClass javaLangClass = facade.findClass(JAVA_LANG_CLASS, resolveScope);
    if (javaLangClass != null) {
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      final PsiTypeParameter[] typeParameters = javaLangClass.getTypeParameters();
      if (typeParameters.length == 1) {
        substitutor = substitutor.put(typeParameters[0], type);
      }
      result = facade.getElementFactory().createType(javaLangClass, substitutor);
    }
    return result;
  }
}
