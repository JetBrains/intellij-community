fun bar(): String {
    return "some string"
}
fun foo(): String{
    <selection>val b = bar()
    val a = "$b$b"</selection>
    if (b.length == 1) {

    }
    return b
}