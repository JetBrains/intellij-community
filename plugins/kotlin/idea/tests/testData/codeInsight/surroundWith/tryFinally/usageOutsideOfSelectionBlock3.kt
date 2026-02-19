fun bar(): String {
    return "some string"
}
fun foo(): String {
    val b = bar()
    val a= "$b$b"
    if (b.length == 1) {

    }
    <selection>val v = a + b</selection>
    return v
}