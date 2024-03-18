# Code insight

## Intentions

Intentions are actions that a user can apply to change code, like specifying a variable type or adding an argument name, and so on.
Intentions can be considered as small refactoring actions.

### Location
- `kotlin.code-insight.intentions.k2`
- `kotlin.code-insight.intentions.shared`

Preferably, an intention should extend `AbstractKotlinApplicableModCommandIntention` or `AbstractKotlinModCommandWithContext`.
It works over the ModCommand API (see `ModCommand`) that allows to perform analysis on a background thread.

### `AbstractKotlinApplicableModCommandIntention`
The most important methods you need to implement:
- `isApplicableByPsi(element)`
    - Checks whether it is applicable to an element by PSI only.
    - Should not use the Analysis API due to performance concerns.
- `getApplicabilityRange()`
    - Determines whether the tool is available in a range; see `ApplicabilityRanges`.
- `isApplicableByAnalyze(element)`
    - Checks whether it is applicable to an element by performing some resolution with the Analysis API.
- `invoke(context, element, updater)`
    - Performs changes over a non-physical PSI.
    - No Analysis API context is expected. Use `AbstractKotlinModCommandWithContext` when you need some Analysis API context.
    - `context` is a ModCommand API action context.
    - Use `updater` (see `ModPsiUpdater`) to perform editor-like actions like moving caret or highlighting an element.

### `AbstractKotlinModCommandWithContext`
Very similar to `AbstractKotlinApplicableModCommandIntention` plus Analysis API context:
- `prepareContext(element)`
    - Provides some context for `apply`.
    - If it is not applicable by analysis, functions should return `null`.
    - Guaranteed to be executed from a read action.
- `apply(element, context, updater)`
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

## Line Markers or Gutter Icons

## Postfix and live templates