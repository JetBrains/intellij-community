package foo
import foo.a1 as A1ImportAlias

expect fun a1()
fun checkCommon() {
    A1ImportAlias()
}