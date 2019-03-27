package client;

import java.util.function.Supplier;

import library.RecentClass;
import library.OldClass;
import library.OldClassWithDefaultConstructor;

import library.RecentAnnotation;
import library.OldAnnotation;

import library.RecentKotlinClass;
import library.RecentKotlinUtilsKt;
import library.RecentKotlinAnnotation;

import library.OldKotlinClass;
import library.OldKotlinClassWithDefaultConstructor;
import library.OldKotlinAnnotation;

import kotlin.jvm.functions.Function0;

import static library.RecentClass.*;

class A {
  public <error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentClass</error> r = null;
  public <error descr="'library.RecentKotlinClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentKotlinClass</error> r2 = null;

  public void m1(OldClass oc, OldKotlinClass okc) {
    String s = oc.<error descr="'recentField' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentField</error>;
    String s2 = okc.<error descr="'recentField' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentField</error>;
  }

  public void m2(OldClass oc, OldKotlinClass okc) {
    oc.<error descr="'recentMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentMethod</error>();
    okc.<error descr="'recentMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentMethod</error>();
  }

  public void m3() {
    OldClass.<error descr="'recentStaticMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentStaticMethod</error>();
    OldKotlinClass.<error descr="'recentStaticMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentStaticMethod</error>();
  }

  public void m4() {
    Object o = new <error descr="'OldClass(java.lang.String)' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">OldClass</error>("");
    Object o2 = new <error descr="'OldKotlinClass(java.lang.String)' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">OldKotlinClass</error>("");
  }

  public void m5() {
    //anonymous class
    Object o = new <error descr="'OldClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">OldClass</error>() {
    };
  }

  public void m6() {
    Supplier<OldClass> s = OldClass::<error descr="'OldClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">new</error>;
    Supplier<OldKotlinClass> s2 = OldKotlinClass::<error descr="'OldKotlinClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">new</error>;
  }

  public void m7(String s) {
    RecentKotlinUtilsKt.<error descr="'recentTopLevelFunction()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentTopLevelFunction</error>();
    RecentKotlinUtilsKt.<error descr="'recentExtensionFunction(java.lang.String)' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentExtensionFunction</error>(s);
    RecentKotlinUtilsKt.<error descr="'recentInlineExtensionFunction(java.lang.String, kotlin.jvm.functions.Function0<java.lang.String>)' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentInlineExtensionFunction</error>(s, new Function0<String>() {
      @Override
      public String invoke() {
        return null;
      }
    });
  }
}

class <error descr="Default constructor in 'library.OldClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">B</error> extends OldClass {
  //implicit call to default "recent" constructor available in source code.
}

class <error descr="Default constructor in 'library.OldKotlinClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">BK</error> extends OldKotlinClass {
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

class CK extends OldKotlinClass {

  public CK() {
    //call old available constructor, to not produce warning here.
    super(1);
  }

  //overrides "recent" method.
  @Override
  public void <error descr="Overrides method in library.OldKotlinClass that is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentMethod</error>() { }
}

class <error descr="Default constructor in 'library.OldClassWithDefaultConstructor' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">D</error> extends OldClassWithDefaultConstructor {
  //implicit call to default "recent" constructor that is NOT available in source code.
}

class <error descr="Default constructor in 'library.OldKotlinClassWithDefaultConstructor' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">DK</error> extends OldKotlinClassWithDefaultConstructor {
  //implicit call to default "recent" constructor that is NOT available in source code.
}

//Class with constructors delegating to default "recent" constructor.
class E extends OldClassWithDefaultConstructor {
  public <error descr="Default constructor in 'library.OldClassWithDefaultConstructor' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">E</error>() {}

  public <error descr="Default constructor in 'library.OldClassWithDefaultConstructor' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">E</error>(int x) {
    super();
  }
}

//Class with constructors delegating to default "recent" constructor.
class EK extends OldKotlinClassWithDefaultConstructor {
  public <error descr="Default constructor in 'library.OldKotlinClassWithDefaultConstructor' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">EK</error>() {}

  public <error descr="Default constructor in 'library.OldKotlinClassWithDefaultConstructor' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">EK</error>(int x) {
    <error descr="'OldKotlinClassWithDefaultConstructor()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">super</error>();
  }
}

class F {

  @<error descr="'library.RecentAnnotation' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentAnnotation</error>
  public void m1() {
  }

  @<error descr="'library.RecentKotlinAnnotation' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentKotlinAnnotation</error>
  public void m1k() {
  }

  @OldAnnotation(
    oldParam = 0,
    <error descr="'recentParam' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentParam</error> = 1
  )
  public void m2() {
  }

  @OldKotlinAnnotation(
    oldParam = 0,
    <error descr="'recentParam' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentParam</error> = 1
  )
  public void m2k() {
  }

}