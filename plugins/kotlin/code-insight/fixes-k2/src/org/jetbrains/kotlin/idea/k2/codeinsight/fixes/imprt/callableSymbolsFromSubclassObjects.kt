// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiModifier
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.analysisScope
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.memberScope
import org.jetbrains.kotlin.analysis.api.components.withNullability
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember
import java.util.concurrent.ConcurrentMap

/**
 * Ad-hoc cache to avoid multiple repeated calls to [KtSymbolFromIndexProvider.getKotlinSubclassObjectsByNameFilter] during
 * unresolved references analysis in the auto-import.
 * 
 * This cache relies on the following conditions: 
 * - [KaSession.analysisScope] is stable during [KaSession]'s lifetime, 
 * so the results of [KtSymbolFromIndexProvider.getKotlinSubclassObjectsByNameFilter] should also be stable.
 * - It's safe to store references to [KaClassSymbol] in a cache as long as they are re-used in the same exact [KaSession].
 * This is achieved by using weak identity keys to store [KaSession].
 * - The values ([KaClassSymbol]s) are weakly reachable from this cache, so that they can be GCed under memory pressure,
 * and do not keep the weakly reachable keys ([KaSession]s) from being GCed.
 * The weakly referenced [List] should survive long enough to be reused when [getKotlinSubclassObjectsSymbolsCached]
 * is frequently called.
 */
private val SUBCLASS_OBJECTS_SYMBOLS_CACHE: ConcurrentMap<KaSession, List<KaClassSymbol>> =
    CollectionFactory.createConcurrentWeakKeyWeakValueIdentityMap()

context(session: KaSession)
private fun KtSymbolFromIndexProvider.getKotlinSubclassObjectsSymbolsCached(): List<KaClassSymbol> {
    return SUBCLASS_OBJECTS_SYMBOLS_CACHE.getOrPut(session) {
        getKotlinSubclassObjectsByNameFilter(
            scope = analysisScope,
            nameFilter = { true },
        ).toList()
    }
}

/**
 * Retrieves all non-local callable symbols (functions, properties, fields) with the given [name] 
 * that can be potentially inherited by subclasses.
 *
 * This includes:
 * - Non-top-level Kotlin callables (functions and properties)
 * - Non-static, non-top-level Java methods and fields
 *
 * Callables declared in `final` classes are excluded, as they cannot be inherited.
 * 
 * N.B. No synthetic Java properties are included here.
 */
context(_: KaSession)
private fun KtSymbolFromIndexProvider.getNonLocalInheritableCallablesByName(name: Name): Sequence<KaCallableSymbol> =
    sequence {
        yieldAll(getKotlinCallableSymbolsByName(name) { !it.isTopLevelKtOrJavaMember() })

        yieldAll(getJavaMethodsByName(name) { !it.isTopLevelKtOrJavaMember() && !it.hasModifierProperty(PsiModifier.STATIC) })
        yieldAll(getJavaFieldsByName(name) { !it.isTopLevelKtOrJavaMember() && !it.hasModifierProperty(PsiModifier.STATIC) })
    }.filter { callableSymbol ->
        val container = callableSymbol.containingDeclaration

        container is KaClassLikeSymbol &&
                container.classId != null &&
                container.modality != KaSymbolModality.FINAL
    }

/**
 * Consider moving this to [KtSymbolFromIndexProvider] when there is a good API to represent inherited callables.
 */
context(_: KaSession)
internal fun KtSymbolFromIndexProvider.getCallableSymbolsFromSubclassObjects(name: Name): Sequence<Pair<KaClassSymbol, KaCallableSymbol>> {
    // If there are no callables by this name which can be inherited,
    // there's no point to traverse all objects.
    // This check is supposed to be quick enough compared to computing `memberScope`s.
    // See KTIJ-35825 for details. 
    if (getNonLocalInheritableCallablesByName(name).none()) {
        return emptySequence()
    }
    
    val allObjects = getKotlinSubclassObjectsSymbolsCached()

    return allObjects.asSequence().flatMap { objectSymbol ->
        val memberScope = objectSymbol.memberScope
        val callablesByName = memberScope.callables(name)
        callablesByName.map {
            ProgressManager.checkCanceled()
            objectSymbol to it
        }
    }
}

/**
 * Consider moving this to [KtSymbolFromIndexProvider] when there is a good API to represent inherited callables.
 */
context(_: KaSession)
internal fun KtSymbolFromIndexProvider.getExtensionCallableSymbolsFromSubclassObjects(
    name: Name,
    receiverTypes: List<KaType>,
): Sequence<Pair<KaClassSymbol, KaCallableSymbol>> =
    getCallableSymbolsFromSubclassObjects(name).filterExtensionsByReceiverTypes(receiverTypes)

/**
 * Mostly a copy of [KtSymbolFromIndexProvider.filterExtensionsByReceiverTypes]; should be unified in the future.
 */
context(_: KaSession)
private fun Sequence<Pair<KaClassSymbol, KaCallableSymbol>>.filterExtensionsByReceiverTypes(
    receiverTypes: List<KaType>
): Sequence<Pair<KaClassSymbol, KaCallableSymbol>> {
    val nonNullableReceiverTypes = receiverTypes.map { it.withNullability(false) }

    return filter { (_, symbol) ->
        if (!symbol.isExtension) return@filter false
        val symbolReceiverType = symbol.receiverType ?: return@filter false

        nonNullableReceiverTypes.any { it isPossiblySubTypeOf symbolReceiverType }
    }
}
