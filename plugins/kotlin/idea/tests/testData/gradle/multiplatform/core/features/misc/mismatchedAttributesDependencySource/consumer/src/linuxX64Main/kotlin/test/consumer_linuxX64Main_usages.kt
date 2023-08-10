package test

fun useLinux() {
    // NB: Linux variant resolution failed, so even the common code isn't visible
    <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: produceCommonMainExpect'")!>produceCommonMainExpect<!>()

    // NB: Kotlin/Native stdlib is imported and usable even though other dependencies partially failed!
    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    val x: CpuArchitecture = CpuArchitecture.ARM64
}
