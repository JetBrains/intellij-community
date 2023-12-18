// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// "Add method 'foo' to 'Dep'" "true"
// RUNTIME_WITH_JDK_10
class J {
    void test() {
        Dep dep = new Dep();
        var foo = dep.<selection><caret></selection>foo();
    }
}