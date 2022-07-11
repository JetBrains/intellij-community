fun foo(list: List<String>) {
    if (true) println(list)

    // printing...
    println("hi")

    for (l in list)<caret> println(l)

    // printing...
// AFTER-WARNING: Parameter 'a' is never used
    println("hi")
}

fun println(a: Any) {}