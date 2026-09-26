// FIR_COMPARISON
// FIR_IDENTICAL
import ppp.prefixFun2
import ppp.prefixProp2

val some = prefix<caret>

// ORDER: prefixProp2
// ORDER: prefixFun2
// ORDER: prefixProp1
// ORDER: prefixProp3
// ORDER: prefixFun1
// ORDER: prefixFun3
