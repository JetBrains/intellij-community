package client;

import java.util.function.Supplier;

import library.RecentClass;
import library.OldClass;
import library.OldClassWithDefaultConstructor;

import library.RecentAnnotation;
import library.OldAnnotation;

import static library.RecentClass.*;

class A {
  public <error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentClass</error> r = null;

  public void m1(OldClass oc) {
    String s = oc.<error descr="'recentField' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentField</error>;
  }

  public void m2(OldClass oc) {
    oc.<error descr="'recentMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentMethod</error>();
  }

  public void m3() {
    OldClass.<error descr="'recentStaticMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentStaticMethod</error>();
  }

  public void m4() {
    Object o = new <error descr="'OldClass(java.lang.String)' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">OldClass</error>("");
  }

  public void m5() {
    //anonymous class
    Object o = new <error descr="'OldClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">OldClass</error>() {
    };
  }

  public void m6() {
    Supplier<OldClass> s = OldClass::<error descr="'OldClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">new</error>;
  }
}

class <error descr="Default constructor in 'library.OldClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">B</error> extends OldClass {
  //implicit call to default "recent" constructor available in source code.
  }

class C extends OldClass {

  public C() {
    //call old available constructor, to not produce warning here.
    super(1);
  }

  //overrides "recent" method.
  @Override
  public void <error descr="Overrides method in library.OldClass that is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentMethod</error>() { }
}

class <error descr="Default constructor in 'library.OldClassWithDefaultConstructor' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">D</error> extends OldClassWithDefaultConstructor {
  //implicit call to default "recent" constructor that is NOT available in source code.
}

//Class with constructors delegating to default "recent" constructor.
class E extends OldClassWithDefaultConstructor {
  public <error descr="Default constructor in 'library.OldClassWithDefaultConstructor' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">E</error>() {}

  public E(int x) {
    super();
  }
}

class F {

  @<error descr="'library.RecentAnnotation' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentAnnotation</error>
  public void m1() {
  }

  @OldAnnotation(
    oldParam = 0,
    <error descr="'recentParam' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentParam</error> = 1
  )
  public void m2() {
  }

}