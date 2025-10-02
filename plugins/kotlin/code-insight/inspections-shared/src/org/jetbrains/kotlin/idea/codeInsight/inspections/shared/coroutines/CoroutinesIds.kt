// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object CoroutinesIds {
    val PACKAGE: FqName = FqName("kotlinx.coroutines")

    object Job {
        val ID: ClassId = ClassId(PACKAGE, Name.identifier("Job"))
        val join: CallableId = CallableId(ID, Name.identifier("join"))
    }

    val joinAll: CallableId = CallableId(PACKAGE, Name.identifier("joinAll"))
    
    object Deferred {
        val ID: ClassId = ClassId(PACKAGE, Name.identifier("Deferred"))
        val await: CallableId = CallableId(ID, Name.identifier("await"))
    }

    val awaitAll: CallableId = CallableId(PACKAGE, Name.identifier("awaitAll"))

    object CoroutineScope {
        val ID: ClassId = ClassId(PACKAGE, Name.identifier("CoroutineScope"))
    }
    
    object Selects {
        val PACKAGE: FqName = FqName("kotlinx.coroutines.selects")
        
        object SelectBuilder {
            val ID: ClassId = ClassId(PACKAGE, Name.identifier("SelectBuilder"))
            val invoke: CallableId = CallableId(ID, Name.identifier("invoke"))
        }

        val onTimeout: CallableId = CallableId(PACKAGE, Name.identifier("onTimeout"))
    }

    val currentCoroutineContext: CallableId = CallableId(PACKAGE, Name.identifier("currentCoroutineContext"))
    val suspendCancellableCoroutine: CallableId = CallableId(PACKAGE, Name.identifier("suspendCancellableCoroutine"))
}
