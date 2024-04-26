package test

import lib.consume

fun usage() {
    <warning descr="[DEPRECATION] '@Deprecated(...) fun consume(rawValue: Char): Unit' is deprecated. Use consume(Int) instead.">consume</warning>('4')
}