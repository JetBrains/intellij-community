package test

import lib.consume

fun value() = 42

fun usage() {
    consume(wrappedValue())
}