# Code insight

## Intentions

Intentions are actions that a user can apply to change code, like specifying a variable type or adding an argument name, and so on.
Intentions can be considered as small refactoring actions.

### Location
- `kotlin.code-insight.intentions.k2`
- `kotlin.code-insight.intentions.shared`

Preferably, an intention should extend `KotlinApplicableModCommandAction`.
It works over the ModCommand API that allows to perform analysis on a background thread.\
To learn more about the ModCommand API,
read [the short API description and migration guide](https://docs.google.com/document/d/1-2_cNjq-Mc28j0eCX1TEuMM-k6UXKvfPTutvIBafIJA/).

### `KotlinApplicableModCommandAction`
The most important methods you need to implement:
- `getApplicableRanges(element)`
    - Determines whether the tool is available in a range; see `ApplicabilityRanges`.
- `isApplicableByPsi(element)`
    - Checks whether it is applicable to an element by PSI only.
    - Should not use the Analysis API due to performance concerns.
- `prepareContext(element)`
    - Prepares an Analysis API context on a physical PSI that is provided for `invoke`.
    - Must return `null` if it is not applicable by analysis.
    - In cases where the inspection neither uses the Analysis API nor needs any context,
      the `prepareContext` return type must be non-nullable `Unit`, and the `prepareContext` body must be empty.
    - Guaranteed to be executed from a read action.
- `invoke(actionContext, element, elementContext, updater)`
    - `actionContext` is a ModCommand API action context.  
    - `element` is a non-physical PSI.
    - `elementContext` is an Analysis API context.
    - Use `updater` (see `ModPsiUpdater`) to perform editor-like actions like moving caret or highlighting an element.
- `getPresentation(context, element)`
    - Provides the whole presentation, including an action text, icon, priority, and highlighting in the editor.
    - The default implementation of `getPresentation` is `Presentation.of(familyName)`.
    - To customize `ModCommandAction` priority, use `Presentation#withPriority` and  
      do NOT use the `HighPriorityAction` and `LowPriorityAction` marker interfaces for `ModCommandActions`,  
      as they have no effect on them.

### `SelfTargetingIntention` and `SelfTargetingRangeIntention`
- `startInWriteAction()`
    - Indicates whether the action must be invoked inside a write action.
- `isApplicableTo(element, caretOffset)`
    - Checks whether it is applicable to an element at caret offset.
- `applyTo(element, editor)`
    - Performs changes over a physical PSI.
- Use `SelfTargetingIntention` and `SelfTargetingRangeIntention` when the action must not be invoked inside a write action.

## Inspections

Inspections are some kind of checkers, or linters.
They inspect code and report some kind of warnings and can recommend how to change code to improve it.

### Location
- `kotlin.code-insight.inspections.k2`
- `kotlin.code-insight.inspections.shared`

There are several classes for inspections: `KotlinApplicableInspectionBase.Simple`, `KotlinDiagnosticBasedInspectionBase`,
and `AbstractKotlinInspection`.

### `KotlinApplicableInspectionBase.Simple`
- `isApplicableByPsi(element)`, `getApplicableRanges(element)`, and `prepareContext(element)`.
    - Are the same as for `KotlinApplicableModCommandAction`.
- `createQuickFix(element, context)`
    - Returns a `KotlinModCommandQuickFix`.
    - Note: The `HighPriorityAction` and `LowPriorityAction` marker interfaces apply to `KotlinModCommandQuickFix`.

### `KotlinDiagnosticBasedInspectionBase`
A subclass of `KotlinApplicableInspectionBase.Simple` that is intended
to create inspections from [extra compiler checks](https://kotlinlang.org/docs/whatsnew21.html#kotlin-k2-compiler)
(`KaDiagnosticCheckerFilter.ONLY_EXTENDED_CHECKERS`), whose warnings are not highlighted by default, unlike warnings
from regular checks (`KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS`).\
Extending `KotlinDiagnosticBasedInspectionBase` also allows to run extra compiler checks and possibly apply fixes in batch.
- `prepareContextByDiagnostic(element, diagnostic)`
    - Prepares the context that is provided for `getProblemDescription` and `createQuickFix`.
    - `element` is a physical PSI.
    - `diagnostic` is a specific diagnostic associated with the element.

### `AbstractKotlinInspection`
- Use `AbstractKotlinInspection` if the quick fix cannot be `KotlinModCommandQuickFix`

## Quick Fixes

Quick fixes are actions on errors and warnings provided by the compiler – they are registered in `KotlinK2QuickFixRegistrar`.

### Location
- `kotlin.code-insight.fixes.k2`
- `kotlin.fir.frontend-independent`

There are two ways to register a quick-fix factory:

1. `registerFactory(factory)`
    - `factory` can be:
        - `KotlinQuickFixFactory.ModCommandBased`, which is preferable as the one that produces a list of `ModCommandActions`:
            - Use `PsiUpdateModCommandAction` if no element context is required.
            - Use `KotlinPsiUpdateModCommandAction.ElementBased` if an element context is required.
        - `KotlinQuickFixFactory.IntentionBased`, which produces a list of `IntentionAction`.

2. `registerPsiQuickFixes(diagnosticClass, factories)`
    - `factories` are pure PSI-based quick-fix factories that do not make Analysis API calls.
       They can be created using `quickFixesPsiBasedFactory`.
    - You should use this way only in cases of reusing quick fixes for K2 that have already been implemented for K1.

### Testing actions

* If the action priority differs from NORMAL, it is good practice to use the `// PRIORITY` directive in test data files.
  For example:
```kotlin
// PRIORITY: HIGH
```
If you call `updater.moveCaretTo()` or `editor.moveCaret()` inside the `invoke` or `applyTo` method,
including `<caret>` in the after-test data file is recommended.

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