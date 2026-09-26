// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.contentRoots

import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

internal class SourceFolderCheckerEntity(val pathRelativeToRoot: Path, val rootType: CheckerContentRootType) {
    override fun toString(): String {
        val rootTypeDescriptionIfNecessary = if (rootType.description.isNotEmpty())
            " (${rootType.description})"
        else
            ""

        // We need invariant separators so the gold files are platform-independent (and work on Windows)
        return "${pathRelativeToRoot.invariantSeparatorsPathString}$rootTypeDescriptionIfNecessary"
    }
}
