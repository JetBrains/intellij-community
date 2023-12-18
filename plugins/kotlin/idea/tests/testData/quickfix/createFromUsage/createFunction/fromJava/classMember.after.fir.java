// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// "Add method 'foo' to 'K'" "true"
class J {
    void test(K k) {
        boolean b = k.<caret>foo(1, "2");
    }
}