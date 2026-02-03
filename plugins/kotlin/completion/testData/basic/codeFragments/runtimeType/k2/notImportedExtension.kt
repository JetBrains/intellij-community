// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun foo(o: Any) {
    <caret>val a = 1
}

// INVOCATION_COUNT: 1
// EXIST: { itemText: "read", attributes: "grayed" }
// EXIST: { itemText: "write", attributes: "grayed" }


// RUNTIME_TYPE: java.util.concurrent.locks.ReentrantReadWriteLock
// IGNORE_K1