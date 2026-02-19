// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

class NestedThroughCallInCorScope {

    suspend fun m() {
        coroutineScope {
            extracted()
        }
    }

    private fun extracted() {
        runBlocking { }
    }
}
