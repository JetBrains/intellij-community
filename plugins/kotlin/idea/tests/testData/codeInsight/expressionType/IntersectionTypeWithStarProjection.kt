fun foo(i: String) {
    val a = <caret>i ?: 1
}

// K1_TYPE: i -> <html>String</html>
// K1_TYPE: i ?: 1 -> <html>{Comparable&lt;*&gt; & java.io.Serializable}</html>

// K2_TYPE: i -> String
// K2_TYPE: i ?: 1 -> Comparable&lt;*&gt; &amp; Serializable
