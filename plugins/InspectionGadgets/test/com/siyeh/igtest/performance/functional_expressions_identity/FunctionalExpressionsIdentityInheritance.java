// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.igtest.performance.functional_expressions_identity;

import java.util.AbstractMap;
import java.util.Set;

public class FunctionalExpressionsIdentityInheritance {
  MyMap<MyFuncInterface, String> <warning descr="Map 'map' may contain key of functional interface type">map</warning> = new MyMap();
  MyMap <warning descr="Map 'map2' may contain key of functional interface type">map2</warning> = new MyMap();
  public void foo() {
    MyFuncInterface myFuncInterface = null;
    map2.put(myFuncInterface, "aaa");
  }

  public class MyMap<K,V> extends AbstractMap<K,V> {

    @Override
    public Set<Entry<K, V>> entrySet() {
      return null;
    }
    public V put(K key, V value) {
      return null;
    }
  }
}
