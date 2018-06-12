// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
class InCodeBlock {
  public boolean test(String s) {
    {
      int i = Integer.parseInt(s);
      i<caret>f (i > /*in condition*/ 0) {
        //comment1
        return true;
      }
    }
    //comment2
    return false;
  }
}