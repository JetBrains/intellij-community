package test

fun useCommonTest() {
    // Known issue
    useJvmAndAndroidMain()
    <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: useJvmMain'")!>useJvmMain<!>()
}
