// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.contentRoots

import java.nio.file.Path

internal class SourceFolderCheckerEntity(val pathRelativeToRoot: Path, val rootType: CheckerContentRootType) {
    override fun toString(): String {
        val rootTypeDescriptionIfNecessary = if (rootType.description.isNotEmpty())
            " (${rootType.description})"
        else
            ""

        return "$pathRelativeToRoot$rootTypeDescriptionIfNecessary"
    }
}
