class C(i: Int){}

fun foo(p: (Int) -> C){}

fun bar(){
    foo(::<caret>)
}

// EXIST: C

// IGNORE_K2
