package test

import lib.consume

fun usage() {
    <warning descr="[DEPRECATION] 'fun consume(rawValue: Char): Unit' is deprecated. Use consume(Int) instead.">consume</warning>('4')
}