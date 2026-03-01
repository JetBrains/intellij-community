package test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

fun useCommonMain() {
    // coroutines-jvm are resolved and usable
    runBlocking(Dispatchers.IO) {}

    // JDK is resolved and usable
    val file = java.io.File("")
    // Known issue: JDK-specific parts are visible
    val label: java.awt.Label? = null
    // Android SDK is not visible
    val bundle: <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'android'.'")!>android<!>.os.Bundle? = null

    // commonMain is visible
    commonMainApi()

    // known issue: jvmAndAndroidMain is visible
    jvmAndAndroidMainApi()

    // known issue: jvmMain is visible
    jvmMainApi()
}
