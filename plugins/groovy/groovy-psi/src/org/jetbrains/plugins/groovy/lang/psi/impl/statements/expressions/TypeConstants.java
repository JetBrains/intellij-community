// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      return TYPE_TO_RANK.getInt(getQualifiedName(type));
    }
    else if (type instanceof PsiPrimitiveType) {
      return TYPE_TO_RANK.getInt(((PsiPrimitiveType)type).getBoxedTypeName());
    }
    return 0;
  }

  @Nullable
  static String getTypeFqn(int rank) {
    return RANK_TO_TYPE.get(rank);
  }
}
