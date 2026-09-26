class WithFunction {
    companion {
        fun foo() {}
    }
}

class WithProperty {
    companion {
        val foo = 1
    }
}

class Empty {
    companion {}
}

val value = object {
    fun foo() {}
}

// SET_INT: BLANK_LINES_AFTER_CLASS_HEADER = 1
