// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.hierarchy.calls

import com.intellij.ide.hierarchy.call.CallHierarchyNodeDescriptor
import com.intellij.psi.PsiMember

fun extractMemberFromDescriptor(nodeDescriptor: CallHierarchyNodeDescriptor): PsiMember? {
    return nodeDescriptor.enclosingElement
}