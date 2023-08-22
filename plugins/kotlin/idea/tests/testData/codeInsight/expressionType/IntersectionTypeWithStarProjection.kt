fun foo(i: String) {
    val a = <caret>i ?: 1
}

// TYPE: i -> <html>String</html>
// TYPE: i ?: 1 -> <html>{Comparable&lt;*&gt; & java.io.Serializable}</html>
