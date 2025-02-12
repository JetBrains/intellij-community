package test

import java.lang.Deprecated

import kotlin.String
import kotlin.run

@Deprecated
fun test(): String {
    return run { "" }
}