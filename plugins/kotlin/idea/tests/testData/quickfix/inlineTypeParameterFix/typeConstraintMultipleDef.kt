// "Inline type parameter" "true"
open class MyClass { val length = 0 }
fun <T, U> test(p: T, q: U) where T : MyClass, U: Int<caret> = p.length + q