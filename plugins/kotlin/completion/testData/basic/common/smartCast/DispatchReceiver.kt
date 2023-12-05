class B {
    fun Int.extension() {}
}

var mutableNonLocal = Any()

fun test() {
    if (mutableNonLocal is B) {
        with(mutableNonLocal) {
            10.exten<caret>
        }
    }
}

// NUMBER: 0