// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@ApiStatus.Internal
object CoroutinesIds {
    val PACKAGE: FqName = FqName("kotlinx.coroutines")

    object Job {
        val ID: ClassId = ClassId(PACKAGE, Name.identifier("Job"))

        val join: CallableId = CallableId(ID, Name.identifier("join"))
        
        object Key {
            val ID: ClassId = Job.ID.createNestedClassId(Name.identifier("Key"))
        }
    }
    
    object NonCancellable {
        val ID: ClassId = ClassId(PACKAGE, Name.identifier("NonCancellable"))
    }

    val joinAll: CallableId = CallableId(PACKAGE, Name.identifier("joinAll"))

    object Deferred {
        val ID: ClassId = ClassId(PACKAGE, Name.identifier("Deferred"))
        val await: CallableId = CallableId(ID, Name.identifier("await"))
    }

    val awaitAll: CallableId = CallableId(PACKAGE, Name.identifier("awaitAll"))

    object CoroutineScope {
        val ID: ClassId = ClassId(PACKAGE, Name.identifier("CoroutineScope"))

        val coroutineContext: CallableId = CallableId(ID, Name.identifier("coroutineContext"))
    }

    object Selects {
        val PACKAGE: FqName = FqName("kotlinx.coroutines.selects")

        object SelectBuilder {
            val ID: ClassId = ClassId(PACKAGE, Name.identifier("SelectBuilder"))

            val invoke: CallableId = CallableId(ID, Name.identifier("invoke"))
        }

        val onTimeout: CallableId = CallableId(PACKAGE, Name.identifier("onTimeout"))
    }

    object Channels {
        val PACKAGE: FqName = FqName("kotlinx.coroutines.channels")

        val produce: CallableId = CallableId(PACKAGE, Name.identifier("produce"))
    }

    object Flows {
        val PACKAGE: FqName = FqName("kotlinx.coroutines.flow")

        object Flow {
            val ID: ClassId = ClassId(PACKAGE, Name.identifier("Flow"))
        }

        val flowOn: CallableId = CallableId(PACKAGE, Name.identifier("flowOn"))
    }
    
    object CoroutineDispatcher {
        val ID: ClassId = ClassId(PACKAGE, Name.identifier("CoroutineDispatcher"))
    }

    val currentCoroutineContext: CallableId = CallableId(PACKAGE, Name.identifier("currentCoroutineContext"))
    val suspendCancellableCoroutine: CallableId = CallableId(PACKAGE, Name.identifier("suspendCancellableCoroutine"))
    
    val withContext: CallableId = CallableId(PACKAGE, Name.identifier("withContext"))
    
    val launch: CallableId = CallableId(PACKAGE, Name.identifier("launch"))
    val async: CallableId = CallableId(PACKAGE, Name.identifier("async"))
    val future: CallableId = CallableId(PACKAGE, Name.identifier("future"))
    val produce: CallableId = CallableId(PACKAGE, Name.identifier("produce"))
    val promise: CallableId = CallableId(PACKAGE, Name.identifier("promise"))

    object ParameterNames {
        val context: Name = Name.identifier("context")
    }

    object Stdlib {
        val PACKAGE: FqName = FqName("kotlin.coroutines")
        
        object CoroutineContext {
            val ID: ClassId = ClassId(PACKAGE, Name.identifier("CoroutineContext"))
            
            val plus: CallableId = CallableId(ID, Name.identifier("plus"))
            val minusKey: CallableId = CallableId(ID, Name.identifier("minusKey"))
        }
        
        val coroutineContext: CallableId = CallableId(PACKAGE, Name.identifier("coroutineContext"))
    }
}
