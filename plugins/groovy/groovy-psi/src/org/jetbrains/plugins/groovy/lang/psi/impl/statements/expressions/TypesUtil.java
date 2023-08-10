// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ComparatorUtil;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrImmediateClosureSignatureImpl;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.Position;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;

public final class TypesUtil implements TypeConstants {

  public static final PsiPrimitiveType[] PRIMITIVES = {
    PsiTypes.byteType(),
    PsiTypes.charType(),
    PsiTypes.doubleType(),
    PsiTypes.floatType(),
    PsiTypes.intType(),
    PsiTypes.shortType(),
    PsiTypes.longType(),
    PsiTypes.booleanType(),
    PsiTypes.voidType()
  };

  private TypesUtil() {
  }

  private static final Map<IElementType, String> ourPrimitiveTypesToClassNames = new HashMap<>();
  private static final @NlsSafe String NULL = "null";

  static {
    ourPrimitiveTypesToClassNames.put(STRING_SQ, CommonClassNames.JAVA_LANG_STRING);
    ourPrimitiveTypesToClassNames.put(STRING_TSQ, CommonClassNames.JAVA_LANG_STRING);
    ourPrimitiveTypesToClassNames.put(STRING_DQ, CommonClassNames.JAVA_LANG_STRING);
    ourPrimitiveTypesToClassNames.put(STRING_TDQ, CommonClassNames.JAVA_LANG_STRING);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mREGEX_LITERAL, CommonClassNames.JAVA_LANG_STRING);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL, CommonClassNames.JAVA_LANG_STRING);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mNUM_INT, CommonClassNames.JAVA_LANG_INTEGER);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mNUM_LONG, CommonClassNames.JAVA_LANG_LONG);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mNUM_FLOAT, CommonClassNames.JAVA_LANG_FLOAT);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mNUM_DOUBLE, CommonClassNames.JAVA_LANG_DOUBLE);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mNUM_BIG_INT, GroovyCommonClassNames.JAVA_MATH_BIG_INTEGER);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.mNUM_BIG_DECIMAL, GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kFALSE, CommonClassNames.JAVA_LANG_BOOLEAN);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kTRUE, CommonClassNames.JAVA_LANG_BOOLEAN);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kNULL, NULL);

    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kINT, CommonClassNames.JAVA_LANG_INTEGER);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kLONG, CommonClassNames.JAVA_LANG_LONG);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kFLOAT, CommonClassNames.JAVA_LANG_FLOAT);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kDOUBLE, CommonClassNames.JAVA_LANG_DOUBLE);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kBOOLEAN, CommonClassNames.JAVA_LANG_BOOLEAN);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kCHAR, CommonClassNames.JAVA_LANG_CHARACTER);
    ourPrimitiveTypesToClassNames.put(GroovyTokenTypes.kBYTE, CommonClassNames.JAVA_LANG_BYTE);
  }

  static final Object2IntMap<String> TYPE_TO_RANK = new Object2IntOpenHashMap<>();

  static {
    TYPE_TO_RANK.put(CommonClassNames.JAVA_LANG_BYTE, BYTE_RANK);
    TYPE_TO_RANK.put(CommonClassNames.JAVA_LANG_CHARACTER, CHARACTER_RANK);
    TYPE_TO_RANK.put(CommonClassNames.JAVA_LANG_SHORT, SHORT_RANK);
    TYPE_TO_RANK.put(CommonClassNames.JAVA_LANG_INTEGER, INTEGER_RANK);
    TYPE_TO_RANK.put(CommonClassNames.JAVA_LANG_LONG, LONG_RANK);
    TYPE_TO_RANK.put(GroovyCommonClassNames.JAVA_MATH_BIG_INTEGER, BIG_INTEGER_RANK);
    TYPE_TO_RANK.put(GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL, BIG_DECIMAL_RANK);
    TYPE_TO_RANK.put(CommonClassNames.JAVA_LANG_FLOAT, FLOAT_RANK);
    TYPE_TO_RANK.put(CommonClassNames.JAVA_LANG_DOUBLE, DOUBLE_RANK);
    TYPE_TO_RANK.put(CommonClassNames.JAVA_LANG_NUMBER, 10);
  }

  static final Int2ObjectMap<String> RANK_TO_TYPE;

  static {
    RANK_TO_TYPE = new Int2ObjectOpenHashMap<>(TYPE_TO_RANK.size());
    for (Object2IntMap.Entry<String> entry : TYPE_TO_RANK.object2IntEntrySet()) {
      RANK_TO_TYPE.put(entry.getIntValue(), entry.getKey());
    }
  }

  private static final List<PsiType> LUB_NUMERIC_TYPES = List.of(PsiTypes.byteType(),
                                                                 PsiTypes.shortType(),
                                                                 PsiTypes.intType(),
                                                                 PsiTypes.longType(),
                                                                 PsiTypes.floatType(),
                                                                 PsiTypes.doubleType());

  /**
   * @deprecated see {@link #canAssign}
   */
  @Deprecated
  public static boolean isAssignable(@Nullable PsiType lType, @Nullable PsiType rType, @NotNull PsiElement context) {
    if (lType == null || rType == null) {
      return false;
    }
    return canAssign(lType, rType, context, Position.ASSIGNMENT) == ConversionResult.OK;
  }

  @NotNull
  public static ConversionResult canAssign(@NotNull PsiType targetType,
                                           @NotNull PsiType actualType,
                                           @NotNull PsiElement context,
                                           @NotNull Position position) {
    if (actualType instanceof PsiIntersectionType) {
      ConversionResult min = ConversionResult.ERROR;
      for (PsiType child : ((PsiIntersectionType)actualType).getConjuncts()) {
        final ConversionResult result = canAssign(targetType, child, context, position);
        if (result.ordinal() < min.ordinal()) {
          min = result;
        }
        if (min == ConversionResult.OK) {
          return ConversionResult.OK;
        }
      }
      return min;
    }

    if (targetType instanceof PsiIntersectionType) {
      ConversionResult max = ConversionResult.OK;
      for (PsiType child : ((PsiIntersectionType)targetType).getConjuncts()) {
        final ConversionResult result = canAssign(child, actualType, context, position);
        if (result.ordinal() > max.ordinal()) {
          max = result;
        }
        if (max == ConversionResult.ERROR) {
          return ConversionResult.ERROR;
        }
      }
      return max;
    }

    final ConversionResult result = areTypesConvertible(targetType, actualType, context, position);
    if (result != null) return result;

    if (isAssignableWithoutConversions(targetType, actualType)) {
      return ConversionResult.OK;
    }

    final PsiManager manager = context.getManager();
    final GlobalSearchScope scope = context.getResolveScope();
    targetType = boxPrimitiveType(targetType, manager, scope);
    actualType = boxPrimitiveType(actualType, manager, scope);

    if (targetType.isAssignableFrom(actualType)) {
      return ConversionResult.OK;
    }

    return ConversionResult.ERROR;
  }

  public static boolean isAssignableByMethodCallConversion(@Nullable PsiType targetType,
                                                           @Nullable PsiType actualType,
                                                           @NotNull PsiElement context) {

    if (targetType == null || actualType == null) return false;
    return canAssign(targetType, actualType, context, Position.METHOD_PARAMETER) == ConversionResult.OK;
  }

  public static boolean isAssignableByParameter(@Nullable PsiType targetType,
                                                @Nullable PsiType actualType,
                                                @NotNull PsiElement context) {

    if (targetType == null || actualType == null) return false;
    return canAssign(targetType, actualType, context, Position.GENERIC_PARAMETER) == ConversionResult.OK;
  }

  @Nullable
  private static ConversionResult areTypesConvertible(@NotNull PsiType targetType,
                                                      @NotNull PsiType actualType,
                                                      @NotNull PsiElement context,
                                                      @NotNull Position position) {
    if (!(context instanceof GroovyPsiElement)) return null;
    if (targetType.equals(actualType)) return ConversionResult.OK;
    for (GrTypeConverter converter : GrTypeConverter.EP_NAME.getExtensions()) {
      if (!converter.isApplicableTo(position)) continue;
      final ConversionResult result = converter.isConvertible(targetType, actualType, position, (GroovyPsiElement)context);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  public static boolean isAssignableWithoutConversions(@Nullable PsiType lType, @Nullable PsiType rType) {
    if (lType == null || rType == null) return false;

    if (rType == PsiTypes.nullType()) {
      return !(lType instanceof PsiPrimitiveType);
    }

    if (rType instanceof GrTraitType) {
      for (PsiType type : ((GrTraitType)rType).getConjuncts()) {
        if (isAssignableWithoutConversions(lType, type)) return true;
      }
      return false;
    }

    if (isClassType(rType, GroovyCommonClassNames.GROOVY_LANG_GSTRING) && lType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      return true;
    }

    lType = optionalUnbox(lType);
    rType = optionalUnbox(rType);

    return TypeConversionUtil.isAssignable(lType, rType);
  }

  @NotNull
  public static ConversionResult canCast(@NotNull PsiType targetType, @NotNull PsiType actualType, @NotNull PsiElement context) {
    final ConversionResult result = areTypesConvertible(targetType, actualType, context, Position.EXPLICIT_CAST);
    if (result != null) return result;
    return TypeConversionUtil.areTypesConvertible(actualType, targetType) ? ConversionResult.OK : ConversionResult.ERROR;
  }

  @NotNull
  public static ConversionResult canAssignWithinMultipleAssignment(@NotNull PsiType targetType, @NotNull PsiType actualType) {
    return isAssignableWithoutConversions(targetType, actualType) ? ConversionResult.OK : ConversionResult.ERROR;
  }

  public static boolean isNumericType(@Nullable PsiType type) {
    if (type instanceof PsiClassType) {
      return TYPE_TO_RANK.containsKey(getQualifiedName(type));
    }

    return type instanceof PsiPrimitiveType && TypeConversionUtil.isNumericType(type);
  }

  public static boolean isIntegralNumberType(@Nullable PsiType type) {
    if (type instanceof PsiClassType) {
      int rank = TYPE_TO_RANK.getInt(getQualifiedName(type));
      return rank > 0 && rank <= BIG_INTEGER_RANK;
    }

    return type instanceof PsiPrimitiveType && TypeConversionUtil.isIntegralNumberType(type);
  }

  public static PsiType unboxPrimitiveTypeWrapperAndEraseGenerics(PsiType result) {
    return TypeConversionUtil.erasure(unboxPrimitiveTypeWrapper(result));
  }

  @NotNull
  public static PsiType optionalUnbox(@NotNull PsiType type) {
    PsiType unboxed = unboxPrimitiveTypeWrapper(type);
    return unboxed == null ? type : unboxed;
  }

  @Contract("null -> null")
  @Nullable
  public static PsiType unboxPrimitiveTypeWrapper(@Nullable PsiType type) {
    PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(type);
    return unboxed == null ? type : unboxed;
  }

  public static PsiType boxPrimitiveType(@Nullable PsiType result,
                                         @NotNull PsiManager manager,
                                         @NotNull GlobalSearchScope resolveScope,
                                         boolean boxVoid) {
    if (result instanceof PsiPrimitiveType primitive && (boxVoid || !PsiTypes.voidType().equals(result))) {
      String boxedTypeName = primitive.getBoxedTypeName();
      if (boxedTypeName != null) {
        return GroovyPsiManager.getInstance(manager.getProject()).createTypeByFQClassName(boxedTypeName, resolveScope);
      }
    }

    return result;
  }

  public static PsiType boxPrimitiveType(@Nullable PsiType result, @NotNull PsiManager manager, @NotNull GlobalSearchScope resolveScope) {
    return boxPrimitiveType(result, manager, resolveScope, false);
  }

  @NotNull
  public static PsiClassType createType(String fqName, @NotNull PsiElement context) {
    return createTypeByFQClassName(fqName, context);
  }

  @NotNull
  public static PsiClassType createType(@NotNull PsiClass clazz) {
    return createType(clazz, null);
  }

  @NotNull
  public static PsiClassType createType(@NotNull PsiClass clazz, @Nullable PsiElement context, PsiType... parameters) {
    return JavaPsiFacade.getInstance(
      (context == null ? clazz : context).getProject()
    ).getElementFactory().createType(clazz, parameters);
  }

  @NotNull
  public static PsiClassType getJavaLangObject(@NotNull PsiElement context) {
    return LazyFqnClassType.getLazyType(CommonClassNames.JAVA_LANG_OBJECT, context);
  }

  @Nullable
  public static PsiType getLeastUpperBoundNullable(@Nullable PsiType type1, @Nullable PsiType type2, @NotNull PsiManager manager) {
    if (type1 == null) return type2;
    if (type2 == null) return type1;
    return getLeastUpperBound(type1, type2, manager);
  }

  @Nullable
  public static PsiType getLeastUpperBoundNullable(@NotNull Iterable<? extends PsiType> collection, @NotNull PsiManager manager) {
    Iterator<? extends PsiType> iterator = collection.iterator();
    PsiType result = null;
    while (iterator.hasNext()) {
      result = getLeastUpperBoundNullable(result, iterator.next(), manager);
    }
    return result;
  }

  @Nullable
  public static PsiType getLeastUpperBound(@NotNull PsiType type1, @NotNull PsiType type2, @NotNull PsiManager manager) {
    if (type1 == PsiTypes.nullType()) return type2;
    if (type2 == PsiTypes.nullType()) return type1;
    {
      PsiType numericLUB = getNumericLUB(type1, type2);
      if (numericLUB != null) return numericLUB;
    }
    if (type1 instanceof GrTupleType tuple1 && type2 instanceof GrTupleType tuple2) {
      List<PsiType> components1 = tuple1.getComponentTypes();
      List<PsiType> components2 = tuple2.getComponentTypes();

      if (components1.isEmpty()) return genNewListBy(type2, manager);
      if (components2.isEmpty()) return genNewListBy(type1, manager);

      PsiType[] components3 = PsiType.createArray(Math.min(components1.size(), components2.size()));
      for (int i = 0; i < components3.length; i++) {
        PsiType c1 = components1.get(i);
        PsiType c2 = components2.get(i);
        if (c1 == null || c2 == null) {
          components3[i] = null;
        }
        else {
          components3[i] = getLeastUpperBound(c1, c2, manager);
        }
      }
      return new GrImmediateTupleType(Arrays.asList(components3), JavaPsiFacade.getInstance(manager.getProject()),
                                      tuple1.getResolveScope().intersectWith(tuple2.getResolveScope()));
    }
    else if (checkEmptyListAndList(type1, type2)) {
      return genNewListBy(type2, manager);
    }
    else if (checkEmptyListAndList(type2, type1)) {
      return genNewListBy(type1, manager);
    }
    else if (type1 instanceof GrMapType && type2 instanceof GrMapType) {
      return GrMapType.merge(((GrMapType)type1), ((GrMapType)type2));
    }
    else if (checkEmptyMapAndMap(type1, type2)) {
      return genNewMapBy(type2, manager);
    }
    else if (checkEmptyMapAndMap(type2, type1)) {
      return genNewMapBy(type1, manager);
    }
    else if (type1 instanceof GrClosureType clType1 && type2 instanceof GrClosureType clType2) {
      List<GrSignature> signatures1 = clType1.getSignatures();
      List<GrSignature> signatures2 = clType2.getSignatures();

      if (signatures1.size() == 1 && signatures2.size() == 1) {
        final GrSignature signature1 = signatures1.get(0);
        final GrSignature signature2 = signatures2.get(0);
        if (signature1.getParameterCount() == signature2.getParameterCount()) {
          final GrSignature signature = GrImmediateClosureSignatureImpl.getLeastUpperBound(signature1, signature2, manager);
          if (signature != null) {
            GlobalSearchScope scope = clType1.getResolveScope().intersectWith(clType2.getResolveScope());
            final LanguageLevel languageLevel = ComparatorUtil.max(clType1.getLanguageLevel(), clType2.getLanguageLevel());
            return GrClosureType.create(Collections.singletonList(signature), scope, JavaPsiFacade.getInstance(manager.getProject()),
                                        languageLevel, true);
          }
        }
      }
    }
    else if (GroovyCommonClassNames.GROOVY_LANG_GSTRING.equals(getQualifiedName(type1)) &&
             CommonClassNames.JAVA_LANG_STRING.equals(getQualifiedName(type2))) {
      return type2;
    }
    else if (GroovyCommonClassNames.GROOVY_LANG_GSTRING.equals(getQualifiedName(type2)) &&
             CommonClassNames.JAVA_LANG_STRING.equals(getQualifiedName(type1))) {
      return type1;
    }
    return GenericsUtil.getLeastUpperBound(type1, type2, manager);
  }

  @Nullable
  private static PsiType getNumericLUB(@Nullable PsiType type1, @Nullable PsiType type2) {
    PsiPrimitiveType unboxedType1 = PsiPrimitiveType.getOptionallyUnboxedType(type1);
    PsiPrimitiveType unboxedType2 = PsiPrimitiveType.getOptionallyUnboxedType(type2);
    if (unboxedType1 != null && unboxedType2 != null) {
      int i1 = LUB_NUMERIC_TYPES.indexOf(unboxedType1);
      int i2 = LUB_NUMERIC_TYPES.indexOf(unboxedType2);
      if (i1 >= 0 && i2 >= 0) {
        return i1 > i2 ? type1 : type2;
      }
    }
    return null;
  }

  private static boolean checkEmptyListAndList(PsiType type1, PsiType type2) {
    if (type1 instanceof GrTupleType) {
      List<PsiType> types = ((GrTupleType)type1).getComponentTypes();
      if (types.isEmpty() && InheritanceUtil.isInheritor(type2, CommonClassNames.JAVA_UTIL_LIST)) return true;
    }

    return false;
  }

  private static PsiType genNewListBy(PsiType genericOwner, @NotNull PsiManager manager) {
    PsiClass list =
      JavaPsiFacade.getInstance(manager.getProject()).findClass(CommonClassNames.JAVA_UTIL_LIST, genericOwner.getResolveScope());
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
    if (list == null) return factory.createTypeFromText(CommonClassNames.JAVA_UTIL_LIST, null);
    return factory.createType(list, PsiUtil.extractIterableTypeParameter(genericOwner, false));
  }

  private static boolean checkEmptyMapAndMap(PsiType type1, PsiType type2) {
    if (type1 instanceof GrMapType) {
      if (((GrMapType)type1).isEmpty() && InheritanceUtil.isInheritor(type2, CommonClassNames.JAVA_UTIL_MAP)) return true;
    }

    return false;
  }

  private static PsiType genNewMapBy(PsiType genericOwner, PsiManager manager) {
    PsiClass map =
      JavaPsiFacade.getInstance(manager.getProject()).findClass(CommonClassNames.JAVA_UTIL_MAP, genericOwner.getResolveScope());
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
    if (map == null) return factory.createTypeFromText(CommonClassNames.JAVA_UTIL_MAP, null);

    final PsiType key = PsiUtil.substituteTypeParameter(genericOwner, CommonClassNames.JAVA_UTIL_MAP, 0, false);
    final PsiType value = PsiUtil.substituteTypeParameter(genericOwner, CommonClassNames.JAVA_UTIL_MAP, 1, false);
    return factory.createType(map, key, value);
  }

  @Nullable
  public static PsiType getPsiType(PsiElement context, IElementType elemType) {
    if (elemType == GroovyTokenTypes.kNULL) {
      return PsiTypes.nullType();
    }
    final String typeName = getBoxedTypeName(elemType);
    if (typeName != null) {
      return createTypeByFQClassName(typeName, context);
    }
    return null;
  }

  @Nullable
  public static String getBoxedTypeName(IElementType elemType) {
    return ourPrimitiveTypesToClassNames.get(elemType);
  }

  @NotNull
  public static PsiType getLeastUpperBound(PsiType[] classes, PsiManager manager) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());

    if (classes.length == 0) return factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT);

    PsiType type = classes[0];

    for (int i = 1; i < classes.length; i++) {
      PsiType t = getLeastUpperBound(type, classes[i], manager);
      if (t != null) {
        type = t;
      }
    }

    return type;
  }

  @Contract("null, _ -> false")
  public static boolean isClassType(@Nullable PsiType type, @NotNull String qName) {
    return qName.equals(getQualifiedName(type));
  }

  @Contract("null, _ -> false")
  public static boolean isClassType(@Nullable PsiType type, String @NotNull ... names) {
    String fqn = getQualifiedName(type);
    if (fqn == null) return false;
    return ContainerUtil.or(names, it -> it.equals(fqn));
  }

  public static PsiSubstitutor composeSubstitutors(PsiSubstitutor s1, PsiSubstitutor s2) {
    final Map<PsiTypeParameter, PsiType> map = s1.getSubstitutionMap();
    Map<PsiTypeParameter, PsiType> result = new HashMap<>(map.size());
    for (PsiTypeParameter parameter : map.keySet()) {
      result.put(parameter, s2.substitute(map.get(parameter)));
    }
    final Map<PsiTypeParameter, PsiType> map2 = s2.getSubstitutionMap();
    for (PsiTypeParameter parameter : map2.keySet()) {
      if (!result.containsKey(parameter)) {
        result.put(parameter, map2.get(parameter));
      }
    }
    return PsiSubstitutor.createSubstitutor(result);
  }

  @NotNull
  public static PsiClassType createTypeByFQClassName(@NotNull String fqName, @NotNull PsiElement context) {
    return GroovyPsiManager.getInstance(context.getProject()).createTypeByFQClassName(fqName, context.getResolveScope());
  }

  @Nullable
  public static PsiType createJavaLangClassType(@Nullable PsiType type, @NotNull PsiElement context) {
    return createJavaLangClassType(type, context.getProject(), context.getResolveScope());
  }

  @Nullable
  public static PsiType createJavaLangClassType(@Nullable PsiType type,
                                                Project project,
                                                GlobalSearchScope resolveScope) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiType result = null;
    PsiClass javaLangClass = facade.findClass(CommonClassNames.JAVA_LANG_CLASS, resolveScope);
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

  @NotNull
  public static PsiPrimitiveType getPrimitiveTypeByText(String typeText) {
    for (final PsiPrimitiveType primitive : PRIMITIVES) {
      if (PsiTypes.voidType().equals(primitive)) {
        return primitive;
      }
      if (primitive.getCanonicalText().equals(typeText)) {
        return primitive;
      }
    }

    assert false : "Unknown primitive type";
    return null;
  }

  @NotNull
  public static PsiClassType createGenericType(@NotNull String fqn, @NotNull PsiElement context, @Nullable PsiType type) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getProject());
    GlobalSearchScope resolveScope = context.getResolveScope();
    PsiClass clazz = facade.findClass(fqn, resolveScope);
    if (clazz == null || clazz.getTypeParameters().length != 1) {
      return facade.getElementFactory().createTypeByFQClassName(fqn, resolveScope);
    }
    return type == null ? facade.getElementFactory().createType(clazz) : facade.getElementFactory().createType(clazz, type);
  }

  @NotNull
  public static PsiClassType createIterableType(@NotNull PsiElement context, @Nullable PsiType type) {
    return createGenericType(CommonClassNames.JAVA_LANG_ITERABLE, context, type);
  }

  @NotNull
  public static PsiClassType createListType(@NotNull PsiElement context, @Nullable PsiType type) {
    PsiType boxedType = boxPrimitiveType(type, context.getManager(), context.getResolveScope());
    return createGenericType(CommonClassNames.JAVA_UTIL_LIST, context, boxedType);
  }

  @NotNull
  public static PsiClassType createListType(@NotNull PsiClass elements) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(elements.getProject());
    return createGenericType(CommonClassNames.JAVA_UTIL_LIST, elements, facade.getElementFactory().createType(elements));
  }

  @NotNull
  public static PsiType createSetType(@NotNull PsiElement context, @NotNull PsiType type) {
    return createGenericType(CommonClassNames.JAVA_UTIL_SET, context, type);
  }

  public static boolean isAnnotatedCheckHierarchyWithCache(@NotNull PsiClass aClass, @NotNull String annotationFQN) {
    Map<String, PsiClass> classMap = ClassUtil.getSuperClassesWithCache(aClass);

    for (PsiClass psiClass : classMap.values()) {
      PsiModifierList modifierList = psiClass.getModifierList();
      if (modifierList != null) {
        if (modifierList.hasAnnotation(annotationFQN)) {
          return true;
        }
      }
    }

    return false;
  }

  @Nullable
  public static PsiType substituteAndNormalizeType(@Nullable PsiType type,
                                                   @NotNull PsiSubstitutor substitutor,
                                                   @Nullable SpreadState state, @NotNull GrExpression expression) {
    if (type == null) return null;
    type = substitutor.substitute(type);
    if (type == null) return null;
    type = PsiImplUtil.normalizeWildcardTypeByPosition(type, expression);
    type = SpreadState.apply(type, state, expression.getProject());
    return type;
  }

  @Nullable
  public static PsiType getItemType(@Nullable PsiType containerType) {
    if (containerType == null) return null;

    if (containerType instanceof PsiArrayType) return ((PsiArrayType)containerType).getComponentType();
    return PsiUtil.extractIterableTypeParameter(containerType, false);
  }

  @Nullable
  public static PsiType inferAnnotationMemberValueType(final GrAnnotationMemberValue value) {
    if (value instanceof GrExpression) {
      return ((GrExpression)value).getType();
    }

    else if (value instanceof GrAnnotation) {
      final PsiElement resolved = ((GrAnnotation)value).getClassReference().resolve();
      if (resolved instanceof PsiClass) {
        return JavaPsiFacade.getElementFactory(value.getProject()).createType((PsiClass)resolved, PsiSubstitutor.EMPTY);
      }

      return null;
    }

    else if (value instanceof GrAnnotationArrayInitializer) {
      return getTupleByAnnotationArrayInitializer((GrAnnotationArrayInitializer)value);
    }

    return null;
  }

  public static PsiType getTupleByAnnotationArrayInitializer(final GrAnnotationArrayInitializer value) {
    return new GrTupleType(value.getResolveScope(), JavaPsiFacade.getInstance(value.getProject())) {
      @NotNull
      @Override
      protected List<PsiType> inferComponents() {
        final GrAnnotationMemberValue[] initializers = value.getInitializers();
        return ContainerUtil.map(initializers, TypesUtil::inferAnnotationMemberValueType);
      }

      @Override
      public boolean isValid() {
        return value.isValid();
      }
    };
  }

  public static boolean resolvesTo(PsiType type, String fqn) {
    if (type instanceof PsiClassType) {
      final PsiClass resolved = ((PsiClassType)type).resolve();
      return resolved != null && fqn.equals(resolved.getQualifiedName());
    }
    return false;
  }

  @Nullable
  public static PsiType rawSecondGeneric(PsiType type, Project project) {
    if (!(type instanceof PsiClassType)) return null;

    final PsiClassType.ClassResolveResult result = ((PsiClassType)type).resolveGenerics();
    final PsiClass element = result.getElement();
    if (element == null) return null;

    final PsiType[] parameters = ((PsiClassType)type).getParameters();

    boolean changed = false;
    final PsiType[] newParameters = new PsiType[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      PsiType parameter = parameters[i];
      if (parameter == null) continue;

      final Ref<PsiType> newParam = new Ref<>();
      parameter.accept(new PsiTypeVisitorEx<>() {
        @Nullable
        @Override
        public Object visitClassType(@NotNull PsiClassType classType) {
          newParam.set(classType.rawType());
          return null;
        }

        @Nullable
        @Override
        public Object visitCapturedWildcardType(@NotNull PsiCapturedWildcardType capturedWildcardType) {
          newParam.set(capturedWildcardType.getWildcard().getBound());
          return null;
        }

        @Nullable
        @Override
        public Object visitWildcardType(@NotNull PsiWildcardType wildcardType) {
          newParam.set(wildcardType.getBound());
          return null;
        }
      });

      if (!newParam.isNull()) {
        changed = true;
        newParameters[i] = newParam.get();
      }
    }
    if (!changed) return null;
    return JavaPsiFacade.getElementFactory(project).createType(element, newParameters);
  }


  @Nullable
  public static PsiType rawWildcard(PsiType type, PsiElement context) {
    final PsiTypeMapper visitor = new GrTypeMapper(context) {
      @Override
      public PsiType visitCapturedWildcardType(@NotNull PsiCapturedWildcardType capturedWildcardType) {
        return getJavaLangObject(context);
      }

      @Override
      public PsiType visitWildcardType(@NotNull PsiWildcardType capturedWildcardType) {
        return getJavaLangObject(context);
      }
    };

    return type.accept(visitor);
  }

  public static boolean isPsiClassTypeToClosure(PsiType type) {
    if (!(type instanceof PsiClassType)) return false;

    final PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null) return false;

    return GroovyCommonClassNames.GROOVY_LANG_CLOSURE.equals(psiClass.getQualifiedName());
  }

  @Nullable
  @NlsSafe
  public static String getQualifiedName(@Nullable PsiType type) {
    if (type instanceof PsiClassType) {
      PsiClass resolved = ((PsiClassType)type).resolve();
      if (resolved instanceof PsiAnonymousClass) {
        return "anonymous " + getQualifiedName(((PsiAnonymousClass)resolved).getBaseClassType());
      }
      if (resolved != null) {
        return resolved.getQualifiedName();
      }
      else {
        return PsiNameHelper.getQualifiedClassName(type.getCanonicalText(), true);
      }
    }

    return null;
  }

  public static boolean isEnum(PsiType type) {
    if (type instanceof PsiClassType) {
      final PsiClass resolved = ((PsiClassType)type).resolve();
      return resolved != null && resolved.isEnum();
    }
    return false;
  }
}
