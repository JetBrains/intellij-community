// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.refactoring.move.moveInner.MoveJavaInnerHandler
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.references.getImportAlias

class MoveKotlinInnerHandler : MoveJavaInnerHandler() {

    override fun preprocessUsages(results: MutableCollection<UsageInfo>?) {
        results?.removeAll { usageInfo ->
            usageInfo.element?.references?.any { it.getImportAlias() != null } == true
        }
    }
}