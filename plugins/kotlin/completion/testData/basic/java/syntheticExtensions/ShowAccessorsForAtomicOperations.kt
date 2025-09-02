// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import java.util.concurrent.atomic.AtomicInteger

fun test(a: AtomicInteger) {
    a.<caret>
}

// EXIST: getAndDecrement
// EXIST: getAndIncrement
// EXIST: getAndSet
// EXIST: getAndUpdate
// EXIST: getAndAdd
// ABSENT: andDecrement
// ABSENT: andIncrement
// ABSENT: andSet
// ABSENT: andUpdate
// ABSENT: andAdd