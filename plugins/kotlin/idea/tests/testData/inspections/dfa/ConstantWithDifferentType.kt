// WITH_STDLIB
const val unresolved : <error descr="[UNRESOLVED_REFERENCE] Unresolved reference 'Foo'.">Foo</error> = "Hello"

<error descr="[TYPE_CANT_BE_USED_FOR_CONST_VAL] Const 'val' has type 'Int?'. Only primitive types and 'String' are allowed.">const</error> val nullable : Int? = 123
        
fun test() {
    if (<warning descr="Condition 'unresolved == \"Hello\"' is always true">unresolved == "Hello"</warning>) {}
}

fun testNullable() {
    if (nullable == 123) {}
}