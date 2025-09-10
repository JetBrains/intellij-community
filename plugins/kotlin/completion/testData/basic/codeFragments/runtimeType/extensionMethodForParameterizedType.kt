// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import java.util.concurrent.*

fun main(args: Array<String>) {
    processFuture(CompletableFuture<String>())
}

fun processFuture(f: Future<String>) {
    <caret>f.isDone
}

fun CompletableFuture<*>.extensionStar() {}

// should be included KTIJ-35532
fun CompletableFuture<String>.extensionString() {}

// should be included KTIJ-35532
fun CompletableFuture<Any>.extensionAny() {}

fun CompletableFuture<List<String>>.extensionList() {}

fun String.extensionNonApplicable() {}

// INVOCATION_COUNT: 1
// EXIST: extensionStar
// NOTHING_ELSE


// RUNTIME_TYPE: java.util.concurrent.CompletableFuture

// IGNORE_K1