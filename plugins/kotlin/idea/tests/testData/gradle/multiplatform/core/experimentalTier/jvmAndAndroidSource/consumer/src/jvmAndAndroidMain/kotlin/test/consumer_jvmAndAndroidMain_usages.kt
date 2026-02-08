package test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

fun useJvmAndAndroidMain() {
    // coroutines-jvm are resolved and usable
    runBlocking(Dispatchers.IO) {}

    // JDK is resolved and usable
    val file = java.io.File("")
    // Known accepted issue: JDK-specific parts are visible
    val label: java.awt.Label? = null
    // Android SDK is not visible
    val bundle: <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'android'.'")!>android<!>.os.Bundle? = null

    // commonMain is visible
    commonMainApi()

    // jvmAndAndroidMain is visible
    jvmAndAndroidMainApi()

    // jvmMain is invisible
    <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'jvmMainApi'.'")!>jvmMainApi<!>()
}
