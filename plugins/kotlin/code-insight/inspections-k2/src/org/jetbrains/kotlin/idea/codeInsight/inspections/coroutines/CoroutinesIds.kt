// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.coroutines

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

        /** The `Job()` function, which makes a new [ID]. It is a function and not a constructor. */
        val factory: CallableId = CallableId(PACKAGE, ID.shortClassName)

        val join: CallableId = CallableId(ID, Name.identifier("join"))
        
        object Key {
            val ID: ClassId = Job.ID.createNestedClassId(Name.identifier("Key"))
        }
    }
    
    /** The `SupervisorJob()` function. It makes a new [Job.ID] and has no type of the same name. */
    val supervisorJobFactory: CallableId = CallableId(PACKAGE, Name.identifier("SupervisorJob"))

    object NonCancellable {
        val ID: ClassId = ClassId(PACKAGE, Name.identifier("NonCancellable"))
    }

    val joinAll: CallableId = CallableId(PACKAGE, Name.identifier("joinAll"))

    object Deferred {
        val ID: ClassId = ClassId(PACKAGE, Name.identifier("Deferred"))
        val await: CallableId = CallableId(ID, Name.identifier("await"))
    }

    object CompletableDeferred {
        val ID: ClassId = ClassId(PACKAGE, Name.identifier("CompletableDeferred"))

        /** The `CompletableDeferred()` function, which makes a new [ID]. */
        val factory: CallableId = CallableId(PACKAGE, ID.shortClassName)
    }

    val awaitAll: CallableId = CallableId(PACKAGE, Name.identifier("awaitAll"))

    object CoroutineScope {
        val ID: ClassId = ClassId(PACKAGE, Name.identifier("CoroutineScope"))

        /** The `CoroutineScope()` function, which makes a new [ID]. */
        val factory: CallableId = CallableId(PACKAGE, ID.shortClassName)

        val coroutineContext: CallableId = CallableId(ID, Name.identifier("coroutineContext"))
    }

    /** The `MainScope()` function. It makes a new [CoroutineScope.ID] and has no type of the same name. */
    val mainScopeFactory: CallableId = CallableId(PACKAGE, Name.identifier("MainScope"))

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

        object Channel {
            val ID: ClassId = ClassId(PACKAGE, Name.identifier("Channel"))

            /** The `Channel()` function, which makes a new [ID]. */
            val factory: CallableId = CallableId(PACKAGE, ID.shortClassName)
        }
    }

    object Flows {
        val PACKAGE: FqName = FqName("kotlinx.coroutines.flow")

        object Flow {
            val ID: ClassId = ClassId(PACKAGE, Name.identifier("Flow"))
        }

        object MutableStateFlow {
            val ID: ClassId = ClassId(PACKAGE, Name.identifier("MutableStateFlow"))

            /** The `MutableStateFlow()` function, which makes a new [ID]. */
            val factory: CallableId = CallableId(PACKAGE, ID.shortClassName)
        }

        object MutableSharedFlow {
            val ID: ClassId = ClassId(PACKAGE, Name.identifier("MutableSharedFlow"))

            /** The `MutableSharedFlow()` function, which makes a new [ID]. */
            val factory: CallableId = CallableId(PACKAGE, ID.shortClassName)
        }

        val count: CallableId = CallableId(PACKAGE, Name.identifier("count"))
        val filter: CallableId = CallableId(PACKAGE, Name.identifier("filter"))
        val filterIsInstance: CallableId = CallableId(PACKAGE, Name.identifier("filterIsInstance"))
        val filterNotNull: CallableId = CallableId(PACKAGE, Name.identifier("filterNotNull"))
        val first: CallableId = CallableId(PACKAGE, Name.identifier("first"))
        val firstOrNull: CallableId = CallableId(PACKAGE, Name.identifier("firstOrNull"))
        val flatMapConcat: CallableId = CallableId(PACKAGE, Name.identifier("flatMapConcat"))
        val flatMapMerge: CallableId = CallableId(PACKAGE, Name.identifier("flatMapMerge"))
        val flattenConcat: CallableId = CallableId(PACKAGE, Name.identifier("flattenConcat"))
        val flattenMerge: CallableId = CallableId(PACKAGE, Name.identifier("flattenMerge"))
        val flowOn: CallableId = CallableId(PACKAGE, Name.identifier("flowOn"))
        val map: CallableId = CallableId(PACKAGE, Name.identifier("map"))
        val mapNotNull: CallableId = CallableId(PACKAGE, Name.identifier("mapNotNull"))
    }

    object Sync {
        val PACKAGE: FqName = FqName("kotlinx.coroutines.sync")

        object Mutex {
            val ID: ClassId = ClassId(PACKAGE, Name.identifier("Mutex"))

            /** The `Mutex()` function, which makes a new [ID]. */
            val factory: CallableId = CallableId(PACKAGE, ID.shortClassName)
        }

        object Semaphore {
            val ID: ClassId = ClassId(PACKAGE, Name.identifier("Semaphore"))

            /** The `Semaphore()` function, which makes a new [ID]. */
            val factory: CallableId = CallableId(PACKAGE, ID.shortClassName)
        }
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
        val concurrency: Name = Name.identifier("concurrency")
        val context: Name = Name.identifier("context")
        val transform: Name = Name.identifier("transform")
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
