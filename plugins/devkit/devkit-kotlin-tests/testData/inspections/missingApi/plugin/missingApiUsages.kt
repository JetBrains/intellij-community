package client

import java.util.function.Supplier

import library.RecentClass
import library.RecentKotlinClass

import library.RecentInterface
import library.RecentKotlinInterface

import library.RecentSamInterface

import library.OldClass
import library.OldKotlinClass

import library.OldClassWithDefaultConstructor
import library.OldKotlinClassWithDefaultConstructor

import library.RecentAnnotation
import library.RecentKotlinAnnotation

import library.OldAnnotation
import library.OldKotlinAnnotation

class A {
  var r: <error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentClass</error>? = null
  var kr: <error descr="'library.RecentKotlinClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentKotlinClass</error>? = null

  @Suppress("UNUSED_PARAMETER")
  fun parameters(
    rc: <error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentClass</error>,
    krc: <error descr="'library.RecentKotlinClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentKotlinClass</error>
  ) {
  }

  fun arrays() {
    arrayOfNulls<<error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentClass</error>>(0)
    arrayOfNulls<<error descr="'library.RecentKotlinClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentKotlinClass</error>>(0)
  }

  fun classAccess() {
    <error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentClass</error>::class.java
    <error descr="'library.RecentKotlinClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentKotlinClass</error>::class.java
  }

  fun m1(oc: OldClass, okc: OldKotlinClass) {
    oc.<error descr="'recentField' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this field might have had a different full signature in the previous IDEs.">recentField</error>
    okc.<error descr="'recentField' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this field might have had a different full signature in the previous IDEs.">recentField</error>
  }

  fun m2(oc: OldClass, okc: OldKotlinClass) {
    oc.<error descr="'recentMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">recentMethod</error>()
    okc.<error descr="'recentMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">recentMethod</error>()
  }

  fun m3() {
    OldClass.<error descr="'recentStaticMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">recentStaticMethod</error>()
    OldKotlinClass.<error descr="'recentStaticMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">recentStaticMethod</error>()
  }

  fun m4() {
    <error descr="'OldClass(java.lang.String)' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">OldClass</error>("")
    <error descr="'OldKotlinClass(java.lang.String)' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">OldKotlinClass</error>("")
  }

  fun anonymousClasses() {
    object : <error descr="'OldClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">OldClass</error>(), <error descr="'library.RecentInterface' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentInterface</error> {
    }
    object : <error descr="'OldKotlinClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">OldKotlinClass</error>(), <error descr="'library.RecentKotlinInterface' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentKotlinInterface</error> {
    }
  }

  fun m6() {
    Supplier<OldClass> { <error descr="'OldClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">OldClass</error>() }
    Supplier<OldKotlinClass> { <error descr="'OldKotlinClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">OldKotlinClass</error>() }
  }

  fun singleAbstractMethod() {
    <error descr="'library.RecentSamInterface' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentSamInterface</error> { 42 }
  }
}

open class Overrider : OldClass(42) {
  //overrides "recent" method.
  override fun <error descr="Overrides method in 'library.OldClass' that is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that the overridden method might have had a different signature in the previous IDEs.">recentMethod</error>() { }
}

open class KOverrider : OldKotlinClass(42) {
  //overrides "recent" method.
  override fun <error descr="Overrides method in 'library.OldKotlinClass' that is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that the overridden method might have had a different signature in the previous IDEs.">recentMethod</error>() { }
}

//No warning should be produced, because the `Overrider.recentMethod` is not "recent" on its own.
class JavaNonDirectOverrideOfRecentMethod : Overrider() {
  override fun recentMethod() { }
}

//No warning should be produced, because the `KOverrider.recentMethod` is not "recent" on its own.
class KotlinNonDirectOverrideOfRecentMethod : KOverrider() {
  override fun recentMethod() { }
}

class B {

  @<error descr="'library.RecentAnnotation' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentAnnotation</error>
  fun markedWithAnnotation() {
  }

  @<error descr="'library.RecentKotlinAnnotation' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this type might have had a different fully qualified name in the previous IDEs.">RecentKotlinAnnotation</error>
  fun markedWithKotlinAnnotation() {
  }

  @OldAnnotation(
    oldParam = 0,
    <error descr="'recentParam' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0. Note that this method might have had a different full signature in the previous IDEs.">recentParam</error> = 1
  )
  fun recentAnnotationParam() {
  }
}