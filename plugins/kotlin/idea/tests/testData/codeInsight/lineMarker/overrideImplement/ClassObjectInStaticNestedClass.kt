interface <lineMarker>TestInterface</lineMarker> {
    fun <lineMarker>test</lineMarker>()
}

class A {
    class B {
        companion object : TestInterface { // TODO: No line marker
            override fun <lineMarker descr="Implements function in 'TestInterface'">test</lineMarker>() {
            }
        }
    }
}