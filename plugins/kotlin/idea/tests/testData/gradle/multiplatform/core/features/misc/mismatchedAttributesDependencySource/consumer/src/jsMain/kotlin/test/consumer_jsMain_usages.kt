package test

fun useJs() {
    // NB: jsMain-commonMain dependency works even though some other platform variants were unresolved!
    produceCommonMainExpect()

    // NB: jsMain-jsMain dependency works even though some other platform variants were unresolved!
    produceJsMainExpect().jsApi()
}
