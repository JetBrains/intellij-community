package sample

import kotlin.test.Test

abstract class <lineMarker descr="Run Test" settings="null"><lineMarker descr="Is subclassed by ChildTestJvm1 (sample) ChildTestJvm2 (sample) Press ⌥⌘B to navigate">BaseTestJvm</lineMarker></lineMarker> {
    private fun privateFunIsNotATest() {}

    fun notATest() {}

    @Test
    private fun alsoNotATest() {}

    @Test
    fun <lineMarker descr="Run Test" settings="null">myTest</lineMarker>() {}
}

class <lineMarker descr="Run Test" settings=":cleanJvmTest :jvmTest --tests \"sample.ChildTestJvm1\"">ChildTestJvm1</lineMarker> : BaseTestJvm()

class <lineMarker descr="Run Test" settings=":cleanJvmTest :jvmTest --tests \"sample.ChildTestJvm2\"">ChildTestJvm2</lineMarker> : BaseTestJvm()