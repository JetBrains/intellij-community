package test

fun use() {
    // NB: commonMain-commonMain dependency works even though some platform variants were unresolved!
    produceCommonMainExpect()
}
