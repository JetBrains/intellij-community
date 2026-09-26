package sample

@ExperimentalStdlibApi
fun foo() {
    // Must report
    JavaEnum.values()

    // Must not report
    JavaEnum.values(false)
}
