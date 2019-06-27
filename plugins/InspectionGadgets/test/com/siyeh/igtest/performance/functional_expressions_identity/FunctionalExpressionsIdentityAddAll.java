// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.igtest.performance.functional_expressions_identity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FunctionalExpressionsIdentityAddAll {
  public void foo() {
    Map<MyFuncInterface, String> <warning descr="Map 'map' may contain key of functional interface type">map</warning> = new HashMap<>();
    Map <warning descr="Map 'map2' may contain key of functional interface type">map2</warning> = new HashMap();
    Map<MyFuncInterface, String> <warning descr="Map 'map3' may contain key of functional interface type">map3</warning> = new HashMap<>();

    MyFuncInterface myFuncInterface = null;
    map3.put(myFuncInterface, "a");

    Map <MyFuncInterface, String> <warning descr="Map 'map4' may contain key of functional interface type">map4</warning> = new HashMap<>();
    map2.putAll(map);

    Set<MyFuncInterface> <warning descr="Set 'set' may contain key of functional interface type">set</warning> = new HashSet<>();
    Set set2 = new HashSet<>();
    Set <warning descr="Set 'set3' may contain key of functional interface type">set3</warning> = new HashSet();
    set3.addAll(set);
  }
}