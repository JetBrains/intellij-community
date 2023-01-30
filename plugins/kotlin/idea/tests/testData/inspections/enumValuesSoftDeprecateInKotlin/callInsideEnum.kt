package sample

@ExperimentalStdlibApi
private enum class MyEnum {
    ;
    init {
        values()
    }

    companion object {
        init {
            values()
        }
    }
}