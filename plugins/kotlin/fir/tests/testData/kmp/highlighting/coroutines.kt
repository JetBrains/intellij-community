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

// PLATFORM: Native
// FILE: native.kt

import kotlinx.coroutines.*

suspend fun nativeCoroutines() {
    coroutineScope {
        launch {
            delay(1000)
        }
    }
}

// PLATFORM: Linux
// FILE: linux.kt

import kotlinx.coroutines.*

suspend fun linuxCoroutines() {
    coroutineScope {
        launch {
            delay(1000)
        }
    }
}

// PLATFORM: MinGW
// FILE: win.kt

import kotlinx.coroutines.*

suspend fun winCoroutines() {
    coroutineScope {
        launch {
            delay(1000)
        }
    }
}

// PLATFORM: LinuxX64
// FILE: linuxX64.kt

import kotlinx.coroutines.*

suspend fun linuxX64Coroutines() {
    coroutineScope {
        launch {
            delay(1000)
        }
    }
}

// PLATFORM: linuxArm64
// FILE: linuxArm64.kt

import kotlinx.coroutines.*

suspend fun linuxArmCoroutines() {
    coroutineScope {
        launch {
            delay(1000)
        }
    }
}
