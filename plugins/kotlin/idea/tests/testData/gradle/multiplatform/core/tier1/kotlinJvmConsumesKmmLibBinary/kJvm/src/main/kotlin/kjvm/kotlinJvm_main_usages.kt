package kjvm

import test.*

fun use(c: CommonMainExpect) {
    // Refinement on libs work + checking that jvmMain and commonMain symbols are visible
    consumeJvmMainExpect(produceCommonMainExpect())
    consumeCommonMainExpect(produceJvmMainExpect())

    // Refinement on expects works
    produceCommonMainExpect().jvmApi()
}
