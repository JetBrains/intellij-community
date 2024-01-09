# Code insight

## Intentions

Intentions are action that user could apply to change code like specify type of variable or add argument name and so on. 
It could be considered as a small refactoring actions.

### Location
- `kotlin.code-insight.intentions-k2`
- `kotlin.code-insight.intentions-shared`

Preferably intention should extend `AbstractKotlinApplicableModCommandIntention` or `AbstractKotlinModCommandWithContext`.
It works over ModCommand API (see `ModCommand`) that allows to perform analysis on a background thread.

### `AbstractKotlinApplicableModCommandIntention`
The most important methods you need to implement:
 - `isApplicableByPsi(element)`
   - Checks whether it is applicable to element by PSI only. 
   - Do not use the Analysis API due to performance concerns. 
 - `getApplicabilityRange()`
   - Determines whether the tool is available in a range, see `ApplicabilityRanges`
 - `isApplicableByAnalyze(element)`
   - Checks whether it is applicable to element by performing some resolution with the Analysis API.
 - `invoke(context, element, updater)`
   - Performs changes over a non-physical PSI. 
   - No Analysis API context is expected. Use `AbstractKotlinModCommandWithContext` when you need some Analysis API context.
   - `context` is a ModCommand API action context.
   - Use `updater` (see `ModPsiUpdater`) to perform some editor-like actions like to move caret or highlight element.

### `AbstractKotlinModCommandWithContext`
Very similar to `AbstractKotlinApplicableModCommandIntention` plus Analysis API context:
 - `prepareContext(element)`
   - Provides some context for `apply`.
   - If it is not applicable by analyze, functions should return `null`. 
   - Guaranteed to be executed from a read action.
 - `apply(element, context, updater)`
   - Performs changes over a non-physical PSI. 
   - `context` contains both Analysis API context and ModCommand API action context.

## Inspections

Inspections are some kind of checkers, or linters.
They inspect code and report some kind of warnings and could recommend how to change code to improve it.

### Location
 - `kotlin.code-insight.inspections-k2`
 - `kotlin.code-insight.inspections-shared`

There are two base classes for inspections: `AbstractKotlinApplicableInspection` and `AbstractKotlinApplicableInspectionWithContext`

### `AbstractKotlinApplicableInspection` 
 - `isApplicableByPsi(element)`, `getApplicabilityRange()`, `isApplicableByAnalyze(element)`
   - are the same as for `AbstractKotlinApplicableModCommandIntention`
 - `shouldApplyInWriteAction`
   - Whether `apply` should be performed in a write action.
 - `apply(element, project, updater)`
   - Applies a fix to element. 
   - ModCommand API based
   - Executed on a background thread

### `AbstractKotlinApplicableInspectionWithContext`
Similar to `AbstractKotlinApplicableInspection` plus Analysis API context:
 - `prepareContext(element)`
   - prepares Analysis API context on a physical PSI
 - `apply(element, context, project, updater)`
   - `element` is a non-physical PSI
   - if `context` keeps some physical PSI you need to convert it with `ModPsiUpdater#getWritable` before non-physical file would be changed

## Quick Fixes
Quick fixes are actions on errors and warnings provided by compiler - they are registered in `KotlinK2QuickFixRegistrar`.

There are several ways how to create `QuickFixFactory`:
 - `quickFixesPsiBasedFactory`
   - to perform pure PSI-based quick fix without any Analysis API calls.
 - `diagnosticModCommandFixFactory`
   - Preferable
   - Produces a list of `KotlinApplicatorBasedModCommand` that requires `KotlinModCommandApplicator`
   - `modCommandApplicator` is DSL helper to build `KotlinModCommandApplicator`
     - `getActionName: ((PSI, INPUT) -> String)`
     - `getFamilyName: (() -> String)`
     - `isApplicableByPsi: ((PSI) -> Boolean)`
     - `applyTo: ((PSI, INPUT, context, updater) -> Unit)`
 - `diagnosticFixFactories`
   - Produces a list of `QuickFixActionBase`

## Line Markers or Gutter Icons

## Postfix and live templates