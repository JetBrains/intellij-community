// FIR_COMPARISON
// FIR_IDENTICAL
import ppp.MyClassB

fun foo(myCla<caret>)

// ORDER: myClassB: MyClassB
// ORDER: myClassA: MyClassA
// ORDER: myClassC: MyClassC
// ORDER: myClaaa: MyClaaa
