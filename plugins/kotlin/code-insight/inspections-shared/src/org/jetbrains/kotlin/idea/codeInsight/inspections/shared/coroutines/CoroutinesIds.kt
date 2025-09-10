// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object CoroutinesIds {
    val COROUTINES_PACKAGE: FqName = FqName("kotlinx.coroutines")

    val CURRENT_COROUTINE_CONTEXT_ID: CallableId = CallableId(COROUTINES_PACKAGE, Name.identifier("currentCoroutineContext"))
    
    val JOB_CLASS_ID: ClassId = ClassId(COROUTINES_PACKAGE, Name.identifier("Job"))
    val JOB_JOIN_ID: CallableId = CallableId(JOB_CLASS_ID, Name.identifier("join"))
    val JOIN_ALL_ID: CallableId = CallableId(COROUTINES_PACKAGE, Name.identifier("joinAll"))
    
    val DEFERRED_CLASS_ID: ClassId = ClassId(COROUTINES_PACKAGE, Name.identifier("Deferred"))
    val DEFERRED_AWAIT_ID: CallableId = CallableId(DEFERRED_CLASS_ID, Name.identifier("await"))
    val AWAIT_ALL_ID: CallableId = CallableId(COROUTINES_PACKAGE, Name.identifier("awaitAll"))
    
    val COROUTINE_SCOPE_CLASS_ID: ClassId = ClassId(COROUTINES_PACKAGE, Name.identifier("CoroutineScope"))
    
    val COROUTINES_SELECTS_PACKAGE: FqName = FqName("kotlinx.coroutines.selects")

    val SELECT_BUILDER_CLASS_ID: ClassId = ClassId(COROUTINES_SELECTS_PACKAGE, Name.identifier("SelectBuilder"))
    val SELECT_BUILDER_INVOKE_ID: CallableId = CallableId(SELECT_BUILDER_CLASS_ID, Name.identifier("invoke"))
    val SELECT_BUILDER_ON_TIMEOUT_ID: CallableId = CallableId(COROUTINES_SELECTS_PACKAGE, Name.identifier("onTimeout"))

    val SUSPEND_CANCELLABLE_COROUTINE_ID: CallableId = CallableId(COROUTINES_PACKAGE, Name.identifier("suspendCancellableCoroutine"))
}
