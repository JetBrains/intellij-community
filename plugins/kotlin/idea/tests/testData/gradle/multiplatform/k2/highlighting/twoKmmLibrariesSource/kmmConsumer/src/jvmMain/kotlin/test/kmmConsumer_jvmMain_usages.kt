//region Test configuration
// - hidden: line markers
//endregion
package test

fun use() {
    // Refinement on libs work + checking that jvmMain and commonMain symbols are visible
    consumeJvmMainExpect(produceCommonMainExpect())
    consumeCommonMainExpect(produceJvmMainExpect())

    // Refinement on expects works
    // Issue: KTIJ-27569
    produceCommonMainExpect().<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'jvmApi'.'")!>jvmApi<!>()

    // iosMain API is not visible
    <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference 'produceIosMainExpect'.'")!>produceIosMainExpect<!>()
}
