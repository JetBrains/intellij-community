# Contribution guide

Thank you for your interest in contributing to Kotlin!

This guide explains the contribution process established in the Kotlin IntelliJ IDEA plugin, as well as defines requirements for the
changes.
Please read the document carefully before submitting requests.

Note: if you want to contribute to the Kotlin compiler and runtime libraries, please
see [JetBrains/kotlin](https://github.com/JetBrains/kotlin).

## Contents

1. [How to contribute](#1-how-to-contribute)
2. [What to contribute](#2-what-to-contribute)
3. [Pull request requirements](#3-pull-request-requirements)
4. [Writing intentions/inspections](#4-writing-intentionsinspections)

## 1. How to contribute

You can contribute to the plugin by sending us a Pull Request to the `master` branch.

Because of technical reasons, we can't use the GitHub UI for accepting PRs. Instead, we pick commits manually. Don't worry:
we won't change the commits (except maybe trivial changes such as typos) or its author information.

You will likely need to set up a project locally to modify its code.
The detailed setup instructions are available in the [README](README.md).

To learn how to write plugins for IntelliJ IDEA, please refer to the 
[IntelliJ Platform SDK DevGuide](https://plugins.jetbrains.com/docs/intellij/).
The portal contains a lot of useful information and explains how the IDE works under the hood.

There's also a [#kontributors](https://kotlinlang.slack.com/archives/C0BUHC9HD) channel in the [public Slack](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up). 
Please feel free to ask questions regarding contributions to the Kotlin IntelliJ IDEA plugin.

## 2. What to contribute

An excellent way to contribute would be to fix
some [issues marked with the "up-for-grabs" tag](https://youtrack.jetbrains.com/issues/KTIJ?q=tag:%20%7BUp%20For%20Grabs%7D%20and%20(State:%20Open%20or%20State:%20Backlog)%20and%20(Subsystems:%20IDE*)).
They usually don't take a lot of time to be fixed, and the fix is often local.

If you plan to work on new functionality (new intentions, inspections and so on) or want to fix something by re-writing a significant
part of a subsystem, please contact us beforehand via the [#kontributors](https://kotlinlang.slack.com/archives/C0BUHC9HD) channel on Slack.
Our experience shows that features that seem
trivial often require thorough design work, or implementing them might dramatically affect the performance of the whole IDE.
So we might not be able to accept the Pull Request, even if it brings significant value to the users.

Surely, this doesn't mean we say no to arbitrary changes. However, the review process might be slightly longer than usual. So sticking
to the "up-for-grabs" tag is a safe way to go.

## 3. Pull Request requirements

We have several requirements for the Pull Requests:

1. A Pull Request must solve some issue. So the issue identifier must be specified.  
   We use [KTIJ](https://youtrack.jetbrains.com/issues/KTIJ) project on YouTrack.
   If the issue doesn't exist yet, please [create it](https://youtrack.jetbrains.com/newIssue?project=KTIJ).
   <br/> We also kindly ask you to follow "Commits Requirements" described in the [README](README.md).
2. Do not submit Pull Requests that solve multiple issues that aren't interconnected. Create several PRs instead.
3. The plugin must still work after the change. Please ensure the plugin compiles and runs successfully after the change.
4. Each non-trivial change in plugin functionality must come with tests, so add new tests or adapt existing ones. 
   In both cases, please ensure the existing tests don't fail with the PR applied.
5. Avoid merge commits. If you want to adapt your PR after changes in `master`, use `git pull --rebase` instead.

## 4. Writing intentions/inspections

### 4.1. IntelliJ IDEA

You can find necessary information about how to write inspections, intentions and quick fixes 
in the [Code Inspections](https://plugins.jetbrains.com/docs/intellij/code-inspections.html) tutorial.

It's essential to know 
about [PSI](https://plugins.jetbrains.com/docs/intellij/psi.html), the source code model used in IntelliJ IDEA.
To inspect PSI, you can use either the built-in [Psi Viewer](https://www.jetbrains.com/help/idea/psi-viewer.html) available 
in the "internal" mode, or an external plugin called [PsiViewer](https://plugins.jetbrains.com/plugin/227-psiviewer).
For debugging purposes consider using `PsiTreeUtilsKt.printTree` function.

### 4.2. Kotlin-specific information

#### General information

The full guideline for inspections is in [Code insight: Inspections](docs/fir-ide/architecture/code-insights.md#inspections).
Read it before you write an inspection. The main rules are:

1. Put a new inspection in [`code-insight/inspections-k2`](code-insight/inspections-k2).
   Use `KotlinApplicableInspectionBase.Simple` for exactly one quick fix.
   Use `KotlinApplicableInspectionBase` for no quick fix or multiple quick fixes.
   Extend `KotlinModCommandQuickFix` for each quick fix.
2. Register inspections in [`intellij.kotlin.codeInsight.inspections.xml`](code-insight/inspections-k2/resources/intellij.kotlin.codeInsight.inspections.xml).
   Register intentions in [`intellij.kotlin.codeInsight.intentions.xml`](code-insight/intentions-k2/resources/intellij.kotlin.codeInsight.intentions.xml).
3. Write a description in English.
   Put inspection descriptions in [`code-insight/descriptions/resources-en/inspectionDescriptions`](code-insight/descriptions/resources-en/inspectionDescriptions).
   Put intention descriptions in [`code-insight/descriptions/resources-en/intentionDescriptions`](code-insight/descriptions/resources-en/intentionDescriptions).
4. Add automatic tests for all intentions, inspections, and quick fixes.
   Put test data for a new local inspection in [`code-insight/inspections-k2/tests/testData/inspectionsLocal`](code-insight/inspections-k2/tests/testData/inspectionsLocal).
   Add negative tests for the cases in the quality checklist.
5. Prefer an inspection with `ProblemHighlightType.INFORMATION` and a quick fix to an intention.
6. In other cases, use only `ProblemHighlightType.GENERIC_ERROR_OR_WARNING`. The user can then change the inspection level.
7. Start a new inspection with a conservative level: disabled by default, `WEAK WARNING`, or `INFORMATION`.
8. Make the highlighting range as narrow as possible.
   For example, highlight the class name, not the whole class declaration. Highlight the function name, not the whole call.
9. Do not hard-code strings that the user sees. Put them in [`KotlinBundle.properties`](base/resources/resources-en/messages/KotlinBundle.properties).
10. Run the inspection on a large project. Open the project in a development IDE and use the "Run Inspection by Name" action.
    Use this checklist:
    - [ ] The execution time is not worse.
    - [ ] The memory consumption is reasonable.
    - [ ] There are no false positives, and there are few false negatives.
    - [ ] The quick fix gives correct code.

#### Reference resolution

1. Resolution in the Analysis API (`analyze { }`, `resolveToCall()`, `resolveToSymbol()`, `expressionType`) is expensive.
   Do the PSI-only checks first, in `isApplicableByPsi`.
   For example, if the inspection looks for top-level classes with the `@Foo` annotation, check that the class is top-level before you resolve `Foo`.
2. Do all the resolution for one element in one `prepareContext` call.
3. A resolve call can return `null` or an ambiguous result. Handle these cases, and do not use `!!` on a resolve result.
4. Do not store `KaSymbol`, `KaType`, or `KaSession` outside of the `analyze` block.
   Use `KaSymbolPointer` or `SmartPsiElementPointer` instead.
5. Do not call `analyze` in a quick fix. Compute the data in `prepareContext`.
   The code can change after the applicability check, so check the PSI preconditions again in the fix.
6. Use the `languageVersionSettings` extension property to get `LanguageVersionSettings` for a `PsiElement`.

#### Performance and stability tips

1. Never do anything continuous in the UI thread. Never call `resolve()` from it.
2. Always start potentially long operations (such as "Usages search") under a `ProgressIndicator` so the user can cancel it if needed.
3. Access PSI, module and project components only from a read/write action.
4. Use `SmartPsiElementPointer` to store references to `PsiElement`s across multiple read/write actions. IntelliJ IDEA might dispose or
   replace a `PsiElement` between the actions.
5. Do not store any references to PSI or a project structure statically or in long-living project components. It will surely lead
   to memory leaks. If absolutely necessary, use `SmartPsiElementPointer` (and corresponding `createSmartPointer()`).