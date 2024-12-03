package test

import kotlin.concurrent.Volatile

class Foo {
    @Volatile
    var property: Int = 10
}