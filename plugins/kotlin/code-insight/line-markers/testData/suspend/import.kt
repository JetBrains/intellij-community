package test

import test.foo

suspend fun test() {
    <lineMarker text="Suspend function call &apos;foo()&apos;">foo</lineMarker>()
}

suspend fun foo() {}