package sample

import kotlin.test.Test

abstract class <lineMarker descr="Run Test" settings="null"><lineMarker descr="Is subclassed by ChildCommon1 (sample) ChildCommon2 (sample) Press ⌥⌘B to navigate">BaseTestCommon</lineMarker></lineMarker> {
    @Test
    fun <lineMarker descr="Run Test" settings="null">myTest</lineMarker>() {}
}

class <lineMarker descr="Run Test" settings=":cleanJvmTest :jvmTest --tests \"sample.ChildCommon1\"">ChildCommon1</lineMarker> : BaseTestCommon()

class <lineMarker descr="Run Test" settings=":cleanJvmTest :jvmTest --tests \"sample.ChildCommon2\"">ChildCommon2</lineMarker> : BaseTestCommon()