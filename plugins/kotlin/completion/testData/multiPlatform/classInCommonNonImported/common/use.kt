// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package use

fun use() {
    Foo<caret>
}

// ABSENT: { lookupString: Foo, module: testModule_JVM }
// ABSENT: { lookupString: Foo, module: testModule_JS }
// EXIST: { lookupString: Foo, module: testModule_Common }