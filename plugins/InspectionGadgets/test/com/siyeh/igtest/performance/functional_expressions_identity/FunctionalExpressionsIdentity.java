// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.igtest.performance.functional_expressions_identity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FunctionalExpressionsIdentity {
  public void foo() {
    Map<MyFuncInterface, String> <warning descr="Map 'hm1' may contain key of functional interface type">hm1</warning> = new HashMap<>();
    Map<String, MyFuncInterface> hm2 = new HashMap<>();
    Set<MyFuncInterface> <warning descr="Set 'set1' may contain key of functional interface type">set1</warning> = new HashSet<>();

    MyFuncInterface func = null;
    Map <warning descr="Map 'hm3' may contain key of functional interface type">hm3</warning> = new HashMap();
    hm3.put(func, "link");

    Map hm4 = new HashMap();
    hm4.put("link", func);

    Set <warning descr="Set 'set2' may contain key of functional interface type">set2</warning> = new HashSet();
    set2.add(func);

  }
}