// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.impl.k2.lookups

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.compositeScope
import org.jetbrains.kotlin.analysis.api.components.scopeContext
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.types.expandedSymbol
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.factories.ClassifierLookupObject
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile

/**
 * For each of [lookups], the qualified name its K2 insert handler would write into [file], or null when it writes the
 * short name. For a client that runs no insert handler (e.g. a debugger console over DAP) and has to write the qualified
 * name itself.
 *
 * Only an `InsertFqNameAndShorten` lookup writes a qualified name: the handler inserts it and the shortener cuts it
 * back where the short name resolves. An `AddImport` lookup (an extension, a class in a callable reference or after a
 * receiver) writes the short name plus an import, which such a client cannot add; it gets null (known gap).
 *
 * The strategy alone does not tell visibility: every class and every non-extension callable with a stable path gets
 * `InsertFqNameAndShorten`, visible or not. Nor does the name being in scope: `kotlin.io.println` is default-imported,
 * yet a file's `fun println(message: Int)` wins over it for an `Int` argument, since explicit and package scopes beat
 * default imports; a local or a member of the context shadows too. So the short name is kept only when it can mean the
 * selected declaration alone: in the scope at the position (the content of a code fragment, whose context supplies the
 * locals and members; the importing scope of a plain file), every classifier (for a class) or every callable (for a
 * callable) of that short name has the qualified name. For a class, no callable of that name may have another one: a
 * local `val StringBuilder = { }` wins the call `StringBuilder()` over the constructor. Overloads of one qualified name
 * are fine, and a type alias counts as the class it expands to (`kotlin.text.StringBuilder` and
 * `java.lang.StringBuilder`). Any other declaration of the short name keeps the qualified name: still correct, only
 * longer.
 *
 * The scope depends on the position only, so it is built once for all [lookups]: one analysis, then one cheap lookup
 * per name.
 *
 * Must be called in a read action.
 */
@ApiStatus.Internal
fun kotlinImportFqNames(file: KtFile, lookups: List<LookupElement>): List<FqName?> {
    val candidates = lookups.map { lookup -> lookup.strategyFqName() }
    if (candidates.all { it == null }) return candidates
    return withAllowedResolve {
        analyze(file) {
            val position = (file as? KtCodeFragment)?.getContentElement()
            val scope = if (position != null) file.scopeContext(position).compositeScope() else importingScope(file)
            candidates.map { fqName -> fqName?.takeUnless { scope.meansOnly(it) } }
        }
    }
}

/**
 * Whether the short name of [fqName] in this scope can mean [fqName] only: all classifiers of that name are (or alias)
 * that class and no callable of that name has another qualified name (a SAM constructor has the class's), or all
 * callables of that name have that qualified name. A local, a member or a declaration of another
 * package with the same name makes it ambiguous, whichever wins resolution.
 */
context(_: KaSession)
private fun KaScope.meansOnly(fqName: FqName): Boolean {
    val name = fqName.shortName()
    if (!mayContainName(name)) return false

    val classifiers = classifiers(name).toList()
    val callableFqNames = callables(name).map { it.callableId?.asSingleFqName() }.toSet()
    val classifier = classifiers.firstOrNull { (it as? KaClassLikeSymbol)?.classId?.asSingleFqName() == fqName }
    if (classifier != null) {
        // A same-name callable (a local `val StringBuilder = { }`, a factory function) wins the call `StringBuilder()`
        val target = classifier.targetFqName()
        return classifiers.all { it.targetFqName() == target } && callableFqNames.all { it == fqName || it == target }
    }
    return callableFqNames == setOf(fqName)
}

/** The class a classifier stands for: a type alias counts as its expansion. Null for a type parameter or a local class. */
context(_: KaSession)
private fun KaClassifierSymbol.targetFqName(): FqName? {
    val symbol = (this as? KaTypeAliasSymbol)?.expandedType?.expandedSymbol ?: this
    return (symbol as? KaClassLikeSymbol)?.classId?.asSingleFqName()
}

private fun LookupElement.strategyFqName(): FqName? {
    val strategy = when (val lookupObject = `object`) {
        is ClassifierLookupObject -> lookupObject.importingStrategy
        is KotlinCallableLookupObject -> lookupObject.options.importingStrategy
        else -> return null
    }
    return (strategy as? ImportStrategy.InsertFqNameAndShorten)?.fqName
}

/**
 * The name a K2 lookup inserts: the import alias when it completes through one, else the declaration's name. Unquoted;
 * the insert handler adds the backticks. Null for a lookup that is not a K2 declaration lookup (e.g. a keyword).
 */
@ApiStatus.Internal
fun LookupElement.kotlinLookupShortName(): Name? = (`object` as? KotlinLookupObject)?.shortName
