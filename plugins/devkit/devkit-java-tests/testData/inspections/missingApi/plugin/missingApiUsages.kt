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

import library.RecentClass.*
import library.*

class A {
  var r: <error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentClass</error>? = null
  var kr: <error descr="'library.RecentKotlinClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentKotlinClass</error>? = null

  @Suppress("UNUSED_PARAMETER")
  fun parameters(
    rc: <error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentClass</error>,
    krc: <error descr="'library.RecentKotlinClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentKotlinClass</error>
  ) {
  }

  fun arrays() {
    arrayOfNulls<<error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentClass</error>>(0)
    arrayOfNulls<<error descr="'library.RecentKotlinClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentKotlinClass</error>>(0)
  }

  fun classAccess() {
    <error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentClass</error>::class.java
    <error descr="'library.RecentKotlinClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentKotlinClass</error>::class.java
  }

  fun m1(oc: OldClass, okc: OldKotlinClass) {
    oc.<error descr="'recentField' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentField</error>
    okc.<error descr="'recentField' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentField</error>
  }

  fun m2(oc: OldClass, okc: OldKotlinClass) {
    oc.<error descr="'recentMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentMethod</error>()
    okc.<error descr="'recentMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentMethod</error>()
  }

  fun m3() {
    OldClass.<error descr="'recentStaticMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentStaticMethod</error>()
    OldKotlinClass.<error descr="'recentStaticMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentStaticMethod</error>()
  }

  fun m4() {
    <error descr="'OldClass(java.lang.String)' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">OldClass</error>("")
    <error descr="'OldKotlinClass(java.lang.String)' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">OldKotlinClass</error>("")
  }

  fun anonymousClasses() {
    object : <error descr="'OldClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">OldClass()</error>, <error descr="'library.RecentInterface' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentInterface</error> {
    }
    object : <error descr="'OldKotlinClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">OldKotlinClass()</error>, <error descr="'library.RecentKotlinInterface' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentKotlinInterface</error> {
    }
  }

  fun m6() {
    Supplier<OldClass> { <error descr="'OldClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">OldClass</error>() }
    Supplier<OldKotlinClass> { <error descr="'OldKotlinClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">OldKotlinClass</error>() }
  }

  fun topLevelFunction(s: String) {
    <error descr="'recentTopLevelFunction()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentTopLevelFunction</error>()
    s.<error descr="'recentExtensionFunction(java.lang.String)' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentExtensionFunction</error>()
    s.<error descr="'recentInlineExtensionFunction(java.lang.String, kotlin.jvm.functions.Function0<java.lang.String>)' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentInlineExtensionFunction</error> { "" }
  }

  fun singleAbstractMethod() {
    <error descr="'library.RecentSamInterface' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentSamInterface</error> { 42 }
  }
}

class Overrider : OldClass(42) {
  //overrides "recent" method.
  override fun <error descr="Overrides method in library.OldClass that is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentMethod</error>() { }
}

class KOverrider : OldKotlinClass(42) {
  //overrides "recent" method.
  override fun <error descr="Overrides method in library.OldKotlinClass that is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentMethod</error>() { }
}

class B {

  @<error descr="'library.RecentAnnotation' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentAnnotation</error>
  fun markedWithAnnotation() {
  }

  @<error descr="'library.RecentKotlinAnnotation' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentKotlinAnnotation</error>
  fun markedWithKotlinAnnotation() {
  }

  @OldAnnotation(
    oldParam = 0,
    <error descr="'recentParam' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentParam</error> = 1
  )
  fun recentAnnotationParam() {
  }

  @OldKotlinAnnotation(
    oldParam = 0,
    <error descr="'recentParam' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentParam</error> = 1
  )
  fun recentKotlinAnnotationParam() {
  }

}