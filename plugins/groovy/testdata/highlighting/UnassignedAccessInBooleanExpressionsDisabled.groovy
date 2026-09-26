// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class A {
  def f (){
    boolean a
    boolean b
    if (a && b) {}
    if (a || b) {}
    if (a ==> b) {}
  }
}