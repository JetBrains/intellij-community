package test

import dependency.InnerTypeAlias
import dependency.Outer.Inner
import dependency.Outer

fun Outer.usage() {
    InnerTypeAlias()
}