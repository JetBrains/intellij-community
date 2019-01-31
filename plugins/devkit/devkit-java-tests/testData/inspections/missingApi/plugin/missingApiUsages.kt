package client

import java.util.function.Supplier

import library.RecentClass
import library.OldClass
import library.OldClassWithDefaultConstructor

import library.RecentAnnotation
import library.OldAnnotation

import library.RecentClass.*

class A {
  var r: <error descr="'library.RecentClass' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentClass</error>? = null

  fun m1(oc: OldClass): Any {
    return oc.<error descr="'recentField' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentField</error>
  }

  fun m2(oc: OldClass) {
    oc.<error descr="'recentMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentMethod</error>()
  }

  fun m3() {
    OldClass.<error descr="'recentStaticMethod()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentStaticMethod</error>()
  }

  fun m4(): Any {
    return <error descr="'OldClass(java.lang.String)' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">OldClass</error>("")
  }

  fun m5(): Any {
    //anonymous class
    return object : <error descr="'OldClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">OldClass</error>() {
    }
  }

  fun m6(): Any {
    return Supplier<OldClass> { <error descr="'OldClass()' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">OldClass</error>() }
  }
}

class B {

  @<error descr="'library.RecentAnnotation' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">RecentAnnotation</error>
  fun m1() {
  }

  @OldAnnotation(
    oldParam = 0,
    <error descr="'recentParam' is available only since 2.0 but the module is targeted for 1.0 - 999.0. It may lead to compatibility problems with IDEs prior to 2.0.">recentParam</error> = 1
  )
  fun m2() {
  }

}