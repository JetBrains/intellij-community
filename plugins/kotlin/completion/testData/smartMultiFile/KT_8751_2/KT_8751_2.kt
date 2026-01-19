class KotlinClass : JavaClass(){
}

fun foo(c: KotlinClass, l: L<String>) {
    c.foo(<caret>)
}

// EXIST: l

// IGNORE_K2
