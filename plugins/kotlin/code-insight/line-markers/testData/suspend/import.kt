package test

import test.foo

suspend fun test() {
    <lineMarker text="Suspend function call 'foo()'">foo</lineMarker>()
}

suspend fun foo() {}