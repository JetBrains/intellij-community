// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.listenerList

import com.intellij.psi.CommonClassNames.JAVA_UTIL_COLLECTION
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiUtil.substituteTypeParameter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField

internal const val listenerListFqn = "groovy.beans.ListenerList"
@NonNls
internal const val listenerListOriginInfo = "by @ListenerList"

fun GrField.getListenerType(): PsiType? = substituteTypeParameter(type, JAVA_UTIL_COLLECTION, 0, false)
