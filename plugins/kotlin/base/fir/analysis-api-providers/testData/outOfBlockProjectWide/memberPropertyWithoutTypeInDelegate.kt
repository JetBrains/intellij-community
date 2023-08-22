class A {
    val x by lazy {
        <caret>
    }
}

// OUT_OF_BLOCK: true
