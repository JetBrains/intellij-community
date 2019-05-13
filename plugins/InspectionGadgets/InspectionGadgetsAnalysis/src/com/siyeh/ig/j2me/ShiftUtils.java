/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.j2me;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;

class ShiftUtils {

  private ShiftUtils() {
    super();
  }

  public static boolean isPowerOfTwo(PsiExpression rhs) {
    if (!(rhs instanceof PsiLiteralExpression)) {
      return false;
    }
    final PsiLiteralExpression literal = (PsiLiteralExpression)rhs;
    final Object value = literal.getValue();
    if (!(value instanceof Number)) {
      return false;
    }
    if (value instanceof Double || value instanceof Float) {
      return false;
    }
    int intValue = ((Number)value).intValue();
    if (intValue <= 1) {
      return false;
    }
    while (intValue % 2 == 0) {
      intValue >>= 1;
    }
    return intValue == 1;
  }

  public static int getLogBaseTwo(PsiLiteralExpression rhs) {
    final Object value = rhs.getValue();
    int log = 0;
    if (value == null) {
      return log;
    }
    int intValue = ((Number)value).intValue();
    while (intValue % 2 == 0) {
      intValue >>= 1;
      log++;
    }
    return log;
  }
}