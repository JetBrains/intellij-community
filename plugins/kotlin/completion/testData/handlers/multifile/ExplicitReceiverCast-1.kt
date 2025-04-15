// IGNORE_K1

package bar

import foo.Foo
import foo.Bar

class Holder {
    val foo: Foo
        get() = Bar()
}

fun test(holder: Holder) {
    if (holder.foo is Bar) {
        holder.foo.ba<caret>
    }
}