/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * Created by Max Medvedev on 12/20/13
 */
public class GrBinaryExpressionUtil {
  @Nullable
  public static PsiType getRightType(GrBinaryFacade e) {
    final GrExpression rightOperand = e.getRightOperand();
    return rightOperand == null ? null : rightOperand.getType();
  }

  @Nullable
  public static PsiType getLeftType(GrBinaryFacade e) {
    return e.getLeftOperand().getType();
  }

  public static PsiType getDefaultNumericResultType(PsiType ltype, PsiType rtype, GrBinaryFacade e) {
    if (isBigDecimal(ltype, rtype)) return createBigDecimal(e);
    if (isFloatOrDouble(ltype, rtype)) return createDouble(e);
    if (isLong(ltype, rtype)) return createLong(e);
    return createInteger(e);
  }

  public static PsiType createDouble(GrBinaryFacade e) {
    return getTypeByFQName(CommonClassNames.JAVA_LANG_DOUBLE, e);
  }

  public static PsiType createLong(GrBinaryFacade e) {
    return getTypeByFQName(CommonClassNames.JAVA_LANG_LONG, e);
  }

  public static PsiType createInteger(GrBinaryFacade e) {
    return getTypeByFQName(CommonClassNames.JAVA_LANG_INTEGER, e);
  }

  public static PsiType createBigDecimal(GrBinaryFacade e) {
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

  public static PsiType getTypeByFQName(String fqn, GrBinaryFacade e) {
    return TypesUtil.createTypeByFQClassName(fqn, e.getPsiElement());
  }
}
