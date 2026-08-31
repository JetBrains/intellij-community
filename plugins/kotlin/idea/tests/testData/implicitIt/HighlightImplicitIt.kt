// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun acceptL1(fn: (Int) -> Unit) {}
fun acceptL2(fn: (Int, Int) -> Unit) {}

fun nestLambdas() {
    acceptL1 {
        // More code.
        <info descr="null">acceptL1</info> {
            // And more code.
            acceptL2 { x, y ->
                // And more code.
                println(<info descr="null">~it</info>)
                acceptL1 {
                    it
                }
            }
        }
    }
}