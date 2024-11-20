// "Replace usages of 'OldFoo' in whole project" "true"

package test

import dependency.NewFoo

fun foo(a: NewFoo) {
}

class X: NewF<selection><caret></selection>oo()
