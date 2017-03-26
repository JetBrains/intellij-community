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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.*;

public interface TypeConstants {

  int BYTE_RANK = 1;
  int CHARACTER_RANK = 2;
  int SHORT_RANK = 3;
  int INTEGER_RANK = 4;
  int LONG_RANK = 5;
  int BIG_INTEGER_RANK = 6;
  int BIG_DECIMAL_RANK = 7;
  int FLOAT_RANK = 8;
  int DOUBLE_RANK = 9;

  static int getTypeRank(@Nullable PsiType type) {
    if (type instanceof PsiClassType) {
      return TYPE_TO_RANK.get(getQualifiedName(type));
    }
    else if (type instanceof PsiPrimitiveType) {
      return TYPE_TO_RANK.get(((PsiPrimitiveType)type).getBoxedTypeName());
    }
    return 0;
  }

  @Nullable
  static String getTypeFqn(int rank) {
    return RANK_TO_TYPE.get(rank);
  }
}
