package test

import lib.consume

fun usage() {
    <warning descr="[DEPRECATION] 'fun consume(rawValue: String): Unit' is deprecated. Use consume(Int) instead.">consume</warning>("42")
}