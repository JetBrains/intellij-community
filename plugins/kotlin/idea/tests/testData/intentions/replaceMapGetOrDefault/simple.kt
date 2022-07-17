// RUNTIME_WITH_FULL_JDK
// AFTER-WARNING: The expression is unused
fun test(map: Map<Int, String>) {
    map.getOrDefault<caret>(1, "bar")
}