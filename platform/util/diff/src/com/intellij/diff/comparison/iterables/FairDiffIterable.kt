// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables

/**
 * Marker interface indicating that elements are compared one-by-one.
 *
 *
 * If range [a, b) is equal to [a', b'), than element(a + i) is equal to element(a' + i) for all i in [0, b-a)
 * Therefore, [.unchanged] ranges are guaranteed to have [DiffIterableUtil.getRangeDelta] equal to 0.
 *
 * @see DiffIterableUtil.fair
 * @see DiffIterableUtil.verifyFair
 */
interface FairDiffIterable : DiffIterable
