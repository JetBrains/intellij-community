package plugin;

import java.util.function.Consumer;
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
  public <error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentClass</error> r = null;
  public <error descr="'library.RecentKotlinClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentKotlinClass</error> r2 = null;

  public void parameters(
    <error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentClass</error> rc,
    <error descr="'library.RecentKotlinClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentKotlinClass</error> rkc
  ) {
  }

  public void array() {
    <error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentClass</error>[] a1 = new <error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentClass</error>[0];
    <error descr="'library.RecentKotlinClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentKotlinClass</error>[] a2 = new <error descr="'library.RecentKotlinClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentKotlinClass</error>[0];
  }

  public void classAccess() {
    Class<<error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentClass</error>> o = <error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentClass</error>.class;
    Class<<error descr="'library.RecentKotlinClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentKotlinClass</error>> o2 = <error descr="'library.RecentKotlinClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentKotlinClass</error>.class;
  }

  public void m1(OldClass oc, OldKotlinClass okc) {
    String s = oc.<error descr="'recentField' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this field might have had a different full signature in the previous IDEs.">recentField</error>;
    String s2 = okc.<error descr="'recentField' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this field might have had a different full signature in the previous IDEs.">recentField</error>;
  }

  public void m2(OldClass oc, OldKotlinClass okc) {
    oc.<error descr="'recentMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">recentMethod</error>();
    okc.<error descr="'recentMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">recentMethod</error>();
  }

  public void m3() {
    OldClass.<error descr="'recentStaticMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">recentStaticMethod</error>();
    OldKotlinClass.<error descr="'recentStaticMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">recentStaticMethod</error>();
  }

  public void m4() {
    Object o = new <error descr="'OldClass(java.lang.String)' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">OldClass</error>("");
    Object o2 = new <error descr="'OldKotlinClass(java.lang.String)' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">OldKotlinClass</error>("");
  }

  public void m5() {
    //anonymous class
    Object o = new <error descr="'OldClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">OldClass</error>() {
    };
  }

  public void m6() {
    Supplier<OldClass> s = OldClass::<error descr="'OldClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">new</error>;
    Supplier<OldKotlinClass> s2 = OldKotlinClass::<error descr="'OldKotlinClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">new</error>;
  }

  public void m7(String s) {
    RecentKotlinUtilsKt.<error descr="'recentTopLevelFunction()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">recentTopLevelFunction</error>();
    RecentKotlinUtilsKt.<error descr="'recentExtensionFunction(java.lang.String)' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">recentExtensionFunction</error>(s);
    RecentKotlinUtilsKt.<error descr="'recentInlineExtensionFunction(java.lang.String, kotlin.jvm.functions.Function0<java.lang.String>)' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">recentInlineExtensionFunction</error>(s, new Function0<String>() {
      @Override
      public String invoke() {
        return null;
      }
    });
  }

  public void m8() {
    Consumer<OldClass> methodReference = OldClass::<error descr="'recentMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">recentMethod</error>;
  }
}

class <error descr="'OldClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">B</error> extends OldClass {
  //implicit call to empty "recent" constructor available in source code.
}

class <error descr="'OldKotlinClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">BK</error> extends OldKotlinClass {
  //implicit call to empty "recent" constructor available in source code.
}

class Overrider extends OldClass {

  public Overrider() {
    //call old available constructor, to not produce warning here.
    super(1);
  }

  //overrides "recent" method.
  @Override
  public void <error descr="Overrides method in 'library.OldClass' that is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that the overridden method might have had a different signature in the previous IDEs.">recentMethod</error>() { }
}

class KOverrider extends OldKotlinClass {

  public KOverrider() {
    //call old available constructor, to not produce warning here.
    super(1);
  }

  //overrides "recent" method.
  @Override
  public void <error descr="Overrides method in 'library.OldKotlinClass' that is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that the overridden method might have had a different signature in the previous IDEs.">recentMethod</error>() { }
}

//No warning should be produced, because the `Overrider.recentMethod` is not "recent" on its own.
class JavaNonDirectOverrideOfRecentMethod extends Overrider {
  @Override
  public void recentMethod() {
  }
}

//No warning should be produced, because the `KOverrider.recentMethod` is not "recent" on its own.
class KotlinNonDirectOverrideOfRecentMethod extends KOverrider {
  @Override
  public void recentMethod() {
  }
}

class <error descr="'OldClassWithDefaultConstructor()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">D</error> extends OldClassWithDefaultConstructor {
  //implicit call to default "recent" constructor that is NOT available in source code.
}

class <error descr="'OldKotlinClassWithDefaultConstructor()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">DK</error> extends OldKotlinClassWithDefaultConstructor {
  //implicit call to default "recent" constructor that is NOT available in source code.
}

//Class with constructors delegating to default "recent" constructor.
class E extends OldClassWithDefaultConstructor {
  public <error descr="'OldClassWithDefaultConstructor()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">E</error>() {}

  public E(int x) {
    <error descr="'OldClassWithDefaultConstructor()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">super</error>();
  }
}

//Class with constructors delegating to default "recent" constructor.
class EK extends OldKotlinClassWithDefaultConstructor {
  public <error descr="'OldKotlinClassWithDefaultConstructor()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">EK</error>() {}

  public EK(int x) {
    <error descr="'OldKotlinClassWithDefaultConstructor()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">super</error>();
  }
}

class F {

  @<error descr="'library.RecentAnnotation' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentAnnotation</error>
  public void m1() {
  }

  @<error descr="'library.RecentKotlinAnnotation' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentKotlinAnnotation</error>
  public void m1k() {
  }

  @OldAnnotation(
    oldParam = 0,
    <error descr="'recentParam' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">recentParam</error> = 1
  )
  public void m2() {
  }

  @OldKotlinAnnotation(
    oldParam = 0,
    <error descr="'recentParam' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">recentParam</error> = 1
  )
  public void m2k() {
  }

}