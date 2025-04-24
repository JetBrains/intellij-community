package bar

import foo.Foo

data class Holder<T>(val value: T)

fun test(holder: Holder<out Foo>) {
    holder.value.fo<caret>
}
