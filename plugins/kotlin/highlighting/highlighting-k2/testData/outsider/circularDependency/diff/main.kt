package test

import lib.consume

fun value() = "42"

fun usage() {
    <warning descr="[DEPRECATION] '@Deprecated(...) fun consume(rawValue: String): Unit' is deprecated. Use consume(Int) instead.">consume</warning>(wrappedValue())
}