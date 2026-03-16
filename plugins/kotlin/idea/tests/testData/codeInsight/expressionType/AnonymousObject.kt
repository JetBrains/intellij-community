fun baz() = run {
    val a = object {}
    <caret>a // empty box
}

// K1_TYPE: a -> <html>&lt;anonymous object&gt;</html>

// K2_TYPE: a -> <b>`&lt;anonymous&gt;`</b>
