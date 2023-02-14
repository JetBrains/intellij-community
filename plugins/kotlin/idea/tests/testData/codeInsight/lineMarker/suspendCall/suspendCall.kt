@file:Suppress("RedundantSuspendModifier")

import kotlin.coroutines.coroutineContext
import kotlin.reflect.KProperty

suspend fun foo() {}

class SuspendDelegate<T> {
    suspend operator fun getValue(thisRef: T, property: KProperty<*>): String = property.name
}

class Bar {
    suspend operator fun invoke(): Bar = this
    suspend operator fun plus(other: Bar) = other
    suspend infix fun q(other: Bar) = other
}

suspend fun Bar.bar(): Bar = this

suspend fun test(b: Bar) {
    <lineMarker descr="Suspend function call">foo</lineMarker>()
    b.<lineMarker descr="Suspend function call">bar</lineMarker>()
        .<lineMarker descr="Suspend function call">bar</lineMarker>()
    b.<lineMarker descr="Suspend function call">invoke</lineMarker>()
    <lineMarker descr="Suspend function call">b</lineMarker>()
    b <lineMarker descr="Suspend function call">+</lineMarker> b
    b <lineMarker descr="Suspend function call">q</lineMarker> b
    <lineMarker descr="Suspend function call">coroutineContext</lineMarker>
    val delegated <lineMarker descr="Suspend function call">by</lineMarker> SuspendDelegate()
}

suspend fun Bar.testWithReceiver() {
    <lineMarker descr="Suspend function call">plus</lineMarker>(this)
    this.<lineMarker descr="Suspend function call">plus</lineMarker>(this)
    this@testWithReceiver.<lineMarker descr="Suspend function call">q</lineMarker>(this)
    this <lineMarker descr="Suspend function call">q</lineMarker> this
}
