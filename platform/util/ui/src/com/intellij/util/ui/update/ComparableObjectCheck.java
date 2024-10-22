// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.update;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;


public final class ComparableObjectCheck {
  public static boolean equals(@NotNull ComparableObject me, Object him) {
    if (me == him) {
      return true;
    }

    else if (!(him instanceof ComparableObject)) {
      return false;
    }

    Object[] my = me.getEqualityObjects();
    Object[] his = ((ComparableObject) him).getEqualityObjects();
    if (his.length == 0) {
      return false;
    }
    return Arrays.deepEquals(my, his);
  }

  public static int hashCode(ComparableObject me, int superCode) {
    Object[] objects = me.getEqualityObjects();
    if (objects.length == 0) {
      return superCode;
    }

    int result = 0;
    for (Object object : objects) {
      result = 29 * result + (object != null ? object.hashCode() : 239);
    }
    return result;
  }

}
