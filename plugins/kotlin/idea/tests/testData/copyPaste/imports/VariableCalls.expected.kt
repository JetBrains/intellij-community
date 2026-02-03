package to

import a.extensionVariableCall1
import a.extensionVariableCall2
import a.variableCall

fun Int.test(n: Int) {
    variableCall()
    extensionVariableCall1()
    n.extensionVariableCall2()
}
