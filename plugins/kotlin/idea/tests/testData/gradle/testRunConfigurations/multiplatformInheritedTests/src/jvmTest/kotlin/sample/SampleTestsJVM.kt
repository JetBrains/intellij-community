package sample

import kotlin.test.Test

abstract class <lineMarker descr="Run Test" settings="null"><lineMarker descr="Is subclassed by     ChildTestJvm1     ChildTestJvm2  Click or press ⌥⌘Bto navigate">BaseTestJvm</lineMarker></lineMarker> {
    @Test
    fun <lineMarker descr="Run Test" settings="null">myTest</lineMarker>() {}
}

class <lineMarker descr="Run Test" settings=":cleanJvmTest :jvmTest --tests \"sample.ChildTestJvm1\"">ChildTestJvm1</lineMarker> : BaseTestJvm()

class <lineMarker descr="Run Test" settings=":cleanJvmTest :jvmTest --tests \"sample.ChildTestJvm2\"">ChildTestJvm2</lineMarker> : BaseTestJvm()