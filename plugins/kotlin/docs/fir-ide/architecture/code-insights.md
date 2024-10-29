# Code insight

## Intentions

Intentions are actions that a user can apply to change code, like specifying a variable type or adding an argument name, and so on.
Intentions can be considered as small refactoring actions.

### Location
- `kotlin.code-insight.intentions.k2`
- `kotlin.code-insight.intentions.shared`

Preferably, an intention should extend `KotlinPsiUpdateModCommandIntention` or `KotlinPsiUpdateModCommandIntentionWithContext`.
It works over the ModCommand API (see `ModCommand`) that allows to perform analysis on a background thread.

### `KotlinPsiUpdateModCommandIntention`
The most important methods you need to implement:
- `getApplicabilityRange()`
    - Determines whether the tool is available in a range; see `ApplicabilityRanges`.
- `isApplicableByPsi(element)`
    - Checks whether it is applicable to an element by PSI only.
    - Should not use the Analysis API due to performance concerns.
- `isApplicableByAnalyze(element)`
    - Checks whether it is applicable to an element by performing some resolution with the Analysis API.
- `invoke(context, element, updater)`
    - Performs changes over a non-physical PSI.
    - No Analysis API context is expected. Use `KotlinPsiUpdateModCommandIntentionWithContext` when you need some Analysis API context.
    - `context` is a ModCommand API action context.
    - Use `updater` (see `ModPsiUpdater`) to perform editor-like actions like moving caret or highlighting an element.

### `KotlinPsiUpdateModCommandIntentionWithContext`
Very similar to `KotlinPsiUpdateModCommandIntention` plus Analysis API context:
- `prepareContext(element)`
    - Provides some context for `apply`.
    - If it is not applicable by analysis, functions should return `null`.
    - Guaranteed to be executed from a read action.
- `invoke(actionContext, element, preparedContext, updater)`
    - Performs changes over a non-physical PSI.
    - `context` contains both an Analysis API context and a ModCommand API action context.

## Inspections

Inspections are some kind of checkers, or linters.
They inspect code and report some kind of warnings and can recommend how to change code to improve it.

### Location
- `kotlin.code-insight.inspections.k2`
- `kotlin.code-insight.inspections.shared`

There are two base classes for inspections: `AbstractKotlinApplicableInspection` and `AbstractKotlinApplicableInspectionWithContext`

### `AbstractKotlinApplicableInspection`
- `isApplicableByPsi(element)`, `getApplicabilityRange()`, `isApplicableByAnalyze(element)`
    - Are the same as for `AbstractKotlinApplicableModCommandIntention`.
- `shouldApplyInWriteAction()`
    - Whether `apply` should be performed in a write action.
- `apply(element, project, updater)`
    - Applies a fix to an element.
    - ModCommand API based.
    - Executed on a background thread.

### `AbstractKotlinApplicableInspectionWithContext`
Similar to `AbstractKotlinApplicableInspection` plus an Analysis API context:
- `prepareContext(element)`
    - Prepares an Analysis API context on a physical PSI.
- `apply(element, context, project, updater)`
    - `element` is a non-physical PSI.
    - if `context` keeps some physical PSI, you need to convert it with `ModPsiUpdater#getWritable` before a non-physical file is changed.

## Quick Fixes

Quick fixes are actions on errors and warnings provided by the compiler - they are registered in `KotlinK2QuickFixRegistrar`.

### Location
- `kotlin.code-insight.fixes.k2`

There are several ways how to create `QuickFixFactory`:
- `quickFixesPsiBasedFactory`
    - to perform a pure PSI-based quick fix without any Analysis API calls.
- `diagnosticModCommandFixFactory`
    - Preferable.
    - Produces a list of `KotlinApplicatorBasedModCommand` that requires `KotlinModCommandApplicator`.
    - `modCommandApplicator` is a DSL helper to build `KotlinModCommandApplicator`
        - `getActionName: ((PSI, INPUT) -> String)`
        - `getFamilyName: (() -> String)`
        - `isApplicableByPsi: ((PSI) -> Boolean)`
        - `applyTo: ((PSI, INPUT, context, updater) -> Unit)`
- `diagnosticFixFactories`
    - Produces a list of `QuickFixActionBase`.

## Completion

### Modifying completion position

See the documentation for `com.intellij.codeInsight.completion.CompletionParameters.getPosition`.

To modify the *dummy identifier* string which is inserted into the copied file at the caret offset,
change `org.jetbrains.kotlin.idea.completion.api.CompletionDummyIdentifierProviderService.provideDummyIdentifier`.

### Adding elements

See the documentation for `com.intellij.codeInsight.completion.CompletionContributor`.

The contributor for the Kotlin plugin is `org.jetbrains.kotlin.idea.completion.KotlinFirCompletionContributor`. It performs the following tasks:
1. Detects the position context for the completion position. See `org.jetbrains.kotlin.idea.util.positionContext` for existing position contexts.
2. Invokes a group of `FirCompletionContributor` subclasses defined for the detected position context.
See `org.jetbrains.kotlin.idea.completion.impl.k2.Completions` for the context-to-contributors mapping.

To add a new type of completion, consider the following options:
- Add a new subclass of `FirCompletionContributor`.
- Modify an existing subclass if the new functionality fits well into it.

Test data: `community/plugins/kotlin/completion/testData/basic`.

### Sorting elements

The order of lookup elements is controlled by `com.intellij.codeInsight.lookup.LookupElementWeigher`.\
See `org.jetbrains.kotlin.idea.completion.weighers` for existing *weighers*.

Test data: `community/plugins/kotlin/completion/testData/weighers`. Note that:
* Currently, tests for weighers don't allow checking the order of lookup elements with the same item text.
  As a workaround, create a test data with the `// WITH_ORDER` directive in `community/plugins/kotlin/completion/testData/basic`
  and specify the elements with their tail texts.
* ML-ranking is disabled in tests.

#### Frozen elements

See the documentation for `com.intellij.codeInsight.completion.CompletionContributor`.

### Inserting an element

See the documentation for `com.intellij.codeInsight.lookup.LookupElement.handleInsert`.

Test data: `community/plugins/kotlin/completion/testData/handlers`.\
To distinguish between lookup elements with the same item text, use the `// TAIL_TEXT:` directive.

#### Completion char

Test data: `community/plugins/kotlin/completion/testData/handlers/charFilter`.

### Helpful links
IJ Refresher Course: [Completion by Peter Gromov](https://www.youtube.com/watch?v=tkHN8Dv272w&list=PLYUy7DvA-7IcZ6W2DeHnl03EqOpfb3t1R)

## Line Markers or Gutter Icons

## Postfix and live templates