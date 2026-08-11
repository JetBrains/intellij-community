package test

import lib.consume

fun value() = "42"

fun usage() {
    <warning descr="[DEPRECATION]">consume</warning>(wrappedValue())
}