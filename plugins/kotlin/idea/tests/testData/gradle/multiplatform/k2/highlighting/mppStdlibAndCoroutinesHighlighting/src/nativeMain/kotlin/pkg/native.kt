//region Test configuration
// - hidden: line markers
//endregion
package pkg

import kotlinx.coroutines.*

// stdlib
fun commonStdlibAndBuiltins() {
    listOf(1, 2, 3).map { it }
    val ea = emptyArray<String>()
    val unit = Unit
}

fun stdlibJvm(param: <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'JvmRepeatable'.'")!>JvmRepeatable<!>) {}

fun stdlibJs() {
    kotlin.js.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'console'.'")!>console<!>
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
fun stdlibNative(param: kotlinx.cinterop.CFunction<*>) {}

fun nativeDistPosix(param: platform.posix.DIR) {}

fun nativeDistFoundation(param: platform.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'Foundation'.'")!>Foundation<!>.NSArray) {}

// coroutines
suspend fun commonCoroutines() {
    coroutineScope {
        launch {
            delay(1000)
        }
    }
}

fun concurrent() {
    runBlocking {}
}

fun jvm(param: <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'ExecutorCoroutineDispatcher'.'")!>ExecutorCoroutineDispatcher<!>) {}

fun js(param: Deferred<String>) {
    param.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'asPromise'.'")!>asPromise<!>()
}

// no natural native-specific API in coroutines
@Suppress("invisible_reference")
private fun native(param: WorkerDispatcher) {}

@Suppress("invisible_reference")
private fun darwin(param: <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'DarwinGlobalQueueDispatcher'.'")!>DarwinGlobalQueueDispatcher<!>) {}
