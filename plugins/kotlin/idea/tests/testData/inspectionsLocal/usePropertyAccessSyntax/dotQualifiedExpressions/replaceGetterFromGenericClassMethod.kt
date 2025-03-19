// FIX: Use property access syntax
fun foo(klass: Class<*>) {
    klass.getEnclosingClass<caret>()
}