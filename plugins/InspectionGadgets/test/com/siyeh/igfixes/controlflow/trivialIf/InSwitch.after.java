// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
class InCodeBlock {
  private static boolean original(boolean first, boolean second, int i) {
    switch (i) {
      case 0:
        return false;
      case 1:
        if (first)
            return second;
        return false;
      default:
        return true;
    }
  }
}