// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class A {
  def f (){
    boolean a
    boolean b
    if (<warning descr="Variable 'a' might not be assigned">a</warning> && <warning descr="Variable 'b' might not be assigned">b</warning>) {}
    if (a || <warning descr="Variable 'b' might not be assigned">b</warning>) {}
    if (a ==> <warning descr="Variable 'b' might not be assigned">b</warning>) {}
  }
}