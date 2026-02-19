// "Replace usages of 'OldFoo' in whole project" "true"

package test

import dependency.OldFoo

fun foo(a: OldFoo) {
}

class X: OldF<caret>oo()