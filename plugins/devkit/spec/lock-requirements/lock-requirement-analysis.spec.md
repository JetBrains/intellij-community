---
name: Lock Requirement Analysis
description: Stable behavior for the DevKit analysis that finds the lock and thread requirements of a method and its callees.
style: plain-1
targets:
  - ../../devkit-core/src/threadingModelHelper/JavaLockReqModel.kt
  - ../../devkit-core/src/threadingModelHelper/LockReqAnalysisStatistics.kt
  - ../../devkit-core/src/threadingModelHelper/LockReqAnalyzerParallelBFS.kt
  - ../../devkit-core/src/threadingModelHelper/LockReqConsumer.kt
  - ../../devkit-core/src/threadingModelHelper/LockReqDetector.kt
  - ../../devkit-core/src/threadingModelHelper/LockReqPhaseTimer.kt
  - ../../devkit-core/src/threadingModelHelper/LockReqPsiOps.kt
  - ../../devkit-core/src/threadingModelHelper/LockReqRules.kt
  - ../../devkit-core/src/threadingModelHelper/LockReqSearchCache.kt
  - ../../devkit-core/src/threadingModelHelper/java/JavaLockReqPsiOps.kt
  - ../../devkit-core/resources/messages/DevKitBundle.properties
  - ../../intellij.kotlin.devkit/src/threadingModelHelper/KtLockReqPsiOps.kt
  - ../../intellij.devkit.threading/src/threadingModelHelper/BaseReqSearchAction.kt
  - ../../intellij.devkit.threading/src/threadingModelHelper/LockReqAction.kt
  - ../../intellij.devkit.threading/src/threadingModelHelper/LockReqService.kt
  - ../../intellij.devkit.threading/src/threadingModelHelper/LockReqsToolWindowFactory.kt
  - ../../intellij.devkit.threading/src/threadingModelHelper/ThreadingReqAction.kt
  - ../../intellij.devkit.threading/src/threadingModelHelper/ui/LockReqsHeader.kt
  - ../../intellij.devkit.threading/src/threadingModelHelper/ui/LockReqsState.kt
  - ../../intellij.devkit.threading/src/threadingModelHelper/ui/LockReqsToolWindowPanel.kt
  - ../../intellij.devkit.threading/src/threadingModelHelper/ui/LockRequirementPresentation.kt
  - ../../intellij.devkit.threading/src/threadingModelHelper/ui/PathDetailsPanel.kt
  - ../../intellij.devkit.threading/src/threadingModelHelper/ui/PathListRenderer.kt
  - ../../intellij.devkit.threading/resources/intellij.devkit.threading.xml
  - ../../intellij.devkit.threading/resources/messages/DevkitThreadingBundle.properties
  - ../../intellij.devkit.mcp/src/DevKitMcpToolset.kt
  - ../../devkit-java-tests/testSrc/org/jetbrains/idea/devkit/threadingModelHelper/JavaLockReqUnitTest.kt
  - ../../devkit-java-tests/testData/threadingModelHelper/*.java
  - ../../devkit-kotlin-fir-tests/testSrc/org/jetbrains/idea/devkit/k2/threadingModelHelper/KtLockReqUnitTest.kt
  - ../../devkit-kotlin-fir-tests/testData/threadingModelHelper/*.kt
---

# Lock Requirement Analysis

Status: Active
Date: 2026-10-20

## Purpose

The lock requirement analysis is a DevKit tool for IntelliJ Platform developers.
It starts at one Java or Kotlin method and follows the calls to a limited depth.
It reports each call path that ends at a lock or thread requirement.

The analysis is a heuristic. A reported path can be unreachable, and the analysis can miss a path.

The work belongs to [IJPL-179707](https://youtrack.jetbrains.com/issue/IJPL-179707), "Implement static analysis for real locking requirements".

## Scope

This specification covers the analysis engine, its rules, its statistics report, the tool window, and the MCP tools.

### Goals

- Show the call paths from a method to its read, write, write-intent, and no-read lock requirements.
- Show the call paths from a method to its EDT and background thread requirements.
- Give the same result for the same input, although the traversal runs in parallel.
- Show each path as a stack trace that points to the call sites.
- Give data about where the analysis time goes.

### Non-goals

- This specification does not define the build-time assertion generation for the threading annotations.
- The analysis does not prove that a path is reachable at runtime.
- The analysis does not change the analyzed code.
- The analysis does not cover languages other than Java and Kotlin.

## Terms

- A *requirement* is a lock or thread constraint, with a *constraint type* and a *reason*.
- The constraint types are `READ`, `WRITE`, `WRITE_INTENT`, `NO_READ`, `EDT`, and `BGT`.
- The *lock types* are `READ`, `WRITE`, `WRITE_INTENT`, and `NO_READ`.
- The *thread types* are `EDT` and `BGT`.
- The reasons are `ANNOTATION`, `ASSERTION`, `SWING_COMPONENT`, `MESSAGE_BUS`, and `IMPLICIT`.
- A *path* is a method chain from the root method, and one requirement at its end.
- A *frame* is one method of a method chain.
- An *opaque* method is a method that the analysis does not go into.

## Requirement Detection

- An analysis must report only the constraint types of its requested set.
- A traversed method with a known lock annotation must give an `ANNOTATION` requirement.
- The path of an annotation requirement must end at the annotated method.
- The known annotations are `RequiresReadLock`, `RequiresWriteLock`, `RequiresEdt`, `RequiresBackgroundThread`, and `RequiresReadLockAbsence`.
  [@test] ../../devkit-java-tests/testSrc/org/jetbrains/idea/devkit/threadingModelHelper/JavaLockReqUnitTest.kt (
    `testAnnotationInChain`;
    `testBothAnnotationAndAssertion`
  )

- A call to a known assertion method must give an `ASSERTION` requirement.
- The path of an assertion requirement must end at the method that contains the call.
- An assertion in a nested block must give a requirement.
- Repeated calls to one assertion method in one body must give one path.
  [@test] ../../devkit-java-tests/testSrc/org/jetbrains/idea/devkit/threadingModelHelper/JavaLockReqUnitTest.kt (
    `testAssertionInNestedBlock`;
    `testMultipleAssertionsInMethod`;
    `testBothAnnotationAndAssertion`
  )

- The known assertion methods are in `ThreadingAssertions` and `Application`.
- Some PSI and VFS methods count as assertion methods too, for example `SmartPsiElementPointer.getElement` and `JavaPsiFacade.findClass`.

Untested: No focused test covers the `Application` assertions or the PSI and VFS assertion methods.

- A call to a method that a read-required class declares must give a `READ` requirement with the `IMPLICIT` reason.
- The check must use the class that declares the called method.
- An override in an implementation class must not match the rule of its super class.
- The read-required classes are the PSI and AST classes in `LockReqRules.readRequiredClasses`.
  [@test] ../../devkit-java-tests/testSrc/org/jetbrains/idea/devkit/threadingModelHelper/JavaLockReqUnitTest.kt (
    `testPsiElementMethodCall`;
    `testPsiMethodAndAstNodeCall`;
    `testPsiElementBaseCall`;
    `testPsiSupportClassesCall`
  )

- A call to a Swing or AWT method must give an `EDT` requirement with the `SWING_COMPONENT` reason.
- A class in `javax.swing` or `java.awt` is a Swing class.
- A subclass of a listed Swing component class is a Swing class too.

Untested: The `EDTSwingComponent.java` test data has no test.

- The analysis must not go into a callee that gives a requirement.
- The caller of such a callee must still follow its other callees.
- A method without requirements must give no path.
  [@test] ../../devkit-java-tests/testSrc/org/jetbrains/idea/devkit/threadingModelHelper/JavaLockReqUnitTest.kt (
    `testNoLockRequirements`;
    `testDifferentClassesMethods`
  )

## Traversal

- The analysis must follow method calls, method references, and constructor calls.
- A call in a lambda body must count as a call of the method that contains the lambda.
- The analysis must follow calls to methods of other classes.
  [@test] ../../devkit-java-tests/testSrc/org/jetbrains/idea/devkit/threadingModelHelper/JavaLockReqUnitTest.kt (
    `testLambdaWithMethodReference`;
    `testDifferentClassesMethods`
  )

- A recursive call cycle must not make the analysis run without end.
- Each method must keep the shortest chain that the analysis found for it.
- The result must not depend on the order in which parallel workers process the methods.
  [@test] ../../devkit-java-tests/testSrc/org/jetbrains/idea/devkit/threadingModelHelper/JavaLockReqUnitTest.kt (
    `testCyclicRecursiveCalls`
  )

- The analysis must not process a method whose chain reaches the depth limit.
- The default depth limit is 5.

Untested: No focused test covers the depth limit.

- For a polymorphic call, the analysis must follow the overrides in the project scope.
- An override must replace the called method in the chain.
- The analysis must follow the overrides in a stable order.
  [@test] ../../devkit-java-tests/testSrc/org/jetbrains/idea/devkit/threadingModelHelper/JavaLockReqUnitTest.kt (
    `testSubtypingPolymorphism`
  )

- If a method has too many overrides for the implementation limit, the analysis must follow no override.
- The default implementation limit is 5.
- In Java, the analysis must not search for the overrides of a functional interface method.
- The analysis must not search for the overrides of common methods such as `equals`, `hashCode`, and `toString`.

Untested: No focused test covers the implementation limit, the functional interface rule, or the common methods.

- A `Disposer.dispose` call must count as a call of the `dispose` method of its argument.
  [@test] ../../devkit-java-tests/testSrc/org/jetbrains/idea/devkit/threadingModelHelper/JavaLockReqUnitTest.kt (
    `testDisposableCall`
  )

- A `MessageBus.syncPublisher` call must record its topic class.
- For a topic class, the analysis must follow all methods of its listener classes.
- The analysis must follow the listener classes in a stable order.

Untested: The `MessageBusDetection.java` test data has no test. No analysis produces the `MESSAGE_BUS` reason.

## Opaque Methods

- The analysis must check an opaque method for requirements.
- The analysis must not search for the overrides of an opaque method.
- The analysis must not go into the body of an opaque method.
- A method that a JDK class declares must be opaque.
- The JDK check must use the configured JDK of the project, not a package name.
- A method of `UserDataHolder`, `UserDataHolderEx`, `UserDataHolderBase`, or `Logger` must be opaque.
- The `Disposable.dispose` method and all its overrides must be opaque.
  [@test] ../../devkit-java-tests/testSrc/org/jetbrains/idea/devkit/threadingModelHelper/JavaLockReqUnitTest.kt (
    `testJdkMethodCall`;
    `testUserDataHolderCall`;
    `testLoggerCall`;
    `testDisposableCall`
  )

- A call to a known async dispatch method must stop the path at that call.
- The known async dispatch methods are the `Application` and `UIUtil` methods that run code later or on another thread.

Untested: No focused test covers the async dispatch methods.

- The analysis must not enqueue a method of a class in `java`, `com.sun`, `com.intellij.util`, or `org.jdom`.
- The analysis must not enqueue a method from `LockReqRules.indifferent` when the requested set is in its scope.

Untested: No focused test covers the skipped packages or the indifferent methods.

## Call Sites

- Each frame of a path must point to the call of the next frame.
- The last frame must point to the access that causes the requirement.
- An annotated method at the end of a path must point to its declaration.
- Each location must use the stack trace form `File.java:line` with a 1-based line.
  [@test] ../../devkit-java-tests/testSrc/org/jetbrains/idea/devkit/threadingModelHelper/JavaLockReqUnitTest.kt (
    `testCallSiteLocations`
  )

- If a method calls one callee more than once, the frame must point to the first call.

Untested: No focused test covers a callee with more than one call site.

## Kotlin Support

- The Kotlin analysis must give the same kinds of paths as the Java analysis.
- It must follow top-level functions, extension functions, suspend functions, and property accessors.
- It must follow companion object functions and functions with default arguments.
  [@test] ../../devkit-kotlin-fir-tests/testSrc/org/jetbrains/idea/devkit/k2/threadingModelHelper/KtLockReqUnitTest.kt (
    `testNoLockRequirementsKt`;
    `testAssertionInNestedBlockKt`;
    `testAnnotationInChainKt`;
    `testLambdaWithMethodReferenceKt`;
    `testTopLevelAndExtensionKt`;
    `testBothAnnotationAndAssertionKt`;
    `testCyclicRecursiveCallsKt`;
    `testDifferentClassesMethodsKt`;
    `testMultipleAssertionsInMethodKt`;
    `testSubtypingPolymorphismKt`;
    `testSuspendAndPropertyKt`;
    `testCompanionDefaultArgsKt`
  )

- In a qualified call, the frame must point to the name of the called function.

Untested: No Kotlin test covers the opaque methods, the implicit read rule, or the call site lines.

## Result Streaming

- The analysis must give each path to the consumer when it finds the path.
- The analysis must give the final result to the consumer when it completes.
- The final result must contain all reported paths and topic classes.
- The analysis must stay cancellable during a wait for a read action or a search.
- A pending write action must be able to cancel the read action of the analysis.

Untested: No focused test covers streaming order or cancellation.

## Search Cache

- One analysis must run each inheritor search and each listener search only once.
- A second request for the same key must get the cached results, also through another call chain.
- If another thread runs the same search, the request must wait for its result.
- A failed or cancelled search must make the next request search again.
- An invalid cached element must make the next request search again.
- A method of a local or anonymous class must not use the cache.
- The cache must not change the results or their order.
  [@test] ../../devkit-java-tests/testSrc/org/jetbrains/idea/devkit/threadingModelHelper/JavaLockReqUnitTest.kt (
    `testInheritorSearchCache`
  )

Untested: No focused test covers the failure, the invalid element, or the local class cases.

## Statistics Report

- The analysis must write a statistics report when it stops.
- It must write the report also on cancellation or failure.
- The report status must be `COMPLETED`, `CANCELLED`, or `FAILED`.
- The default directory is `<log dir>/lock-req-analysis/`.
- A `null` statistics directory must turn the report off.
- A write failure must only log a warning.
- The consumer must get the report file after a successful write.
  [@test] ../../devkit-java-tests/testSrc/org/jetbrains/idea/devkit/threadingModelHelper/JavaLockReqUnitTest.kt (
    `testStatisticsReport`
  )

- The report must give the root method, the status, the wall clock time, and the configuration.
- The report must give the counts of enqueued, processed, unfinished, and skipped entries.
- The report must give a time breakdown for each traversal phase.
- The report must give the slowest classes for the inheritor search, with their slowest methods.
- The report must give the slowest topic classes for the listener search.
- The search summaries and tables must give the cache hits.
- The report must give the slowest calls, with a phase line below each call.
- The report must give the traversal tree in DFS order, with the subtree depth, size, and time.
  [@test] ../../devkit-java-tests/testSrc/org/jetbrains/idea/devkit/threadingModelHelper/JavaLockReqUnitTest.kt (
    `testStatisticsReport`;
    `testInheritorSearchCache`
  )

- The phases must be exclusive.
- A nested phase must count only for itself.
- The unmeasured part of the node time must count for the `other` phase.
  [@test] ../../devkit-java-tests/testSrc/org/jetbrains/idea/devkit/threadingModelHelper/JavaLockReqUnitTest.kt (
    `testPhaseTimerExcludesNestedBlocks`
  )

## User Experience

### Actions

- Analyze Lock Requirements must search for the lock types.
- Analyze Threading Requirements must search for the thread types.
- Each action must be visible and enabled only when the caret is in a Java or Kotlin method.
- Each action must analyze the method at the caret.
- Each action must show the Locking Requirements tool window and activate it.
- The IDE must register the tool window at the bottom when an action first needs it.
- The analysis must run with a cancellable background progress.
- The progress must show the processed and total method counts, and the current method.
- After the analysis stops, the IDE must open the statistics report in the editor.

Untested: No UI test covers the actions, the tool window, or the progress.

### Tool Window

- Before the first analysis, the tool window must show "No lock requirements analyzed yet" and a hint.
- The tool window must show the latest analysis of the project.
- A new analysis must replace the shown result.
- The tool window must update the shown paths while the analysis runs.
- The tool window must show the path list on the left and the path details on the right.

### Header and Filters

- The header must show the number of the shown paths.
- The header must show one check box for each constraint type.
- All constraint type check boxes must be selected at first.
- The list must show only paths whose constraint type is selected.
- The search field must filter the paths by the method chain text.
- The search must ignore the case and the leading and trailing spaces.
- The search text must match the qualified class and method names of the chain.

### Path List

- Each row must show the requirement icon, the bold requirement label, and the short method chain.
- The icon must show the requirement reason.
- A speculative path must show its chain in grey.
- An update of the list must keep the selected path when the path stays in the list.
- If the analysis found no paths, the list must show "No lock requirements found" and a hint.
- If the filters hide all paths, the list must show "No paths match the current filters".
- A double click on a row must open the source of the requirement.

### Path Details

- Without a selection, the details must show "Select a path to view details".
- The details must show the method chain as a stack trace, with the deepest frame first.
- Each frame must use the JVM form `at com.example.Foo.bar(Foo.java:42)`.
- A constructor frame must use the `<init>` method name.
- Each frame must be a hyperlink to its call site.
- The details must show the requirement icon and the requirement description, for example "Requires read lock (annotation)".
- A speculative path must add "speculative" to the description.
- Go to Lock Check must open the source of the requirement.
- Go to Lock Check must do nothing when the source is not valid.
- Copy Path must copy the chain in call order, one frame for each line, and the annotation name.
- The tool window disposable must own the stack trace console.

## MCP Tools

- `find_lock_requirements_usages` must analyze the method at a 1-based file position for the lock types.
- `find_threading_requirements_usages` must analyze the method at a 1-based file position for the thread types.
- Each tool must return each path as a requirement type and a list of class and method names.
- Each tool must stop at its timeout and return the paths found up to then.
- Each tool must report whether the timeout occurred.
- An unknown file, an invalid position, or a position outside a method must give an expected error.
- The MCP tools must not open the statistics report in the editor.

Untested: No focused test covers the MCP tools.

## Verification

The linked tests need no setup beyond the standard module test command.
`JavaLockReqUnitTest` gives a mock JDK only to `testJdkMethodCall`.

## Open Questions

- A call in a lambda that goes to an async dispatch method still counts as a call of the enclosing method.
- No analysis sets the speculative flag of a path, so the speculative presentation has no source.
- No analysis produces the `MESSAGE_BUS` reason or calls the Swing component callback.
- The Java test mocks `MessageBus` in a package that the message bus rule does not match.
- The clipboard text uses `RequiresNoReadAccess` and `RequiresWriteIntentLock`, which are not the known annotation names.
- The assertion labels in the list, for example `assertIsEdt()`, are not the names of the known assertion methods.
- The actions are not in an action group, so only Find Action shows them.
- The Kotlin extension functions in `LoggerKt` are not opaque.
