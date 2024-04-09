// "/(Create extension function 'A.foo')|(Create extension function 'package2.A.foo')/" "true"
// ERROR: Unresolved reference: foo

import package1.A

class X {
    init {
        val y = package2.A()
        y.foo()
    }
}

private fun package2.A.foo() {
    TODO("Not yet implemented")
}
