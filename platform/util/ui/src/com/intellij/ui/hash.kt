// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.dynatrace.hash4j.hashing.Hasher64
import com.dynatrace.hash4j.hashing.Hashing

internal val hasher: Hasher64 = Hashing.komihash5_0()
internal val seededHasher: Hasher64 = Hashing.komihash5_0(4812324275)