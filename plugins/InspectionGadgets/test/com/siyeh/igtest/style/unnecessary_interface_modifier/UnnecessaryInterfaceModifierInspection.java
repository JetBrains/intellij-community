package com.siyeh.igtest.style.unnecessary_interface_modifier;

public <warning descr="Modifier 'abstract' is redundant for interfaces">abstract</warning> interface UnnecessaryInterfaceModifierInspection {
    <warning descr="Modifier 'public' is redundant for interface members">public</warning> <warning descr="Modifier 'static' is redundant for interface fields">static</warning> <warning descr="Modifier 'final' is redundant for interface fields">final</warning> int ONE = 1;
    int TWO = 2;

    <warning descr="Modifier 'public' is redundant for interface members">public</warning> <warning descr="Modifier 'abstract' is redundant for interface methods">abstract</warning> void foo();

    void foo2();

    <warning descr="Modifier 'public' is redundant for interface members">public</warning> <warning descr="Modifier 'abstract' is redundant for interfaces">abstract</warning> <warning descr="Modifier 'static' is redundant for inner interfaces">static</warning> interface Inner {

    }
}
interface Next {
    <warning descr="Modifier 'static' is redundant for inner interfaces">static</warning> interface Nested {}

    <warning descr="Modifier 'public' is redundant for interface members">public</warning> abstract <warning descr="Modifier 'static' is redundant for inner classes of interfaces">static</warning> class Inner {}
    <warning descr="Modifier 'public' is redundant for interface members">public</warning> <warning descr="Modifier 'abstract' is redundant for interfaces">abstract</warning> <warning descr="Modifier 'static' is redundant for inner interfaces">static</warning> interface Inner2 {}


    <warning descr="Modifier 'public' is redundant for interface members">public</warning> final class Sub extends Inner {}
}
class X {
  static <warning descr="Modifier 'transient' is redundant for a 'static' field">transient</warning> String s;
}
<warning descr="Modifier 'strictfp' is redundant on Java 17 and higher">strictfp</warning> class Y {

  <warning descr="Modifier 'strictfp' is redundant on Java 17 and higher">strictfp</warning> void y() {}
}