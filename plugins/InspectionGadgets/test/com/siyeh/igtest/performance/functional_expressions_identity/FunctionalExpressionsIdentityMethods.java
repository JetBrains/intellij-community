// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.igtest.performance.functional_expressions_identity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FunctionalExpressionsIdentityMethods {
  public void foo() {
    MyFuncInterface funcI = null;
    Map <warning descr="Map 'map' may contain key of functional interface type">map</warning> = new HashMap();
    map.<error descr="Cannot resolve method 'putIfAbsent(com.siyeh.igtest.performance.functional_expressions_identity.MyFuncInterface, java.lang.String)'">putIfAbsent</error>(funcI, "");

    Map <warning descr="Map 'map2' may contain key of functional interface type">map2</warning> = new HashMap();
    map2.<error descr="Cannot resolve method 'compute(com.siyeh.igtest.performance.functional_expressions_identity.MyFuncInterface, <lambda expression>)'">compute</error>(funcI, (x,y) -> <error descr="Inconvertible types; cannot cast '<lambda parameter>' to 'java.lang.String'">(String)x</error>+y);

    Map <warning descr="Map 'map3' may contain key of functional interface type">map3</warning> = new HashMap();
    map3.<error descr="Cannot resolve method 'computeIfAbsent(com.siyeh.igtest.performance.functional_expressions_identity.MyFuncInterface, <lambda expression>)'">computeIfAbsent</error>(funcI,  key -> key + ", " + "amazing value");

    Map <warning descr="Map 'map4' may contain key of functional interface type">map4</warning> = new HashMap();
    map4.<error descr="Cannot resolve method 'computeIfPresent(com.siyeh.igtest.performance.functional_expressions_identity.MyFuncInterface, <lambda expression>)'">computeIfPresent</error>(funcI, (key, value) -> key + ", " + value);

    Map <warning descr="Map 'map5' may contain key of functional interface type">map5</warning> = new HashMap();
    map5.remove(funcI);

    Map <warning descr="Map 'map6' may contain key of functional interface type">map6</warning> = new HashMap();
    map6.<error descr="Cannot resolve method 'merge(com.siyeh.igtest.performance.functional_expressions_identity.MyFuncInterface, java.lang.String, <lambda expression>)'">merge</error>(funcI, " Blabla", (oldVal, newVal) -> <error descr="Inconvertible types; cannot cast '<lambda parameter>' to 'java.lang.String'">(String) oldVal</error> + newVal);

    Map <warning descr="Map 'map7' may contain key of functional interface type">map7</warning> = new HashMap();
    Integer value = (Integer) map7.<error descr="Cannot resolve method 'getOrDefault(com.siyeh.igtest.performance.functional_expressions_identity.MyFuncInterface, int)'">getOrDefault</error>(funcI, 0);

    Map <warning descr="Map 'map8' may contain key of functional interface type">map8</warning> = new HashMap();
    map8.<error descr="Cannot resolve method 'replace(com.siyeh.igtest.performance.functional_expressions_identity.MyFuncInterface, java.lang.String)'">replace</error>(funcI, "");

    Map <warning descr="Map 'map9' may contain key of functional interface type">map9</warning> = new HashMap();
    map9.containsKey(funcI);

    Map <warning descr="Map 'map10' may contain key of functional interface type">map10</warning> = new HashMap();
    map10.get(funcI);

    Set<MyFuncInterface> <warning descr="Set 'set0' may contain key of functional interface type">set0</warning> = new HashSet<>();
    set0.add(funcI);

    Set <warning descr="Set 'set1' may contain key of functional interface type">set1</warning> = new HashSet();
    set1.contains(funcI);

    Set <warning descr="Set 'set2' may contain key of functional interface type">set2</warning> = new HashSet();
    set2.remove(funcI);

    Set <warning descr="Set 'set3' may contain key of functional interface type">set3</warning> = new HashSet();
    set3.removeAll(set0);

    Set <warning descr="Set 'set4' may contain key of functional interface type">set4</warning> = new HashSet();
    set4.containsAll(set0);

    Set <warning descr="Set 'set5' may contain key of functional interface type">set5</warning> = new HashSet();
    set5.retainAll(set0);
  }
}
