annotation class Ann(val <caret>x: String) {
    <error descr="[ANNOTATION_CLASS_MEMBER] Members are prohibited in annotation classes.">fun foo()</error> {
        println(x)
    }
}