# K2 Code Completion (`impl-k2`)

This module implements code completion for the Kotlin K2 (FIR) compiler plugin in IntelliJ IDEA.

## Overview

The completion system is built around three main entry points:

- **`KotlinFirCompletionContributor`** — IntelliJ `CompletionContributor` that registers providers for `BASIC` and `SMART` completion types, handles dummy identifier correction, and delegates to `KotlinFirCompletionProvider`.
- **`KotlinFirCompletionProvider`** — Orchestrates the completion flow: creates `KotlinFirCompletionParameters`, detects position context via `KotlinPositionContextDetector`, calls `Completions.complete()`, and optionally runs chain completion if results are insufficient.
- **`Completions.complete()`** — Filters the registered contributors by position context, collects `K2CompletionSection`s from each, and dispatches them to a `K2CompletionRunner`.

## Completion Flow

```
CompletionParameters
  → KotlinFirCompletionParameters (with position correction)
    → Position context detection (KotlinPositionContextDetector)
      → Contributor matching (by position context class)
        → Section registration (K2CompletionSetupScope)
          → Runner execution (sequential or parallel)
            → KaSession opened, common data created
              → Each section runs within session:
                  → Visibility checking
                  → Symbol collection (scopes + index)
                  → LookupElement creation (factories)
                  → Element decoration (insertion handlers, import strategy)
                  → Elements added to sink
            → Weighing & sorting (CompletionSorter + 22 weighers)
              → Results delivered to CompletionResultSet
```

## Core Abstractions

### `K2CompletionContributor<P>`
Generic abstract base class parameterized by position context type `P`. Key methods:
- `K2CompletionSetupScope<P>.isAppropriatePosition()` — Pre-session position check (no analysis API).
- `K2CompletionSetupScope<P>.registerCompletions()` — Registers one or more `K2CompletionSection`s.
- `context(KaSession, K2CompletionSectionContext<P>) addElement()` / `addElements()` — Adds lookup elements, decorated with contributor metadata and insertion handlers.

**`K2SimpleCompletionContributor<P>`** is a convenience subclass that wraps a single `complete()` method into one named section.

### `K2CompletionSection<P>`
An isolated, independently executable unit of work within a contributor. Each section has:
- `priority` (`K2ContributorSectionPriority`) — Controls execution order (`HEURISTIC=10`, `DEFAULT=50`, `FROM_INDEX=100`).
- `name` — Used for debugging and JFR profiling.
- `runnable` — The actual completion logic.

Sections enable parallel execution and fine-grained performance monitoring.

### `K2CompletionContext<P>`
Immutable context created before the analysis session. Contains `KotlinFirCompletionParameters`, `CompletionResultSet`, and the detected `positionContext`. Provides name filters (`scopeNameFilter`, `getIndexNameFilter()`) for efficient index lookups.

### `K2CompletionSectionContext<P>`
Per-section context created within an analysis session. Provides:
- Access to `weighingContext`, `visibilityChecker`, `importStrategyDetector`, `symbolFromIndexProvider`.
- **Lazy session properties**: `runtimeType`, `extensionChecker`, `runtimeTypeExtensionChecker` — computed on first access to avoid unnecessary work.
- `sink` — A `K2LookupElementSink` for accumulating results.

### `K2CompletionSectionCommonData<P>`
Shared data across all sections running in the same `KaSession`, including `WeighingContext`, `PrefixMatcher`, `CompletionVisibilityChecker`, and `sessionStorage` for caching expensive computations.

### `K2CompletionRunner`
Interface that decouples section definition from execution:
- **`SequentialCompletionRunner`** — Runs all sections in a single `KaSession`, one after another.
- **`ParallelCompletionRunner`** — Runs sections across multiple threads (up to 4, configurable via `MAX_CONCURRENT_COMPLETION_THREADS`), each with its own `KaSession`. Uses a priority queue for section ordering and accumulating sinks for result collection.

Selected via `K2CompletionRunner.getInstance(sectionCount)` based on the `kotlin.k2.parallel.completion.enabled` registry flag.

## Contributors

All contributors are registered in `Completions.contributors`. Each targets a specific `KotlinRawPositionContext` subtype.

### Callables
| Contributor | Description |
|---|---|
| `K2CallableCompletionContributor` | Functions, properties, nested callables; handles smart casts and extensions |
| `K2CallableReferenceCompletionContributor` | Callable references (`::foo`) |
| `K2InfixCallableCompletionContributor` | Infix function completion |
| `K2KDocCallableCompletionContributor` | Callable completion in KDoc comments |
| `K2SuperMemberCompletionContributor` | Super method/property completion |

### Classifiers & Types
| Contributor | Description |
|---|---|
| `K2ClassifierCompletionContributor` | Classes, interfaces, type parameters in name references |
| `K2ClassReferenceCompletionContributor` | Class completion in special contexts (annotations, etc.) |
| `K2TypeInstantiationContributor` | Constructor calls and anonymous object creation |

### Named Arguments & Parameters
| Contributor | Description |
|---|---|
| `K2NamedArgumentCompletionContributor` | Named argument completion in call sites |
| `K2MultipleArgumentContributor` | Completes remaining arguments |
| `K2TrailingFunctionParameterNameCompletionContributorBase.All()` | All trailing lambda parameter names |
| `K2TrailingFunctionParameterNameCompletionContributorBase.Missing()` | Missing trailing lambda parameter names |

### Keywords & Packages
| Contributor | Description |
|---|---|
| `K2KeywordCompletionContributor` | Language keywords (`if`, `when`, `try`, etc.) |
| `K2OperatorNameCompletionContributor` | Operator function names for overloading |
| `K2PackageCompletionContributor` | Package name completion |
| `K2ImportDirectivePackageMembersCompletionContributor` | Members within import directives |

### Declarations & Overrides
| Contributor | Description |
|---|---|
| `K2DeclarationFromOverridableMembersContributor` | Override/implement member completion |
| `K2DeclarationFromUnresolvedNameContributor` | Create-from-usage for missing declarations |
| `K2ActualDeclarationContributor` | `actual` declaration completion for `expect`/`actual` |

### Special Contexts
| Contributor | Description |
|---|---|
| `K2WhenWithSubjectConditionContributor` | Enum/sealed class entries in `when` conditions |
| `K2SuperEntryContributor` | Super class/interface entries |
| `K2TypeParameterConstraintNameInWhereClauseCompletionContributor` | Type parameter names in `where` clauses |
| `K2SameAsFileClassifierNameCompletionContributor` | Classifiers matching the file name |
| `K2KDocParameterNameContributor` | Parameter names in KDoc `@param` tags |
| `K2VariableOrParameterNameWithTypeCompletionContributor` | Variable/parameter name suggestions based on type |

## Lookup Element Creation

### `KotlinFirLookupElementFactory`
Central factory that routes symbol types to specialized sub-factories:

| Factory | Purpose |
|---|---|
| `FunctionLookupElementFactory` | Functions — handles parameters, defaults, varargs, trailing lambdas, SAM conversions |
| `VariableLookupElementFactory` | Properties and variables — type text, getter/setter hints |
| `ClassLookupElementFactory` | Classes, interfaces, objects — constructors, anonymous objects, type arguments |
| `TypeParameterLookupElementFactory` | Type parameters |
| `TypeLookupElementFactory` | Type completion elements |
| `OperatorNameLookupElementFactory` | Operator function names |
| `NamedArgumentLookupElementFactory` | Named arguments (`name = `) |
| `PackagePartLookupElementFactory` | Package names and members |

Shared utilities live in `factoryUtils.kt` and `insertionUtils.kt`.

### Lookup Objects
- **`KotlinLookupObject`** — Base interface for all Kotlin lookup objects, extends `SerializableLookupObject`.
- **`KotlinCallableLookupObject`** — Stores import strategy, insertion strategy, and signature info for callables.
- **`ClassifierLookupObject`** — Stores import strategy for classifiers.
- **`UniqueLookupObject`** — Ensures element uniqueness in the result set.

## Insertion Handlers

### Strategies

**`CallableInsertionStrategy`** (sealed class) controls how callables are inserted:
- `AsCall` — Insert as function call with parentheses.
- `AsIdentifier` — Insert name only.
- `WithCallArgs(args)` — Insert with pre-filled arguments.
- `WithSuperDisambiguation` — Super call with type disambiguation.

**`ImportStrategy`** (sealed class) controls import behavior:
- `DoNothing` — No import needed.
- `AddImport(nameToImport)` — Add an import statement.
- `InsertFqNameAndShorten(fqName)` — Insert fully qualified name and shorten.

**`ImportStrategyDetector`** determines the appropriate import strategy for a symbol based on its location and existing imports.

### Handler Classes

| Handler | Purpose |
|---|---|
| `WithImportInsertionHandler` | Adds import statements during insertion |
| `TrailingLambdaInsertionHandler` | Inserts trailing lambda with parameter names via live template |
| `AnonymousObjectInsertHandler` | Handles anonymous object instantiation |
| `SmartCompletionTailHandler` | Inserts tail text (closing parens, etc.) for smart completion |
| `BracketOperatorInsertionHandler` | Handles `[]` operator insertion |
| `AdaptToExplicitReceiverInsertionHandler` | Adapts completions to explicit receivers |
| `QualifyContextSensitiveResolutionHandler` | Qualifies names for context-sensitive resolution |
| `InsertRequiredTypeArgumentsInsertHandler` | Inserts required type arguments |
| `WrapSingleStringTemplateEntryWithBracesInsertHandler` | Wraps string template entries with `${}` |

## Weighing & Ranking

Weighers are applied via `Weighers.applyWeighers()` to a `CompletionSorter`. Order determines priority (earlier = higher influence).

| Weigher | Purpose |
|---|---|
| `ExpectedTypeWeigher` | Matches expected type (`MATCHES_PREFERRED` > `MATCHES` > `NONE`) |
| `KindWeigher` | Symbol kind preference: locals > members > non-members |
| `DeprecatedWeigher` | Deprioritizes deprecated symbols |
| `PreferGetSetMethodsToPropertyWeigher` | Prefers properties over getter/setter methods |
| `NotImportedWeigher` | Deprioritizes symbols requiring imports |
| `ClassifierWeigher` | Classifier scope sorting (local > member > imported) |
| `VariableOrFunctionWeigher` | Prioritizes variables over functions in certain contexts |
| `PreferredSubtypeWeigher` | Prefers sealed inheritors in `is`/`as` checks |
| `DurationPreferringWeigher` | Prefers `Duration`-based overloads |
| `K2SoftDeprecationWeigher` | Soft deprecation for old language features |
| `PreferContextualCallablesWeigher` | Prioritizes overridden symbols in containing scope |
| `PreferFewerParametersWeigher` | Fewer parameters ranked higher |
| `PreferAbstractForOverrideWeigher` | Prefers abstract members for override completion |
| `ByNameAlphabeticalWeigher` | Alphabetical fallback ordering |
| `PriorityWeigher` | Respects symbol priority annotations |
| `TrailingLambdaWeigher` | Prefers callables accepting trailing lambdas |
| `TrailingLambdaParameterNameWeigher` | Parameter name matching for trailing lambdas |
| `PreferNamedArgumentCompletionWeigher` | Named argument priority |
| `PreferMatchingArgumentNameWeigher` | Argument name matching |
| `CompletionContributorGroupWeigher` | Contributor group priority (via `getGroupPriority()`) |
| `CallableWeigher` | Callable-specific sorting (receiver type, extension vs. member) |
| `VariableOrParameterNameWithTypeWeigher` | Variable/parameter name with type hints |

**`WeighingContext`** provides the data weighers need: scope context, expected type, receiver types, preferred subtype, contextual symbols cache, and language version settings.

## Performance

### Parallel Execution
`ParallelCompletionRunner` distributes sections across up to 4 threads using `Dispatchers.Default`. Each thread maintains its own `KaSession` and `K2CompletionSectionCommonData`. A shared priority queue orders sections by priority. Results are collected in `K2AccumulatingLookupElementSink` instances and merged back in section order.

Enabled via: `kotlin.k2.parallel.completion.enabled` registry key.

### Lazy Session Properties
`LazyCompletionSessionProperty<T, P>` provides session-scoped lazy initialization for expensive computations like `runtimeType` evaluation and `extensionChecker` creation. Uses `Optional<T>` to distinguish null from uninitialized. Values are stored in `K2CompletionSectionContext`'s `UserDataHolder` and shared across sections within the same session.

### Batched Element Addition
`K2AccumulatingLookupElementSink` queues elements (single or batch) as messages for asynchronous consumption, avoiding per-element overhead when adding to the `CompletionResultSet`.

### Index Filtering
`K2CompletionContext.getIndexNameFilter()` applies different matching strategies based on prefix length:
- Short prefixes (< 4 chars): start-only matching (`startOnlyNameFilter`).
- Longer prefixes or rerun (invocation count ≥ 2): substring matching (`scopeNameFilter`).

### Scope vs. Index: Two-Phase Lookups

Contributors split symbol collection into **scope-based** sections (fast, local) and **index-based** sections (slower, global). The section priority system (`HEURISTIC=10` → `DEFAULT=50` → `FROM_INDEX=100`) ensures scope results appear first while index lookups run later.

**`KtSymbolFromIndexProvider`** is the central index access point, instantiated once per runner execution from `parameters.completionFile`. It exposes name-filter-based methods for different symbol kinds:

| Method | Returns |
|---|---|
| `getKotlinClassesByNameFilter()` | Kotlin classes from stub index |
| `getJavaClassesByNameFilter()` | Java classes from stub index |
| `getTopLevelCallableSymbolsByNameFilter()` | Top-level Kotlin functions and properties |
| `getKotlinCallableSymbolsByNameFilter()` | All Kotlin callables (including members) |
| `getJavaCallablesByNameFilter()` | Java static methods and fields |
| `getExtensionCallableSymbolsByNameFilter()` | Extension functions matching given receiver types |
| `getKotlinEnumEntriesByNameFilter()` | Kotlin enum entries |
| `getJavaFieldsByNameFilter()` | Java fields (including enum constants) |

**Contributors with index sections:**

| Contributor | Scope Sections | Index Sections (`FROM_INDEX`) |
|---|---|---|
| `K2CallableCompletionContributor` | "Local Variables" (HEURISTIC), "Local Extensions", "Local Completion" (DEFAULT) | "Enums from Index", "Index Completion", "Local Extensions with Runtime Type", "Index Completion with Runtime Type" |
| `K2ClassifierCompletionContributor` | "Without Receiver" (DEFAULT) | "Without Receiver From Index" |
| `K2TypeInstantiationContributor` | "Exact Expected Type" (HEURISTIC) | "Subtypes" (PSI-based inheritor search) |
| `K2VariableOrParameterNameWithTypeCompletionContributor` | "From Parameters in File" (HEURISTIC), "Classes From Scope Context" (DEFAULT) | "Classes From Indices" |
| `K2WhenWithSubjectConditionContributor` | "Scope Completion" (HEURISTIC) | "Index" |
| `K2TrailingFunctionParameterNameCompletionContributorBase` | Member scope lookup | `getExtensionCallableSymbolsByNameFilter()` fallback for component functions |

**Common patterns:**

1. **Classifier index access** uses `FirClassifierProvider.getAvailableClassifiersFromIndex()`, a helper that combines `getKotlinClassesByNameFilter()` and `getJavaClassesByNameFilter()` with visibility filtering. Used by `K2ClassifierCompletionContributor`, `K2VariableOrParameterNameWithTypeCompletionContributor`, and `K2WhenWithSubjectConditionContributor`.

2. **Extension index access** uses `getExtensionCallableSymbolsByNameFilter(nameFilter, receiverTypes)` which performs receiver-type-aware lookups. Used by `K2CallableCompletionContributor` via `collectExtensionsFromIndexAndResolveExtensionScope()`.

3. **Deduplication**: scope sections track which symbols were already found; index sections filter those out to avoid duplicates.

4. **Empty prefix guard**: classifiers and when-condition contributors skip index lookups when the prefix is empty, calling `sink.restartCompletionOnAnyPrefixChange()` instead.

5. **Visibility filtering** is applied at two levels during index access: a fast PSI-level pre-filter (`canBeVisible`) passed to the index provider, and a full symbol-level check (`isVisible`) on the resolved results.

### Chain Completion
`K2ChainCompletionContributor` defers expensive chain lookups to a post-completion phase. Chain contributors are registered during main completion and executed only if initial results are insufficient. Enabled via: `kotlin.k2.chain.completion.enabled` registry key.

### Smart Completion
When enabled (`kotlin.k2.smart.completion.enabled`), uses `SmartCompletionPrefixMatcher` to filter results to only elements matching the expected type (`MATCHES` or `MATCHES_PREFERRED`).
