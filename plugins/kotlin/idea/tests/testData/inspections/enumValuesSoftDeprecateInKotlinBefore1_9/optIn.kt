package sample

@OptIn(ExperimentalStdlibApi::class)
private class OptInTest {
    fun reported_ClassDeclarartionOptIn() {
        JavaEnum.values()
    }
}

@ExperimentalStdlibApi
private class OptInTestOneMore {
    fun reported_ClassDeclarartionExperimental() {
        JavaEnum.values()
    }
}

@OptIn(ExperimentalStdlibApi::class)
private val reported_valDeclarartionOptIn = JavaEnum.values()

@ExperimentalStdlibApi
private val reported_valDeclarartionExperimental = JavaEnum.values()

fun notReported_NoOptIn() {
    JavaEnum.values()
}

@OptIn(ExperimentalStdlibApi::class)
fun reported_OptInAnnotation() {
    JavaEnum.values()
}

@ExperimentalStdlibApi
fun reported_ExperimentalAnnotation() {
    JavaEnum.values()
}
