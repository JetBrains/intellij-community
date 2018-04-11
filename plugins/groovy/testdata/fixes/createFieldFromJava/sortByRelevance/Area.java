// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class J {

  void bar() {
    new A().f<caret>ield.put("a", "b");
  }

}