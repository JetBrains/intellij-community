// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class A {
  boolean complexImplication() {
    def a = true
    def b = true
    def c = true
    def d = true
    return <warning descr="Overly complex boolean expression">a ==> b ==> c ==> d</warning>
  }

  boolean notComplexImplication() {
    def a = true
    def b = true
    def c = true
    return a ==> b ==> c
  }
}