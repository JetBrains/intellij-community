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
- `getActionPresentation(context, element)`
    - Provides the whole presentation, including an action text, icon, priority, and highlighting in the editor.
    - The default implementation is `Presentation.of(familyName)`.
    - `getPresentation` is final. It adds the Fix All option unless `addFixAllOption(context, element)` returns `false`.
    - To customize `ModCommandAction` priority, use `Presentation#withPriority` and  
      do NOT use the `HighPriorityAction` and `LowPriorityAction` marker interfaces for `ModCommandActions`,  
      as they have no effect on them.

### Tests

Put the test data in `idea/tests/testData/intentions/<intentionName>/`.
Register the directory in `GenerateK2IntentionTests.kt` with a `model(...)` line, then run "Generate Kotlin Tests".
- Add a `.intention` file with the fully qualified class name of the intention.
- Add `<case>.kt` with `<caret>`, and `<case>.kt.after` for each case where the intention applies.
- Use `// IS_APPLICABLE: false` for a negative case, and `// INTENTION_TEXT: <text>` for the expected text.

Plan the cases before you write code. See [Test data coverage](#test-data-coverage).

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

The IntelliJ inspection framework calls `isAvailableForFile` and `buildVisitor` before it inspects elements in a file.
`buildVisitor` returns a `KtVisitor` that calls `visitTargetElement` for each candidate element.
`visitTargetElement` runs stages 3 through 6 for that element.
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
    - Use PSI only for cheap structural checks here.
      `prepareContext` uses the Analysis API for semantic checks and context data.
      The Analysis API is forbidden here (`forbidAnalysis`).
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
- Extend `KotlinModCommandQuickFix<E>`. It computes changes off the EDT on a non-physical file copy.
  The IntelliJ Platform can preview the result and apply it to the real file.
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

Plan the cases before you write code. See [Test data coverage](#test-data-coverage).

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

K1 is sunset. Do not add `// IGNORE_K2` or another K1 directive. Make the test pass instead.

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

Quick fixes are actions on errors and warnings that the compiler reports.
This section is the guideline for a new quick fix and for a change to an existing one.

A quick fix for a regular compiler diagnostic goes to a quick fix registrar.
A warning from an extended checker is not highlighted by default. Make it a diagnostic-based inspection instead.
See [Inspections from extended compiler checkers](#inspections-from-extended-compiler-checkers).

### Location

| What | Where |
|---|---|
| Fix factories and actions | `code-insight/fixes-k2/src/org/jetbrains/kotlin/idea/k2/codeinsight/fixes/` (module `intellij.kotlin.codeInsight.fixes`) |
| Registrar | `KotlinK2QuickFixRegistrar` in the same package |
| Shared PSI-only fixes | `frontend-independent/src/org/jetbrains/kotlin/idea/quickfix/` (module `kotlin.fir.frontend-independent`) and `code-insight/impl-base/.../quickFix/` |
| Factory API | `code-insight/api/src/org/jetbrains/kotlin/idea/codeinsight/api/applicators/fixes/` |
| Action base classes | `code-insight/api/src/org/jetbrains/kotlin/idea/codeinsight/api/applicable/intentions/KotlinPsiUpdateModCommandAction.kt` |
| Messages | `base/resources/resources-en/messages/KotlinBundle.properties` |
| Test data | `idea/tests/testData/quickfix/<group>/` |
| Generated tests | `HighLevelQuickFixTestGenerated` (module `intellij.kotlin.codeInsight.fixes.tests`) |

Other registrars use the same extension point, `org.jetbrains.kotlin.codeinsight.quickfix.registrar`.
They are `K2CreateFromUsageQuickFixesRegistrar`, `ChangeSignatureQuickFixesRegistrar`, and the registrars of the compiler plugins.
Put a fix for a compiler plugin diagnostic in the registrar of that plugin.

### Before you write code

1. Find the KTIJ issue and the diagnostic class, for example `KaFirDiagnostic.ArgumentTypeMismatch`.
2. Search `KotlinK2QuickFixRegistrar` for the diagnostic. Extend an existing factory instead of adding a second fix with the same effect.
3. Search `frontend-independent` and `impl-base` for a fix that you can reuse, for example `AddModifierFix` or `RemoveModifierFixBase`.
4. Write down the PSI forms on which the compiler reports the diagnostic.
   Write down the cases where the fix cannot remove the diagnostic. These are your negative tests.

### Write the factory

Put the factory in an `internal object XxxFixFactory`, or `XxxFixFactories` for several diagnostics.
Put the action in a `private class` in the same object.

```kotlin
internal object RemoveUnnamedPropertyFixFactory {
    val unnamedPropertyWithImplicitIgnorableTypeFixFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnnamedPropertyWithImplicitIgnorableType ->
            val property = diagnostic.psi.getParentOfType<KtProperty>(strict = false) ?: return@ModCommandBased emptyList()
            if (property.name != "_" || property.initializer == null) return@ModCommandBased emptyList()
            listOf(RemoveUnnamedPropertyFix(property))
        }

    private class RemoveUnnamedPropertyFix(element: KtProperty) :
        KotlinPsiUpdateModCommandAction.ElementContextless<KtProperty>(element) {
        override fun getFamilyName(): String = KotlinBundle.message("remove.unused.underscore.declaration")
        override fun invoke(context: ActionContext, element: KtProperty, updater: ModPsiUpdater) {
            val initializer = element.initializer ?: return
            element.replace(initializer.copy())
        }
    }
}
```

Choose the factory type:

| Factory | Use when |
|---|---|
| `KotlinQuickFixFactory.ModCommandBased` | Default. The fix changes PSI, opens a chooser, or starts a template. |
| `KotlinQuickFixFactory.IntentionBased` | Only if ModCommand cannot express the fix. For example, it changes the project model or the build, or opens a refactoring dialog. |
| `quickFixesPsiBasedFactory { psi -> ... }` | Only to register an existing PSI-only fix. It must not use the Analysis API. |

Rules for the factory:
- The factory runs in a `KaSession`. Do all the resolution and the type checks here.
- Return `emptyList()` when the fix cannot remove the diagnostic. Do not offer a fix that leaves the code red.
- Use the typed properties of the diagnostic, for example `diagnostic.expectedType`, before you navigate from `diagnostic.psi`.
  `diagnostic.psi` is often a broad type such as `PsiElement`.
- Use `as?`, `getParentOfType`, and `?: return@ModCommandBased emptyList()`. Do not use `!!` or `first()` on PSI navigation.
- Do not search indexes or references for each diagnostic. If you must, register the factory in `lazyList`, as the import fixes do.
  The platform then builds a lazy fix only when the user opens the list of fixes.
- If the fix edits a declaration that a reference resolves to, check `canRefactorElement()`.
  Do not offer the fix for library, compiled, or read-only code.

### Write the action

Choose the action base class:

| Base class | Use when |
|---|---|
| `KotlinPsiUpdateModCommandAction.ElementContextless<E>` | The fix needs no data from the analysis. |
| `KotlinPsiUpdateModCommandAction.ElementBased<E, C>` | The fix needs data from the analysis. The factory computes the context `C`. |
| `PsiBasedModCommandAction<E>` | The fix returns a custom `ModCommand`, for example `ModCommand.chooseAction(...)`, or changes several elements in one `ModCommand.psiUpdate`. |

Rules for the action:
- The action runs on a non-physical copy of the file, in a background read action.
  Do not call `analyze` in `invoke`.
- Keep the context free of the session. Do not store `KaSymbol`, `KaType`, or `KaSession`.
  Store strings, names, `FqName`, `ClassId`, `SmartPsiElementPointer`, or `KaSymbolPointer`.
- Change only `element` and elements from `updater.getWritable(...)`.
- Insert fully qualified text, then shorten references. Do not add imports by hand.
- Keep comments. Use `CommentSaver` around a `replace()`.
- Generate names with `Name.render()` or `quoteIfNeeded()`, not `asString()`. This applies to the code and to the action text.
- Apply a change to an `expect` declaration or an `actual` declaration on all the counterparts.
  See `AddModifierFixMpp` for an example.
- Guard a side effect that must not run in the preview with `IntentionPreviewUtils.isIntentionPreviewActive()`.

#### Text, priority, and Fix All

- `getFamilyName()` must be specific. The Fix All action shows it: "Apply all '<family name>' fixes in file".
  Do not use a generic text such as "Remove element".
- Override `getActionPresentation(context, element)` for an element-specific text or a priority.
  Use `Presentation.of(text).withPriority(PriorityAction.Priority.HIGH)`.
  `getPresentation` is final. `HighPriorityAction` and `LowPriorityAction` throw an exception on these classes.
- `ElementContextless` and `ElementBased` add the Fix All option by default.
  Fix All groups quick fixes by their action class, not by their diagnostic.
  It visits each highlight and applies only the first matching quick fix for that highlight.
  Other matching fixes on the same highlight are skipped. Add a Fix All test when one highlight offers multiple fixes of the same action class.
  Disable Fix All if applying only one matching fix makes the result incomplete or unsafe.
  If different diagnostics use the same action class, include them in the same Fix All test.
- Return `false` from `addFixAllOption(context, element)` if a batch run is not safe.
  For an `IntentionAction`, return `false` from `availableInBatchMode()`.
  Do this for a fix that changes the build, a dependency, or a project setting, and for a fix that asks the user.

#### Language features and opt-in

- Gate a fix that produces new syntax with `languageVersionSettings.supportsFeature(LanguageFeature.X)`.
- To offer "Increase language version" or "Enable feature" for a new language feature, add one entry to
  `LanguageFeatureSupportWhiteList` in `UnsupportedFeatureFixFactory`.
  Add a test file `idea/tests/testData/quickfix/unsupportedFeature/<Feature>_<version>_Increase_Version.kt`.
- Each opt-in diagnostic has a warning and an error variant, for example `OptInUsage` and `OptInUsageError`. Register a factory for each one.

### Register the factory

Add a line to the thematic block of `KotlinK2QuickFixRegistrar` that fits, for example `typeMismatch`, `visibility`, or `other`:

```kotlin
registerFactory(RemoveUnnamedPropertyFixFactory.unnamedPropertyWithImplicitIgnorableTypeFixFactory)
```

- `registerFactory` takes the diagnostic class from the type of the factory.
- If you add a new block, add it to `createCombined(...)` in `list`. If you do not, the registrar ignores the block without an error.
- If the compiler reports the same problem with several diagnostic classes, register the factory for each class.
- Register each fix in one place only. Two registrations show the fix twice.
- Put a message in `KotlinBundle.properties`. Use a specific key, for example `fix.<name>.text`.

### Tests

Plan the cases before you write code. Start from the compiler tests for the diagnostic.
See [Test data coverage](#test-data-coverage).

Put the test data in `idea/tests/testData/quickfix/<group>/`. Use an existing group if one fits.
- Add `<case>.kt`. Put `<caret>` on the element that has the diagnostic.
- Put the action hint on the first line: `// "<action text>" "true"`.
  Use `"false"` for a negative test. The test then checks that the action is not available.
- Add `<case>.kt.after` for each `"true"` case. Copy the `.kt` file and change the code to the expected result.
  The `.after` file keeps all the directives.
- Add a `"false"` test for each case where the factory returns no fix.

A test with `"true"` also checks these points:
- The action is not available at the caret after it runs.
- The errors before and after the fix match `K2_ERROR` and `K2_AFTER_ERROR`.
- The FUS event matches `FUS_QUICKFIX_NAME`.

Directives for a quick fix test:

| Directive | Meaning |
|---|---|
| `// "<text>" "true\|false"` | The action that must be available or not available. Use `/regex/` for a text pattern. |
| `// FUS_QUICKFIX_NAME: <class>` | The class in the FUS event, for example `...RemoveUnnamedPropertyFixFactory$RemoveUnnamedPropertyFix`. Required for `"true"`. Not allowed for `"false"`. |
| `// K2_ERROR: <FACTORY_NAME>` | An expected compiler error before the fix, for example `UNRESOLVED_REFERENCE`. Use one line for each error. |
| `// K2_AFTER_ERROR: <FACTORY_NAME>` | An expected compiler error after the fix. |
| `// DISABLE_K2_ERRORS` | Turns off the error checks. Use it only with a reason. |
| `// ENABLE_WARNINGS`, `// WARNING:`, `// AFTER_WARNING:` | Checks warnings before and after the fix. |
| `// ACTION: <text>`, `// K2_ACTIONS_LIST: <text>` | The full list of actions at the caret. `HighLevelQuickFixTestGenerated` checks the list only with `// KEEP_ACTIONS_LIST_ORDER`, which also checks the order. |
| `// PRIORITY: HIGH` | The expected priority, if it is not `NORMAL`. |
| `// TEST_PREVIEW: <text>` | The expected intention preview text, if the preview differs from the result. |
| `// APPLY_QUICKFIX: false` | The action must be available, but the test does not run it. |
| `// SHOULD_FAIL_WITH: <message>` | The expected error message of a fix that refuses to run. |
| `// CHOSEN_OPTION: <regex>` | The option to select in a chooser. |
| `// SHOULD_BE_AVAILABLE_AFTER_EXECUTION` | The action can stay available after it runs. |
| `// LANGUAGE_VERSION: <v>`, `// COMPILER_ARGUMENTS: <args>` | The compiler settings. Pin the language version if the result depends on it. |

K1 is sunset. Do not add `// IGNORE_K2` or another K1 directive. Make the test pass instead.

To test Fix All, add a separate file with the platform text as the hint, for example
`// "Apply all 'Change to star projection' fixes in file" "true"`.
Put two or more diagnostics in the file. See `quickfix/addStarProjections/checkType/changeToStarProjectionSingleParameterFixAll.kt`.

If the fix changes another file, use a multi-file test:
`<case>.before.Main.kt`, `<case>.before.Dependency.kt` (or `.java`), and `<case>.after.kt`.
Add `<case>.after.Dependency.kt` for each other file that changes.

If the quick fix belongs to an inspection, put a `.inspection` file with the inspection class name in the test data directory.

#### Generate the test data

The test helps you fill in the directives. Run the test, then apply the diff of the comparison failure in the IDE.
- If `FUS_QUICKFIX_NAME` is missing or wrong, the failure shows the file with the correct line.
- If `K2_ERROR` or `K2_AFTER_ERROR` is wrong, the failure shows the file with the actual errors.
- If the `ACTION` list is wrong, the failure shows the file with the actual list.
- If the action text is wrong, the failure lists all the available actions as `// "<text>" "true"` lines.
- A multi-file test writes a missing `.after` file and fails once. Review the written file.

A single-file test does not write a missing `.kt.after` file. Create it yourself.
Then review each generated line. The test accepts any current behavior, also a wrong one.

#### Generate and run the tests

A new file in an existing group needs no change in the generator.
For a new group, add `model("$idea/quickfix/<group>", ...)` to each applicable `testClass` block in
`util/test-generator-fir/test/org/jetbrains/kotlin/fir/testGenerator/codeinsight/GenerateK2QuickFixTests.kt`.

- `AbstractHighLevelQuickFixTest` generates single-file tests in `HighLevelQuickFixTestGenerated`.
- `AbstractHighLevelQuickFixMultiFileTest` generates multi-file tests in `HighLevelQuickFixMultiFileTestGenerated`.

Run the "Generate Kotlin Tests" run configuration, and commit each changed generated class.

Run the tests from the `community` directory:

```shell
./tests.cmd --module intellij.kotlin.codeInsight.fixes.tests \
  --test 'org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixTestGenerated$Nullables'
```

### Quality checklist

These defect categories occur again and again in fixes to existing quick fixes.
For each category that applies, add a negative test, a fix, or both.

| Category | What to check |
|---|---|
| Fix does not help | The fix must remove the diagnostic and add no new error. Check error types, existing modifiers, enum entries, generated members such as `copy`, `Unit` and `Unit?`, and smart casts. Use `KaSubtypingErrorTypePolicy.STRICT` for subtype checks. |
| PSI forms | Test the plain, qualified, safe call, infix, nested, type reference, `when` subject, destructuring, constructor, and local forms. |
| Diagnostic changes | The compiler can move or split a diagnostic. Register every diagnostic class, and pin `LANGUAGE_VERSION` in tests that depend on it. |
| Action text | The text names the real change. It is specific enough for the Fix All label. Names with backticks render correctly. |
| Priority and duplicates | Set the priority through `Presentation`. Do not offer the same fix from two factories. Remove duplicate targets. |
| Fix All | Running the fix on all diagnostics in a file gives correct code. No duplicate declarations or parameters. |
| Session leaks | The context holds no `KaType` or `KaSymbol`. Analyze only elements of the file of the current session. |
| Other files | Check `canRefactorElement()` before you edit another declaration. Handle a Java target explicitly. Add a multi-file test. |
| Multiplatform | Change the `expect` declaration and all the `actual` declarations. Check that types are visible from the common module. |
| Broken code | Use `typeIfSafeToResolve`. Rethrow control-flow exceptions with `rethrowControlFlowException`. |
| Performance | Exit early on an error type before you search an index. Call `ProgressManager.checkCanceled()` in loops. |
| K1 port | Compare with all the diagnostics that the K1 fix was registered for. Make each test with `IGNORE_K2` pass, and remove the directive. |

### After the change

- Run `lint_files` on the changed files, if the IDE tools are available.
- Use the `commits` skill for the commit message. Put the `KTIJ-NNNNN` issue and the `[kotlin]` tag in the subject.

### Testing actions

These rules apply to intentions and to quick fixes.
- If the action priority is not `NORMAL`, add the `// PRIORITY` directive to the test data, for example `// PRIORITY: HIGH`.
- If the action calls `updater.moveCaretTo()` or `editor.moveCaret()`, put `<caret>` in the `.after` file.

## Test data coverage

These rules apply to inspections, quick fixes, and intentions.
Most defects in these actions come from a code form that no test covers.
Plan the test cases before you write the action. Then write the action until all the cases pass.

### Plan the cases

Write a case list before the first line of code. Use these sources in this order:
1. The quality checklist of the action kind. Add one case for each category that applies.
2. The compiler test data for the same diagnostic or construct. See [Reuse the compiler test data](#reuse-the-compiler-test-data).
3. The YouTrack issues for the action. Search KTIJ for the action name. Add one regression case for each fixed issue.
4. The old test data of the same action. A case with `// IGNORE_K2` is a K2 gap.
   Make it pass, and remove the directive.
5. The test data of a similar action, for example the inverse intention.

Each case is one of these kinds:

| Kind | File | Meaning |
|---|---|---|
| Positive | `<case>.kt`, and `<case>.kt.after` when the action changes code | The action is available, and the result is correct code. An inspection without a quick fix needs only `<case>.kt`. |
| Negative | `<case>.kt` only | The action is not available. Use `PROBLEM: none`, `"false"`, or `IS_APPLICABLE: false`. |
| Broken code | `<case>.kt` | The code has a syntax or resolve error. The action does not throw. |
| Feature off | `<case>.kt` | A language feature or a compiler flag is off. The action is not available. |

A good case list has more negative cases than positive cases.
Each negative case states one reason in its name, for example `safeCallOnNullableNoWarn.kt`.

### Reuse the compiler test data

The Kotlin compiler has tests for every diagnostic in [JetBrains/kotlin](https://github.com/JetBrains/kotlin).
These tests show the code forms where the compiler reports the diagnostic, and the forms where it does not.
Use them for a quick fix, for a diagnostic-based inspection, and for an inspection or an intention that is close to a diagnostic.
Both projects use the Apache 2.0 license.

#### Get the test data

Use a local checkout of JetBrains/kotlin next to this repository, at `../kotlin` from the repository root.
Run `git -C ../kotlin pull` first if the checkout is older than the compiler version of the plugin.

If there is no local checkout, make a sparse clone outside this repository. It takes about 15 seconds and 180 MB:

```shell
git clone -q --depth 1 --filter=blob:none --sparse https://github.com/JetBrains/kotlin.git /tmp/kotlin-compiler-testdata
git -C /tmp/kotlin-compiler-testdata sparse-checkout set --no-cone \
  'compiler/testData/diagnostics/' 'compiler/fir/analysis-tests/legacy-fir-tests/testData/' 'analysis/analysis-api/testData/'
```

To read one known file, use `https://raw.githubusercontent.com/JetBrains/kotlin/master/<path>`.

Find the files that report a diagnostic. Run the command from the `community` directory:

```shell
./tools/rg.cmd -l '<!UNNECESSARY_SAFE_CALL[!:(,]' \
  ../../kotlin/compiler/testData/diagnostics ../../kotlin/compiler/fir/analysis-tests/legacy-fir-tests/testData
```

The directory of a checker also holds tests without the diagnostic, for example `compiler/testData/diagnostics/tests/crv/`.
Read these files too. They are the negative cases.

#### Read the compiler format

| Compiler test data | Meaning |
|---|---|
| `<!NAME!>code<!>` | The compiler reports `NAME` on `code`. |
| `<!NAME("arg")!>code<!>` | The same, with the diagnostic arguments. |
| `<!NAME1, NAME2!>code<!>` | Two diagnostics on one range. |
| Code without markup | The compiler reports nothing. This is a negative case. |
| `// LANGUAGE: +Feature` or `-Feature` | A language feature on or off. |
| `// FILE: A.java` | The start of one more file in a multi-file test. |
| `// WITH_STDLIB` | The standard library is on the classpath. |
| `// RUN_PIPELINE_TILL`, `// ISSUE`, `// FIR_IDENTICAL` | Compiler test settings. |
| `/* GENERATED_FIR_TAGS: ... */` | The construct kinds in the file, for example `safeCall`, `callableReference`, `whenWithSubject`. |
| `<case>.fir.kt` | The K2 result, when it differs from `<case>.kt`. |
| `<case>.ll.kt`, `<case>.latestLV.kt` | The result in the Analysis API or with the latest language version. |

Use the `.fir.kt` file when it exists. The IDE uses K2.

Use `GENERATED_FIR_TAGS` as a coverage checklist.
Collect the tags of all the files for the diagnostic.
Add an IDE case for each tag that changes the PSI that the action edits.

#### Convert a case

Do not copy a compiler file as it is. A compiler file has many diagnostics, and an IDE test checks one caret.
For each case:
1. Take the smallest part of the code that keeps the case. Keep the declarations that it needs.
2. Remove all the `<!...!>` and `<!>` markup.
3. Put `<caret>` inside the range of the diagnostic.
4. Convert the directives with the table below.
5. Name the file for the case, not for the compiler file. Write the compiler path in a comment only if it helps the reader.
6. Run the test, and write the `.after` file. Check that the result compiles.

| Compiler | IDE |
|---|---|
| `// LANGUAGE: +Feature` | `// COMPILER_ARGUMENTS: -XXLanguage:+Feature` |
| `// WITH_STDLIB` | `// WITH_STDLIB` |
| `// FILE: A.java` and `// FILE: B.kt` | A multi-file test, or a Java file next to the test |
| Other errors in the code | `// K2_ERROR:` lines, or remove the code |
| `// RUN_PIPELINE_TILL`, `// ISSUE`, `GENERATED_FIR_TAGS` | Remove |

A compiler case can be wrong for the IDE action. The diagnostic can be correct, but the fix can make it worse.
Then make it a negative case, or leave it out and write the reason in the commit message.

### Review the test data

The test accepts the current behavior, also a wrong one.
Read each `.after` file as a reviewer.
- The result compiles, or the `K2_AFTER_ERROR` lines name each error.
- The result keeps all the comments and the formatting outside the changed range.
- The result has the same behavior as the original code.
- No negative case passes for a reason other than the reason in its name.
  For example, a typo in the code can make the action unavailable.

### Report the coverage

In the commit message, list the checklist categories without a test, and give the reason for each.
Do not add a test only to make the list complete.

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