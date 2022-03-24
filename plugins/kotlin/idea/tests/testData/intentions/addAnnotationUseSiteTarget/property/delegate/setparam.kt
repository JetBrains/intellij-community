// CHOOSE_USE_SITE_TARGET: setparam
// WITH_STDLIB
// IS_APPLICABLE: false

annotation class A

class Property {
    @A<caret>
    val foo: String by lazy { "" }
}