// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package a;

public class Testing {
    public static void test() {
        Target t = new Target();
        t.set<caret>
    }
}
// EXIST: setName
// ABSENT: setValue
