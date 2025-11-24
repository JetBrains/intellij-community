// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text.matching

/**
 * An abstraction over [java.util.BitSet], should be eliminated in favor of Kotlin BitSet
 * when it becomes multiplatform: KT-55163
 */
internal typealias BitSet = java.util.BitSet