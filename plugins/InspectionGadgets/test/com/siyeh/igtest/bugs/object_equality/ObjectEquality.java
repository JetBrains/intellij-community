package com.siyeh.igtest.bugs.object_equality;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ObjectEquality {

  void test(boolean cond) { }

  void compareBoolean(boolean p1, boolean p2, Boolean w1, Boolean w2) {
    test(p1 == p2);
    test(p1 != p2);

    test(p1 == w2);
    test(w1 == p2);

    test(w1 <warning descr="Object values are compared using '==', not 'equals()'">==</warning> w2);
    test(w1 <warning descr="Object values are compared using '!=', not 'equals()'">!=</warning> w2);
  }

  void compareInt(int p1, int p2, Integer w1, Integer w2) {
    test(p1 == p2);
    test(p1 != p2);

    test(p1 == w2);
    test(w1 == p2);

    test(w1 == w2);
    test(w1 != w2);
  }

  void compareDouble(double p1, double p2, Double w1, Double w2) {
    test(p1 == p2);
    test(p1 != p2);

    test(p1 == w2);
    test(w1 == p2);

    test(w1 == w2);
    test(w1 != w2);
  }

  // The classes of the primitive wrappers are final, the intermediate class 'Number' isn't.
  void compareNumbers(Double dbl, Integer i, Number num, Object obj) {
    test(<error descr="Operator '==' cannot be applied to 'java.lang.Double', 'java.lang.Integer'">dbl == i</error>);
    test(dbl == num);
    test(dbl == obj);
    test(i == num);
    test(i == obj);
    test(num == obj);
  }

  // Strings are handled separately by StringEqualityInspection, so don't warn.
  void compareStrings(String s1, String s2, Object obj) {
    test(s1 == s2);
    test(s1 != s2);
    test(s1 == obj);
    test(obj == s2);
  }

  // The interface 'java.util.Collection' does not require 'equals' and 'hashCode'
  // to implement a value-based comparison.
  void compareCollections(Collection<String> coll1, Collection<String> coll2) {
    test(coll1 <warning descr="Object values are compared using '==', not 'equals()'">==</warning> coll2);
    test(coll1 <warning descr="Object values are compared using '!=', not 'equals()'">!=</warning> coll2);
    test(coll1 == null);
    test(coll1 != null);
    test(null == coll2);
    test(null != coll2);
  }

  // The interface 'java.util.List' defines a contract for its 'equals' and 'hashCode' methods,
  // therefore comparing lists with '==' is unusual.
  void compareLists(List<String> list1, List<String> list2) {
    test(list1 <warning descr="Object values are compared using '==', not 'equals()'">==</warning> list2);
    test(list1 <warning descr="Object values are compared using '!=', not 'equals()'">!=</warning> list2);
    test(list1 == null);
    test(list1 != null);
    test(null == list2);
    test(null != list2);
  }

  // The interface 'java.util.Set' defines a contract for its 'equals' and 'hashCode' methods,
  // therefore comparing sets with '==' is unusual.
  void compareSets(Set<String> set1, Set<String> set2) {
    test(set1 <warning descr="Object values are compared using '==', not 'equals()'">==</warning> set2);
    test(set1 <warning descr="Object values are compared using '!=', not 'equals()'">!=</warning> set2);
    test(set1 == null);
    test(set1 != null);
    test(null == set2);
    test(null != set2);
  }

  // The interface 'java.util.Map' defines a contract for its 'equals' and 'hashCode' methods,
  // therefore comparing maps with '==' is unusual.
  void compareMaps(Map<String, Object> map1, Map<String, Object> map2) {
    test(map1 <warning descr="Object values are compared using '==', not 'equals()'">==</warning> map2);
    test(map1 <warning descr="Object values are compared using '!=', not 'equals()'">!=</warning> map2);
    test(null == map2);
    test(null != map2);
    test(map1 == null);
    test(map1 != null);
    test(map1 ==<error descr="Expression expected">)</error>;
  }

  // Enum constants are interned, therefore they can be safely compared using '=='.
  void compareEnums(MyEnum enum1, MyEnum enum2, Object obj) {
    test(enum1 == enum2);
    test(obj == MyEnum.FIRST);
  }

  // java.lang.Class is final and does not implement a custom 'equals',
  // therefore '==' and 'equals' are equivalent.
  void compareClasses(Class class1, Class class2) {
    test(class1 == class2);
    test(char.class == char.class);
  }

  // A class having a private constructor is a hint that objects are interned,
  // thereby making '==' and 'equals' equivalent.
  void comparePrivateConstructor(MyPrivateConstructor priv1, MyPrivateConstructor priv2, Object obj) {
    test(priv1 == priv2);

    test(priv1 == obj);
    test(obj == priv1);
  }

  // Ensure that the inspection handles flipped operands in the same way.
  void compareOrder(MyFinal fin, MyClass cls, Object obj) {
    test(fin == obj);
    test(obj == fin);
    test(cls <warning descr="Object values are compared using '==', not 'equals()'">==</warning> obj);
    test(obj <warning descr="Object values are compared using '==', not 'equals()'">==</warning> cls);
  }

  // When comparing two expressions whose declared type is an interface,
  // it is generally unknown whether the dynamic types may be compared using '==' or not.
  void compareInterface(MyInterface i1, MyInterface i2, Object obj, MyFinal fin) {
    test(i1 <warning descr="Object values are compared using '==', not 'equals()'">==</warning> i2);

    test(i1 <warning descr="Object values are compared using '==', not 'equals()'">==</warning> obj);
    test(obj <warning descr="Object values are compared using '==', not 'equals()'">==</warning> i1);

    test(i1 == fin);
    test(fin == i1);
  }

  interface MyInterface {
  }

  class MyClass {
  }

  enum MyEnum {
    FIRST, SECOND, THIRD;
  }

  final class MyFinal implements MyInterface {
  }

  class MyPrivateConstructor {
    private MyPrivateConstructor() { }
  }

  class MyEnumTypeParameter<E extends Enum<E>> {
    void testTypeParameter(E a, E b) {
      test(a == b);
    }
  }
}