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

fun stdlibJvm(param: JvmRepeatable) {}

fun stdlibJs() {
    kotlin.js.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'console'.'")!>console<!>
}

@OptIn(<!HIGHLIGHTING("severity='ERROR'; descr='[ANNOTATION_ARGUMENT_MUST_BE_CONST] Annotation argument must be a compile-time constant.'")!>kotlinx.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'cinterop'.'")!>cinterop<!>.ExperimentalForeignApi::class<!>)
fun stdlibNative(param: kotlinx.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'cinterop'.'")!>cinterop<!>.CFunction<*>) {}

fun nativeDistPosix(param: <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'platform'.'")!>platform<!>.posix.DIR) {}

fun nativeDistFoundation(param: <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'platform'.'")!>platform<!>.Foundation.NSArray) {}

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

fun jvm(param: ExecutorCoroutineDispatcher) {}

fun js(param: Deferred<String>) {
    param.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'asPromise'.'")!>asPromise<!>()
}

// no natural native-specific API in coroutines
@Suppress("invisible_reference")
private fun native(param: <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'WorkerDispatcher'.'")!>WorkerDispatcher<!>) {}

@Suppress("invisible_reference")
private fun darwin(param: <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'DarwinGlobalQueueDispatcher'.'")!>DarwinGlobalQueueDispatcher<!>) {}
