package test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

fun useJvmAndAndroidMain() {
    // coroutines-jvm are resolved and usable
    runBlocking(Dispatchers.IO) {}

    // JDK is resolved and usable
    val file = java.io.File("")
    // Known issue: JDK-specific parts are visible
    val label: java.awt.Label? = null
    // Android SDK is not visible
    val bundle: <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: android'")!>android<!>.<!HIGHLIGHTING("severity='ERROR'; descr='[DEBUG] Reference is not resolved to anything, but is not marked unresolved'")!>os<!>.<!HIGHLIGHTING("severity='ERROR'; descr='[DEBUG] Reference is not resolved to anything, but is not marked unresolved'")!>Bundle<!>? = null

    // commonMain is visible
    commonMainApi()

    // jvmAndAndroidMain is visible
    jvmAndAndroidMainApi()

    // known issue: jvmMain is visible
    jvmMainApi()
}
