val String.foo: Int
    get() = this@<caret>.length()

// IGNORE_K2
// EXIST: "this@foo"
// NOTHING_ELSE
