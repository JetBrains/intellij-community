
package bar

import foo.Foo

fun bar() {
    Foo().let { <caret> }
}
