package test

fun use() {
    // Refinement on libs work + checking that jvmMain and commonMain symbols are visible
    consumeJvmMainExpect(produceCommonMainExpect())
    consumeCommonMainExpect(produceJvmMainExpect())

    // Refinement on expects works
    produceCommonMainExpect().jvmApi()
}
