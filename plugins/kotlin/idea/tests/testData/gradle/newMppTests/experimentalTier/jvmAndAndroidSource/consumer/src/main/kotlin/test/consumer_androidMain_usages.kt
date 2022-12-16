package test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

fun useAndroidMain() {
    // coroutines-jvm are resolved and usable
    runBlocking(Dispatchers.IO) {}

    // JDK is resolved and usable
    val file = java.io.File("")
    // JDK-specific parts are not visible
    val label: java.awt.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: Label'")!>Label<!>? = null
    // Android SDK is visible
    val bundle: android.os.Bundle? = null

    // commonMain is visible
    commonMainApi()

    // jvmAndAndroidMain is visible
    jvmAndAndroidMainApi()

    // jvmMain is not visible
    <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: jvmMainApi'")!>jvmMainApi<!>()

    // androidMain is visible
    androidMainApi()
}
