// FIR_IDENTICAL
val packageSize = 0
val packageSizeGetter
get() = packageSize * 2

var packageSizeSetter = 5
set(value) {
    field = value * 2
}

var packageSizeBean = 5
get() = packageSize * 2
set(value) {
    field = value * 2
}


class test() {
    // no highlighting check
    val size = 0

    val classSize = 0

    val classSizeGetter
    get() = classSize * 2

    var classSizeSetter = 5
    set(value) {
        field = value * 2
    }

    var classSizeBean = 5
    get() = classSize * 2
    set(value) {
        field = value * 2
    }

    fun callCustomPD() {
        classSizeBean = 30
    }
}
