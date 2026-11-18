// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

/**
 * The look of the skeleton blocks at one moment.
 *
 * [fadeProgress] is the fraction of the color ramp from the background to full contrast, from 0 to 1.
 * [opacity] is the opacity of the blocks, from 0 to 1.
 */
internal data class EditorSkeletonFrameState(val fadeProgress: Double, val opacity: Double)
