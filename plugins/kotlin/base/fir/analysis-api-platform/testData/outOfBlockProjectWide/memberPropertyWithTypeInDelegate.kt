class A {
    val x: Int by lazy {
        <caret>
    }
}

// OUT_OF_BLOCK: true
