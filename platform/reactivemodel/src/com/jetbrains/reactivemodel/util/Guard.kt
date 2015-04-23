package com.jetbrains.reactivemodel.util


public class Guard {
    public var locked: Boolean = false

    public inline fun lock(inline f: () -> Unit) {
        locked = true
        try {f()}
        finally {
            locked = false
        }
    }
}