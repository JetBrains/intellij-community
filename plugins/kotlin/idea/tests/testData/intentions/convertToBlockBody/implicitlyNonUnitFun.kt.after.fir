// WITH_STDLIB

fun foo(): String {
    return java.io.File("x").getAbsolutePath()
}