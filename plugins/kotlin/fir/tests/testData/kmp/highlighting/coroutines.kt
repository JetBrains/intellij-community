// PLATFORM: Common
// FILE: common.kt

import kotlinx.coroutines.*

suspend fun commonCoroutines() {
    coroutineScope {
        launch {
            delay(1000)
        }
    }
}

// PLATFORM: Jvm
// FILE: jvm.kt

import kotlinx.coroutines.*

suspend fun jvmCoroutines() {
    coroutineScope {
        launch {
            delay(1000)
        }
    }
}

// PLATFORM: Js
// FILE: js.kt

import kotlinx.coroutines.*

suspend fun jsCoroutines() {
    coroutineScope {
        launch {
            delay(1000)
        }
    }
}
