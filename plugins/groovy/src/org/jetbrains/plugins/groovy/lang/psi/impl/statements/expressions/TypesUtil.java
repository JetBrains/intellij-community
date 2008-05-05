package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Map;

/**
 * @author ven
 */
public class TypesUtil {
  @NonNls
  public static final Map<String, PsiType> ourQNameToUnboxed = new HashMap<String, PsiType>();

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
      return binaryExpression.getManager().getElementFactory().createTypeByFQClassName(qName, scope);
    }

    return getOverloadedOperatorType(lType, binaryExpression.getOperationTokenType(), binaryExpression, new PsiType[]{rType});
  }

  public static PsiType getOverloadedOperatorType(PsiType thisType, IElementType tokenType,
                                                  GroovyPsiElement place, PsiType[] argumentTypes) {
    return getOverloadedOperatorType(thisType, ourOperationsToOperatorNames.get(tokenType), place, argumentTypes);
  }

  public static PsiType getOverloadedOperatorType(PsiType thisType, String operatorName,
                                                  GroovyPsiElement place, PsiType[] argumentTypes) {
    if (operatorName != null) {
      MethodResolverProcessor processor = new MethodResolverProcessor(operatorName, place, false, thisType, argumentTypes, PsiType.EMPTY_ARRAY);
      if (thisType instanceof PsiClassType) {
        final PsiClass lClass = ((PsiClassType) thisType).resolve();
        if (lClass != null) {
          lClass.processDeclarations(processor, PsiSubstitutor.EMPTY, null, place);
        }
      }

      ResolveUtil.processNonCodeMethods(thisType, processor, place.getProject());
      final GroovyResolveResult[] candidates = processor.getCandidates();
      if (candidates.length == 1) {
        final PsiElement element = candidates[0].getElement();
        if (element instanceof PsiMethod) {
          return candidates[0].getSubstitutor().substitute(((PsiMethod) element).getReturnType());
        }
      }
    }
    return null;
  }

  private static final Map<IElementType, String> ourOperationsToOperatorNames = new HashMap<IElementType, String>();

  static {
    ourOperationsToOperatorNames.put(GroovyTokenTypes.mPLUS, "plus");
    ourOperationsToOperatorNames.put(GroovyTokenTypes.mMINUS, "minus");
    ourOperationsToOperatorNames.put(GroovyTokenTypes.mBAND, "and");
    ourOperationsToOperatorNames.put(GroovyTokenTypes.mBOR, "or");
    ourOperationsToOperatorNames.put(GroovyTokenTypes.mBXOR, "xor");
    ourOperationsToOperatorNames.put(GroovyTokenTypes.mDIV, "div");
    ourOperationsToOperatorNames.put(GroovyTokenTypes.mMOD, "mod");
    ourOperationsToOperatorNames.put(GroovyTokenTypes.mSTAR, "multiply");
    ourOperationsToOperatorNames.put(GroovyTokenTypes.mDEC, "previous");
    ourOperationsToOperatorNames.put(GroovyTokenTypes.mINC, "next");
  }

  private static final TObjectIntHashMap<String> TYPE_TO_RANK = new TObjectIntHashMap<String>();

  static {
    TYPE_TO_RANK.put("java.lang.Byte", 1);
    TYPE_TO_RANK.put("java.lang.Short", 2);
    TYPE_TO_RANK.put("java.lang.Character", 2);
    TYPE_TO_RANK.put("java.lang.Integer", 3);
    TYPE_TO_RANK.put("java.lang.Long", 4);
    TYPE_TO_RANK.put("java.math.BigInteger", 5);
    TYPE_TO_RANK.put("java.math.BigDecimal", 6);
    TYPE_TO_RANK.put("java.lang.Float", 7);
    TYPE_TO_RANK.put("java.lang.Double", 8);
  }

  static {
    ourQNameToUnboxed.put("java.lang.Boolean", PsiType.BOOLEAN);
    ourQNameToUnboxed.put("java.lang.Byte", PsiType.BYTE);
    ourQNameToUnboxed.put("java.lang.Character", PsiType.CHAR);
    ourQNameToUnboxed.put("java.lang.Short", PsiType.SHORT);
    ourQNameToUnboxed.put("java.lang.Integer", PsiType.INT);
    ourQNameToUnboxed.put("java.lang.Long", PsiType.LONG);
    ourQNameToUnboxed.put("java.lang.Float", PsiType.FLOAT);
    ourQNameToUnboxed.put("java.lang.Double", PsiType.DOUBLE);
  }


  private static final TIntObjectHashMap<String> RANK_TO_TYPE = new TIntObjectHashMap<String>();

  static {
    RANK_TO_TYPE.put(1, "java.lang.Integer");
    RANK_TO_TYPE.put(2, "java.lang.Integer");
    RANK_TO_TYPE.put(3, "java.lang.Integer");
    RANK_TO_TYPE.put(4, "java.lang.Long");
    RANK_TO_TYPE.put(5, "java.lang.BigInteger");
    RANK_TO_TYPE.put(6, "java.math.BigDecimal");
    RANK_TO_TYPE.put(7, "java.lang.Double");
  }

  public static boolean isAssignable(PsiType lType, PsiType rType, PsiManager manager, GlobalSearchScope scope) {
    //all numeric types are assignable
    if (isNumericType(lType)) {
      return isNumericType(rType) || rType.equalsToText("java.lang.String") || rType.equals(PsiType.NULL);
    } else {
      if (lType.equalsToText("java.lang.String") && isNumericType(rType)) return true;
      rType = boxPrimitiveType(rType, manager, scope);
      lType = boxPrimitiveType(lType, manager, scope);
    }

    return lType.isAssignableFrom(rType);
  }

  public static boolean isAssignableByMethodCallConversion(PsiType lType, PsiType rType, PsiManager manager, GlobalSearchScope scope) {
    if (lType == null || rType == null) return false;

    if (isNumericType(lType) && isNumericType(rType)) {
      lType = unboxPrimitiveTypeWrapper(lType);
      if (lType.equalsToText("java.math.BigDecimal")) lType = PsiType.DOUBLE;
      rType = unboxPrimitiveTypeWrapper(rType);
      if (rType.equalsToText("java.math.BigDecimal")) rType = PsiType.DOUBLE;
    } else {
      rType = boxPrimitiveType(rType, manager, scope);
      lType = boxPrimitiveType(lType, manager, scope);
    }

    return TypeConversionUtil.isAssignable(lType, rType);

  }

  public static boolean isNumericType(PsiType type) {
    if (type instanceof PsiClassType) {
      return TYPE_TO_RANK.contains(type.getCanonicalText());
    }

    return type instanceof PsiPrimitiveType &&
        TypeConversionUtil.isNumericType(type);
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

  public static PsiType boxPrimitiveTypeAndEraseGenerics(PsiType result, PsiManager manager, GlobalSearchScope resolveScope) {
    if (result instanceof PsiPrimitiveType) {
      PsiPrimitiveType primitive = (PsiPrimitiveType) result;
      String boxedTypeName = primitive.getBoxedTypeName();
      if (boxedTypeName != null) {
        return manager.getElementFactory().createTypeByFQClassName(boxedTypeName, resolveScope);
      }
    }

    return TypeConversionUtil.erasure(result);
  }

  public static PsiType boxPrimitiveType(PsiType result, PsiManager manager, GlobalSearchScope resolveScope) {
    if (result instanceof PsiPrimitiveType) {
      PsiPrimitiveType primitive = (PsiPrimitiveType) result;
      String boxedTypeName = primitive.getBoxedTypeName();
      if (boxedTypeName != null) {
        return manager.getElementFactory().createTypeByFQClassName(boxedTypeName, resolveScope);
      }
    }

    return result;
  }

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
    PsiManager manager = context.getManager();
    return manager.getElementFactory().createTypeByFQClassName(fqName, context.getResolveScope());
  }

  public static PsiClassType getJavaLangObject(GroovyPsiElement context) {
    return PsiType.getJavaLangObject(context.getManager(), context.getResolveScope());
  }

  public static PsiType getLeastUpperBound(PsiType type1, PsiType type2, PsiManager manager) {
    if (type1 instanceof GrTupleType && type2 instanceof GrTupleType) {
      GrTupleType tuple1 = (GrTupleType) type1;
      GrTupleType tuple2 = (GrTupleType) type2;
      PsiType[] components1 = tuple1.getComponentTypes();
      PsiType[] components2 = tuple2.getComponentTypes();
      PsiType[] components3 = new PsiType[Math.min(components1.length, components2.length)];
      for (int i = 0; i < components3.length; i++) {
        PsiType c1 = components1[i];
        PsiType c2 = components2[i];
        if (c1 == null || c2 == null) {
          components3[i] = null;
        } else {
          components3[i] = getLeastUpperBound(c1, c2, manager);
        }
      }
      return new GrTupleType(components3, manager, tuple1.getScope().intersectWith(tuple2.getResolveScope()));
    } else if (type1 instanceof GrClosureType && type2 instanceof GrClosureType) {
      GrClosureType clType1 = (GrClosureType) type1;
      GrClosureType clType2 = (GrClosureType) type2;
      PsiType[] parameterTypes1 = clType1.getClosureParameterTypes();
      PsiType[] parameterTypes2 = clType2.getClosureParameterTypes();
      if (parameterTypes1.length == parameterTypes2.length) {
        PsiType[] paramTypes = new PsiType[parameterTypes1.length];
        boolean[] opts = new boolean[parameterTypes1.length];
        for (int i = 0; i < paramTypes.length; i++) {
          paramTypes[i] = GenericsUtil.getGreatestLowerBound(parameterTypes1[i], parameterTypes2[i]);
          opts[i] = clType1.isOptionalParameter(i) && clType2.isOptionalParameter(i);
        }
        PsiType returnType = getLeastUpperBound(clType1.getClosureReturnType(), clType2.getClosureReturnType(), manager);
        GlobalSearchScope scope = clType1.getResolveScope().intersectWith(clType2.getResolveScope());
        return GrClosureType.create(returnType, paramTypes, opts, manager, scope);
      }
    }

    return GenericsUtil.getLeastUpperBound(type1, type2, manager);
  }
}
