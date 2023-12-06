// FIR_COMPARISON
package test

import some.foo3FromSimpleImport
import pack.*

val foo6Var = 12
fun foo5CurentFile() = 12

val some = foo<caret>

// "foo" is before other elements because of exact prefix match

// ORDER: foo
// ORDER: foo3FromSimpleImport
// ORDER: foo6Var
// ORDER: foo5CurentFile
// ORDER: foo4FromSamePackage
// ORDER: foo2FromStarImport
// ORDER: foo1NotImported
// INVOCATION_COUNT: 2
// SELECTED: 0

// in K2 explicit simple-importing scope has higher priority than package scope