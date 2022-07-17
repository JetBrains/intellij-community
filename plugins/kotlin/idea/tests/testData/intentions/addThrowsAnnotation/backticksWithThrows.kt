// WITH_STDLIB
class `My$Exception` : Exception()
class `My$Exception2` : Exception()

@Throws(`My$Exception`::class)
fun test() {
    <caret>throw `My$Exception2`()
}