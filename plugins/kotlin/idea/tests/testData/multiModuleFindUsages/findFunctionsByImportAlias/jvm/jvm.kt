package foo

import foo.a1 as A1ImportAlias

actual fun a1() {}
fun checkJvm() {
    A1ImportAlias()
}
