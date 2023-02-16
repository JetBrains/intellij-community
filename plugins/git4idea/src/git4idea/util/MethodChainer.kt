// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.util

import java.util.function.Function

/**
 * Monad added to make chaining multiple operations cleaner or simpler.
 * This class was converted from Java to Kotlin using IntelliJ.
 */
class MethodChainer<Type> private constructor(private val myType: Type) {
  fun run(methodForType: Function<Type, Type>): MethodChainer<Type> {
    return MethodChainer(methodForType.apply(myType))
  }

  fun runIf(isTrue: Boolean, methodForType: Function<Type, Type>): MethodChainer<Type> {
    return if (isTrue) run(methodForType) else this
  }

  fun runIfElse(
    isTrue: Boolean,
    methodForTypeIfTrue: Function<Type, Type>,
    methodForTypeIfFalse: Function<Type, Type>
  ): MethodChainer<Type> {
    return if (isTrue) run(methodForTypeIfTrue) else run(methodForTypeIfFalse)
  }

  fun unwrap(): Type {
    return myType
  }

  companion object {
    @JvmStatic
    fun <Type> wrap(type: Type): MethodChainer<Type> {
      return MethodChainer(type)
    }
  }
}