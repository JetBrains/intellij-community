# Code insight

## Intentions

Intentions are actions that a user can apply to change code, like specifying a variable type or adding an argument name, and so on.
Intentions can be considered as small refactoring actions.

### Location
- `kotlin.code-insight.intentions.k2`

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

Inspections are checkers, or linters.
They inspect code, report problems, and can offer quick fixes that change the code.

This section is the guideline for a new inspection and for a change to an existing one.
Follow it step by step.

### Location

| What | Where |
|---|---|
| Inspection class | `code-insight/inspections-k2/src/org/jetbrains/kotlin/idea/codeInsight/inspections/` (module `intellij.kotlin.codeInsight.inspections`) |
| Registration | `code-insight/inspections-k2/resources/intellij.kotlin.codeInsight.inspections.xml` |
| Description | `code-insight/descriptions/resources-en/inspectionDescriptions/<ShortName>.html` |
| Messages | `base/resources/resources-en/messages/KotlinBundle.properties` |
| Test data | `code-insight/inspections-k2/tests/testData/inspectionsLocal/<inspectionName>/` |
| Generated tests | `K2LocalInspectionTestGenerated` (module `intellij.kotlin.codeInsight.inspections.tests`) |
| Base classes | `code-insight/api/src/org/jetbrains/kotlin/idea/codeinsight/api/applicable/inspections/` |
| Shared utilities | `code-insight/utils`, `code-insight/impl-base`, `base/code-insight` |

Use a subpackage when one fits: `collections`, `coroutines`, `declarations`, `expressions`, `java`, `kdoc`, `diagnosticBased`.

### Before you write code

1. Find the KTIJ issue. If no issue exists, create one.
2. Search for an existing inspection, intention, or compiler diagnostic that covers the case.
   Extend it instead of adding a duplicate.
3. Write down the positive cases and the negative cases first.
   Use the [Quality checklist](#quality-checklist) to find the negative cases.

### Choose the base class

Use the applicable base classes. They are mandatory for a new inspection.

| Case | Base class |
|---|---|
| One quick fix | `KotlinApplicableInspectionBase.Simple<E, C>` |
| No quick fix, or more than one | `KotlinApplicableInspectionBase<E, C>`. Override `InspectionManager.createProblemDescriptor`. |
| Extended compiler checker, diagnostic on a `Kt…` element | `KotlinKtDiagnosticBasedInspectionBase<E, D, C>` |
| Extended compiler checker, diagnostic on a `PsiElement` | `KotlinPsiDiagnosticBasedInspectionBase<E, D, C>` |

Use `AbstractKotlinInspection` only for these cases. Write the reason in the commit message.
- The quick fix cannot be a ModCommand. For example, it opens a dialog or starts a refactoring.
- The inspection must report many elements from one traversal, and one element per visit does not fit.

Do not use `AbstractApplicabilityBasedInspection` or `IntentionBasedInspection`. They are legacy.

### Structure of an applicable inspection

`KotlinApplicableInspectionBase.visitTargetElement` calls the methods in this order.
Each step must be cheaper than the next one.

1. `isAvailableForFile(file)`
    - Return `false` for a file where the inspection can never apply. Call `super` first.
    - Gate new syntax with `file.languageVersionSettings.supportsFeature(LanguageFeature.X)`.
    - Skip injected fragments with `file.isNotInjectedOrShouldBeAnalyzed` if the check is not valid there.
    - Skip the file when the required library or platform is absent.
2. `buildVisitor(holder, isOnTheFly)`
    - Visit only the element type that you need. Use a helper such as `callExpressionVisitor { }`.
    - Call `visitTargetElement(element, holder, isOnTheFly)` from the visitor.
3. `isApplicableByPsi(element)`
    - Do all the PSI-only checks here. The Analysis API is forbidden here (`forbidAnalysis`).
    - Reject most elements in this step. For example, check the callee name before you resolve the call.
4. `getApplicableRanges(element)`
    - Return the narrowest range. Highlight the name or the keyword, not the whole declaration or call.
    - Use `ApplicabilityRange.self/single/multiple/union` or the presets in `ApplicabilityRanges`.
    - The ranges are relative to `element`.
5. `context(session: KaSession) prepareContext(element)`
    - Do all the Analysis API work here. Return `null` when the inspection does not apply.
    - Use `Unit` as the context if the fix needs no data. Return `condition.asUnit` for a boolean check.
    - Use a `data class Context` if the fix needs data.
6. `getProblemDescription(element, context)` and `createQuickFix(element, context)`
    - Take the messages from `KotlinBundle`.
    - Keep the default `ProblemHighlightType.GENERIC_ERROR_OR_WARNING`. The user can then change the level in the profile.
    - Use `INFORMATION` only for a fix-only suggestion. The base class drops it in batch mode.

Rules for the context:
- Do not store `KaSession`, `KaSymbol`, `KaType`, or a call. Use `KaSymbolPointer` if you must keep a symbol.
- Do not store a raw `PsiElement`. Use `SmartPsiElementPointer`, a name, an `FqName`, a `ClassId`, or a string.

Rules for the quick fix:
- Extend `KotlinModCommandQuickFix<E>`. It runs in a background read action on a non-physical copy of the file.
- Do not call `analyze` in `applyFix`. Compute everything in `prepareContext`.
- Change only `element` or elements from `updater.getWritable(...)`.
  For an element in another file, map it to the copy explicitly.
- `getFamilyName()` contains no element-specific text. Use `getName()` for the element-specific text.
- Do not touch a symbol in `getName()`, `getFamilyName()`, or `toString()`.
- Keep comments. Use `CommentSaver` around a `replace()`. If you cannot keep a comment, do not report the problem.
- Generate code from source text, not from `name.asString()`. Use `quoteIfNeeded()` and shorten references (`ShortenReferencesFacility`) for names that need backticks or a qualifier.
- Put the caret with `updater.moveCaretTo(...)` if the user must continue at a specific place.

An example of a small inspection is `CopyWithoutNamedArgumentsInspection`. An example with a `Unit` context
and a language feature gate is `RedundantContextSensitiveResolutionQualifierInspection`.

### Inspections from extended compiler checkers

Extended checkers are [extra compiler checks](https://kotlinlang.org/docs/whatsnew21.html#kotlin-k2-compiler)
(`KaDiagnosticCheckerKind.EXTENDED`). The IDE does not highlight their warnings by default.
A diagnostic-based inspection shows such a warning, makes it configurable in the profile, and supports batch fixes.
- Set `diagnosticType` to the `KaFirDiagnostic` class.
- Implement `prepareContextByDiagnostic(element, diagnostic)`. `prepareContext` is final.
- Choose `E` so that it is the PSI type of the element that has the diagnostic.

Quick fixes for regular compiler diagnostics are not inspections. Register them in `KotlinK2QuickFixRegistrar`.
See [Quick Fixes](#quick-fixes).

### Options

Use a constructor property with `@JvmField` and override `getOptionsPane()` with `OptPane.pane(...)`.
See `RemoveCurlyBracesFromTemplateInspection` for an example.
Add a test with a `settings.xml` file in the test data directory for each option value.

### Registration

Add a `<localInspection>` entry to `intellij.kotlin.codeInsight.inspections.xml`:

```xml
<localInspection implementationClass="org.jetbrains.kotlin.idea.codeInsight.inspections.FooInspection"
                 groupPath="Kotlin" groupBundle="messages.KotlinBundle" groupKey="group.names.redundant.constructs"
                 enabledByDefault="false" level="WEAK WARNING"
                 language="kotlin" key="inspection.foo.display.name" bundle="messages.KotlinBundle"/>
```

- Make the class `internal`. Name it `<ShortName>Inspection`.
- The platform takes the short name from the class name without the `Inspection` suffix.
  Set `shortName` only to keep the ID of an existing inspection.
- Use one of the existing `group.names.*` keys. Do not add a new group without agreement from the team.
- Set `cleanupTool="true"` and implement `CleanupLocalInspectionTool` only if the fix is always safe.
  Code Cleanup applies the fix without a review by the user.
- For a redundant construct, set `editorAttributes="NOT_USED_ELEMENT_ATTRIBUTES"`.

#### Default level

A new inspection starts with a conservative level:
- Use `enabledByDefault="false"`, or `level="WEAK WARNING"` or `level="INFORMATION"`.
- Use `level="WARNING"` only for a probable bug, and only when a run on a large project shows no false positives.
- Do not use `ERROR`. The compiler reports errors.

Raise the level in a separate commit after the inspection has users and no open false-positive issues.

### Description and messages

- Write the description in `<ShortName>.html`. Use this structure:
  1. One sentence that starts with "Reports".
  2. A paragraph that tells why the code is a problem.
  3. `<p><b>Example:</b></p>` with a `<pre><code>` block.
  4. `<p>After the quick-fix is applied:</p>` with a `<pre><code>` block.
- Add the keys to `KotlinBundle.properties`:
    - `inspection.<name>.display.name` for the display name.
    - `inspection.<name>.problem.description` for the problem message.
    - `inspection.<name>.fix.text` for the fix family name, or reuse an existing key with the same text.
- Write the display name as a noun phrase, for example "Redundant underscore entry".
  Write the fix text as an imperative, for example "Remove redundant underscore entry".

### Tests

Put the test data in `code-insight/inspections-k2/tests/testData/inspectionsLocal/<inspectionName>/`.
The generator model for this directory is recursive, so a new subdirectory needs no change in the generator.
- Add a `.inspection` file with the fully qualified class name of the inspection.
- Add one `<case>.kt` file for each case. Put `<caret>` on the element that the inspection must report.
- Add `<case>.kt.after` for each case where the fix applies. Do not add it for a negative case.
- Name a negative case with a `NoWarn` or `NotApplicable` suffix, for example `lateinitNoWarn.kt`.

Directives for a local inspection test:

| Directive | Meaning |
|---|---|
| `// PROBLEM: none` | The inspection must not report a problem at the caret. |
| `// PROBLEM: <text>` | The expected problem message. |
| `// HIGHLIGHT: <type>` | The expected `ProblemHighlightType`, for example `INFORMATION`. |
| `// FIX: <text>` | The fix to apply when there is more than one. `// FIX: none` means no fix. |
| `// NO_FIX: <text>` | A fix that must not be offered. |
| `// K2_ERROR: <text>` / `// K2_AFTER_ERROR: <text>` | The expected compiler errors before and after the fix. |
| `// COMPILER_ARGUMENTS: <args>` | Compiler arguments, for example `-XXLanguage:+Feature`. |
| `// LANGUAGE_VERSION: <v>` / `// API_VERSION: <v>` | The language and API versions. |
| `// WITH_COROUTINES`, `// RUNTIME_WITH_FULL_JDK`, `// JS` | Other project descriptors. The standard library is on by default. |
| `// IGNORE_K2` | A known failure. The test fails if it starts to pass. |

The `// "<fix text>" "true"` header and `// FUS_QUICKFIX_NAME:` are quick fix test directives.
The local inspection test does not read them, so do not add them.

Regenerate the tests with the "Generate Kotlin Tests" run configuration. Commit the changed `K2LocalInspectionTestGenerated.java`.
Do not edit a generated file by hand.

Run the tests from the `community` directory.
The nested test class name is the test data directory name in upper camel case, inside `InspectionsLocal`:

```shell
./tests.cmd --module intellij.kotlin.codeInsight.inspections.tests \
  --test 'org.jetbrains.kotlin.idea.inspections.tests.K2LocalInspectionTestGenerated$InspectionsLocal$RedundantDestructuringUnderscore'
```

Use `inspections/<name>/inspectionData/` with `inspections.test` and `expected.xml` only for a test that must check all problems in a file.

### Quality checklist

These defect categories occur again and again in fixes to existing inspections.
For each category that applies, add a negative test, a fix, or both.

| Category | What to check |
|---|---|
| Expected type | A "redundant" construct can be necessary for an expected type, an overload, or type inference. Do not report when the type of the result would change. |
| Call forms | Test a plain call, `a.b()`, `a?.b()`, `a!!.b()`, an infix call, an operator call, `invoke`, a callable reference, and a call chain. |
| Nullability | Test nullable receivers and platform types. Unwrap a flexible type before you compare class IDs. Keep checks that a later smart cast needs. |
| Resolution | A call can be ambiguous or unresolved. Do not assume one successful call. Handle `null` from every resolve call. |
| Broken code | The PSI can contain error elements. Use `typeIfSafeToResolve`, `firstOrNull`, and `?:`. Do not use `!!`, `single()`, or `error()` on data from user code. |
| Comments and formatting | The fix keeps all comments and KDoc once. Add a test with comments inside the changed range. |
| Names | Test names that need backticks, `this@label`, labeled lambdas, and a name that shadows another one. |
| Side effects | Do not remove an expression that can have a side effect, for example a custom getter, a delegate, or a call. |
| Declarations | Handle `expect` and `actual` declarations together. Do not report "unused" on an `expect` declaration. |
| Compiler plugins | Take modality and visibility from the symbol, not from the PSI. The all-open and JPA plugins change them. |
| Language features | Add a test with the feature off for each `supportsFeature` gate. |
| Location | Do not report in import directives, the package directive, or injected fragments if the check is not valid there. |
| Java interoperability | Test Java getters and setters, Java fields, and SAM conversions. |
| Suppression | An inspection that inspects other elements in one visit must check suppression for each reported element. |
| User intent | Do not report a name `_`, or code where the user handles the concern explicitly. |
| Performance | Do not search indexes or references in the visitor. Compute expensive fix data only when the fix runs. |

### Check on a real project

Run the inspection on a large Kotlin project before you commit, for example this repository.
Use "Run Inspection by Name" in a development IDE.
- Check the time and the memory usage.
- Review a sample of the reported problems for false positives.
- Apply the fix to some problems and compile the result.

Write the project and the result in the commit message.

### After the change

- Run `lint_files` on the changed files, if the IDE tools are available.
- Use the `commits` skill for the commit message. Put the `KTIJ-NNNNN` issue and the `[kotlin]` tag in the subject.

## Quick Fixes

Quick fixes are actions on errors and warnings provided by the compiler – they are registered in `KotlinK2QuickFixRegistrar`.

### Location
- `kotlin.code-insight.fixes.k2`
- `kotlin.fir.frontend-independent`

There are two ways to register a quick-fix factory:

1. `registerFactory(factory)`
    - `factory` can be:
        - `KotlinQuickFixFactory.ModCommandBased`, which is preferable as the one that produces a list of `ModCommandActions`:
            - Use `KotlinPsiUpdateModCommandAction.ElementContextless` if no element context is required.
            - Use `KotlinPsiUpdateModCommandAction.ElementBased` if an element context is required.
            - Both classes provide the "Fix All" option by default.
            - Override `getActionPresentation(context, element)` to customize the presentation of the quick fix.
            - Override `addFixAllOption(context, element)` and return `false` to disable the default "Fix All" option.
            - Do not override `getPresentation(context, element)`; use `getActionPresentation(context, element)` instead.
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