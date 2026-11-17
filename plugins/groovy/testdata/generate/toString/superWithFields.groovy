// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class Parent {
    String toString() { "parent" }
}

class Foo extends Parent {
    int count
    <caret>
}