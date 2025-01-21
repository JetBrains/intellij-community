package b

import a.C
import a.MyProvider
import a.MyObject.provideDelegate

class Example : MyProvider {
    val c: C by C()
}