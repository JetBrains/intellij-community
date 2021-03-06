// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.update;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;


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

    if (his.length == 0 || my.length != his.length) {
      return false;
    }

    for (int i = 0; i < my.length; i++) {
      if (!Comparing.equal(my[i], his[i])) {
        return false;
      }
    }

    return true;
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
