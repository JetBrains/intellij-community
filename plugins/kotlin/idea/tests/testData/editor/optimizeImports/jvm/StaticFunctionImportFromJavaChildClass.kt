package test

import Base
import Child.STATIC_CONSTANT_FROM_BASE
import Child.staticFunFromBase
import KotlinChild.nonStaticFieldFromBase

fun usage() {
    staticFunFromBase()
    STATIC_CONSTANT_FROM_BASE
    nonStaticFieldFromBase
}

object KotlinChild : Base()