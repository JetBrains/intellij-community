import foo.A

fun main(a: A) {
    a.field<error descr="[UNSAFE_CALL]">.</error>length
}
