// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import kotlinx.coroutines.runBlocking

class SuppressedRunBlocking {

    suspend fun reported() {
        runBlocking { }
    }

    @Suppress("RunBlocking")
    suspend fun suppressedOnFunction() {
        runBlocking { }
    }

    @Suppress("runblocking")
    suspend fun suppressedByLowercaseId() {
        runBlocking { }
    }

    @Suppress("SomeOtherInspection")
    suspend fun reportedForOtherId() {
        runBlocking { }
    }

    @Suppress("RunBlocking")
    class SuppressedOnClass {
        suspend fun suppressedByEnclosingClass() {
            runBlocking { }
        }
    }
}
