// CHOOSE_USE_SITE_TARGET: get
// WITH_STDLIB

annotation class A

class Property {
    @A<caret>
    val foo: String by lazy { "" }
}