package test

fun useAndroidMain() {
    // JDK is resolved and usable
    val file = java.io.File("")
    // JDK-specific parts are not visible
    val label: java.awt.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: Label'")!>Label<!>? = null
    // Android SDK is visible
    val bundle: android.os.Bundle? = null
}
