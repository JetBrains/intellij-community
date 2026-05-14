// "Replace usages of 'oldProp: String' in whole project" "true"

import pack.foo
import pack.newProp

fun foo() {
    foo(<selection><caret></selection>newProp)
}
