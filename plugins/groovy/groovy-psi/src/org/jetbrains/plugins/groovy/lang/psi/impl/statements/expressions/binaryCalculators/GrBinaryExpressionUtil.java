// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypeConstants.*;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createTypeByFQClassName;

public final class GrBinaryExpressionUtil {

  private static final int[] RANKS = new int[]{
    INTEGER_RANK, LONG_RANK, BIG_INTEGER_RANK, BIG_DECIMAL_RANK, DOUBLE_RANK
  };

  public static PsiType getDefaultNumericResultType(PsiType ltype, PsiType rtype, GrOperatorExpression e) {
    int lRank = getTypeRank(ltype);
    int rRank = getTypeRank(rtype);
    return getDefaultNumericResultType(lRank, rRank, e);
  }

  @Nullable
  public static PsiType getDefaultNumericResultType(int lRank, int rRank, PsiElement context) {
    int resultRank = getResultTypeRank(lRank, rRank);
    String fqn = getTypeFqn(resultRank);
    return fqn == null ? null : createTypeByFQClassName(fqn, context);
  }

  private static int getResultTypeRank(int lRank, int rRank) {
    for (int rank : RANKS) {
      if (lRank <= rank && rRank <= rank) {
        return rank;
      }
    }
    return 0;
  }

  public static PsiType createDouble(GrOperatorExpression e) {
    return getTypeByFQName(CommonClassNames.JAVA_LANG_DOUBLE, e);
  }

  public static PsiType createLong(GrOperatorExpression e) {
    return getTypeByFQName(CommonClassNames.JAVA_LANG_LONG, e);
  }

  public static PsiType createInteger(GrOperatorExpression e) {
    return getTypeByFQName(CommonClassNames.JAVA_LANG_INTEGER, e);
  }

  public static PsiType createBigDecimal(GrOperatorExpression e) {
    return getTypeByFQName(GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL, e);
  }

  public static boolean isBigDecimal(PsiType lType, PsiType rType) {
    return lType.equalsToText(GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL) || rType.equalsToText(GroovyCommonClassNames.JAVA_MATH_BIG_DECIMAL);
  }

  public static boolean isFloatOrDouble(PsiType ltype, PsiType rtype) {
    return ltype.equalsToText(CommonClassNames.JAVA_LANG_DOUBLE) || rtype.equalsToText(CommonClassNames.JAVA_LANG_DOUBLE) ||
           ltype.equalsToText(CommonClassNames.JAVA_LANG_FLOAT)  || rtype.equalsToText(CommonClassNames.JAVA_LANG_FLOAT);
  }

  public static boolean isLong(PsiType ltype, PsiType rtype) {
    return ltype.equalsToText(CommonClassNames.JAVA_LANG_LONG) || rtype.equalsToText(CommonClassNames.JAVA_LANG_LONG);
  }

  public static PsiType getTypeByFQName(String fqn, GrOperatorExpression e) {
    return createTypeByFQClassName(fqn, e);
  }
}
