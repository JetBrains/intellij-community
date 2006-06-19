/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ipp.shift;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;

class ShiftUtils{

    private ShiftUtils(){
        super();
    }

    public static boolean isPowerOfTwo(PsiExpression rhs){
        if(!(rhs instanceof PsiLiteralExpression)){
            return false;
        }
        final PsiLiteralExpression literal = (PsiLiteralExpression) rhs;
        final Object value = literal.getValue();
        if(!(value instanceof Number)){
            return false;
        }
        if(value instanceof Double || value instanceof Float){
            return false;
        }
        int intValue = ((Number) value).intValue();
        if(intValue <= 0){
            return false;
        }
        while(intValue % 2 == 0){
            intValue >>= 1;
        }
        return intValue == 1;
    }

    public static int getLogBase2(PsiExpression rhs){
        final PsiLiteralExpression literal = (PsiLiteralExpression) rhs;
        final Object value = literal.getValue();
        int intValue = ((Number) value).intValue();
        int log = 0;
        while(intValue % 2 == 0){
            intValue >>= 1;
            log++;
        }
        return log;
    }

    public static int getExpBase2(PsiExpression rhs){
        final PsiLiteralExpression literal = (PsiLiteralExpression) rhs;
        final Object value = literal.getValue();
        final int intValue = ((Number) value).intValue();
        int exp = 1;
        for(int i = 0; i < intValue; i++){
            exp <<= 1;
        }
        return exp;
    }

    public static boolean isIntegral(PsiType lhsType){
        return lhsType != null &&
                (lhsType.equals(PsiType.INT)
                        || lhsType.equals(PsiType.SHORT)
                        || lhsType.equals(PsiType.LONG)
                        || lhsType.equals(PsiType.BYTE));
    }

    public static boolean isIntLiteral(PsiExpression rhs){
        if(!(rhs instanceof PsiLiteralExpression)){
            return false;
        }
        final PsiLiteralExpression literal = (PsiLiteralExpression) rhs;
        final Object value = literal.getValue();
        if(!(value instanceof Number)){
            return false;
        }
        return !(value instanceof Double) && !(value instanceof Float);
    }
}
