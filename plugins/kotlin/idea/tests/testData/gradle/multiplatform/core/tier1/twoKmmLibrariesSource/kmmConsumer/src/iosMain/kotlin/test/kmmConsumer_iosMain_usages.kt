package test

fun use() {
    // Refinement on libs work + checking that iosMain and commonMain symbols are visible
    consumeIosMainExpect(produceCommonMainExpect())
    consumeCommonMainExpect(produceIosMainExpect())

    // Refinement on expects works
    produceCommonMainExpect().iosApi()

    // jvmMain API is not visible
    <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: produceJvmMainExpect'")!>produceJvmMainExpect<!>()
}
