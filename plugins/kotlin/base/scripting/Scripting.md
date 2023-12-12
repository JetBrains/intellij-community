```
PLAN

[QUESTIONS TO BE COVERED]

1. What happens when a project [containing scripts] is opened.
2. What happens when a script is opened in the editor.
3. What is important for script resolution/highlighting, navigation and completion.
4. How script definitions are loaded, cached (ae8a4c03), reloaded.
5. Where can one see what dependencies a script have.
6. How are script dependencies indexed. Deferred indexing for sources.
7. What data and where is passed to the compiler for analysis.
8. Script settings: what is standalone script. Settings persistance.
9. K2-compiler: scripting support as a plugin. Why is it important. Plugin activation.
10. Injected scripts (markdown, embedded kotlin snippets).
11. How scripts importing (@Import) work in .main.kts.
12. Dynamic dependencies (@Import and plugins{}). Configuration refinement.
13. Scripts highlighting: scripts specific pass (compiler diagnostics)
14. How is scripting related code organized (modules, their purposes and relations)

[GRADLE SPECIFIC QUESTIONS TO BE COVERED]

1. Import. How/where Gradle scripts configuration are loaded (dependencies, implicit imports, receivers, etc.).
2. What's the difference between project reimport and partial reload.
3. How build roots data is cached (configurations) and persisted.
4. Assignment plugin. Kotlin facet, compiler arguments, etc.
5. Known Gradle side issues.
```

## GENERAL INFORMATION

### What is Kotlin Script

Kotlin script is a general term meaning `.kts` file where one can write code in Kotlin on a top level.
Differ to a regular `.kt` file, for example, to write `val a = 3 + 5` there is no need to declare a function or class. 

There are different script types usually associated with a specific file sub-extension, e.g. `.main.kts`, `.ws.kts` or `.gradle.kts`. Here
is where magic comes into play. Every script type has its specific set of implicit gems (import statements, base class,
receivers, annotations, etc.).

### Script types

What defines the script type are its DSL and a source of dependencies.

DSL assumes built-in opportunities available out of the box: import statements, functions, receivers, annotations.

Dependencies can be either taken from a host module or provided as a static list together with DSL features.
The latter are called "standalone" and reside outside project source roots.
Scripts requiring access to module sources and dependencies are "non-standalone" and therefore reside inside source roots.   

Both DSL and source of dependencies are "encapsulated" in the file extension or even its name. For example, `.kts` support pure Kotlin
(including the standard library) and are standalone. Scripts having `.ws.kts` extension are also "pure Kotlin" ones but reside in module
source roots and have access to its sources and dependencies.

### How file name defines a script type

Script files are not self-contained. There is meta-data that defines all the hidden pieces - script definition. 
In the IDE, definitions represent a chain of entities, each responsible for a specific type of script. Each time a script is analysed, the
chain is asked for the most appropriate definition. Usually, the applicability of a definition is determined by a file extension. Other
options are also possible. For example, Gradle's `init.gradle.kts`, `build.gradle.kts` and `settings.gradle.kts` have different definitions
though their extension is the same.

### Implicit scripts

Usually, scripts are associated with `.kts` files. But in general case, a script is a Kotlin code placed into 
some host. This can be `.kts` file, code-snippet-container in markdown `.md` file, calculation cell of the Kotlin Notebook or
injected language block in a regular `.java` file.

### External Resources

1. [Custom scripts tutorial](https://kotlinlang.org/docs/custom-script-deps-tutorial.html#0)

2. [Custom scripts examples](https://github.com/Kotlin/kotlin-script-examples)

3. [Running scripts in command line](https://kotlinlang.org/docs/command-line.html#run-scripts)

## TECHNICAL DETAILS

### Script definitions

Modern way to define a script (as a family) is to use so called template class annotated with `@KotliScript` annotation.


#### Compilation configuration
#### Host configuration  
#### Evaluation configuration

## Injected scripts (Kotlin Notebooks, Kotlin code snippets in tests and .md)

# Logs

To activate debug messages: `Help => Diagnostic Tools => Debug Log Settings (available as an IDEA action)`\
Add the following line into the input: `org.jetbrains.kotlin.idea.script`

### What's inside

All scripting related messages are prefixed with `[KOTLIN_SCRIPTING]`.

###### Definitions loading
```
2023-11-29 10:27:38,478 [  81788]   INFO - #o.j.k.i.script - [KOTLIN_SCRIPTING] Loading script definitions: classes = [org.gradle.kotlin.dsl.KotlinBuildScript], classpath = [/Users/klunnyi/.gradle/wrapper/dists/gradle-8.4-bin/1w5dpkrfk8irigvoxmyhowfim/gradle-8.4/lib/gradle-core-api-8.4.jar, /Users/klunnyi/.gradle/wrapper/dists/gradle-8.4-bin/1w5dpkrfk8irigvoxmyhowfim/gradle-8.4/lib/gradle-kotlin-dsl-tooling-models-8.4.jar, /Users/klunnyi/.gradle/wrapper/dists/gradle-8.4-bin/1w5dpkrfk8irigvoxmyhowfim/gradle-8.4/lib/gradle-kotlin-dsl-8.4.jar, /Users/klunnyi/.gradle/wrapper/dists/gradle-8.4-bin/1w5dpkrfk8irigvoxmyhowfim/gradle-8.4/lib/gradle-core-8.4.jar, /Users/klunnyi/.gradle/wrapper/dists/gradle-8.4-bin/1w5dpkrfk8irigvoxmyhowfim/gradle-8.4/lib/kotlin-stdlib-1.9.10.jar, /Users/klunnyi/.gradle/wrapper/dists/gradle-8.4-bin/1w5dpkrfk8irigvoxmyhowfim/gradle-8.4/lib/kotlin-compiler-embeddable-1.9.10.jar]
2023-11-29 10:27:38,492 [  81802]   INFO - #o.j.k.i.script - [KOTLIN_SCRIPTING] Loaded definitions: classes = [org.gradle.kotlin.dsl.KotlinBuildScript], definitions = [KotlinBuildScript]
...
2023-11-29 10:27:38,494 [  81804]   FINE - #o.j.k.i.script - [KOTLIN_SCRIPTING] Loaded definitions: time = 94 ms, source = org.jetbrains.kotlin.idea.gradleJava.scripting.GradleScriptDefinitionsContributor, definitions = [KotlinInitScript, KotlinSettingsScript, KotlinBuildScript]
2023-11-29 10:27:38,494 [  81804]   FINE - #o.j.k.i.script - [KOTLIN_SCRIPTING] Loaded definitions: time = 0 ms, source = org.jetbrains.kotlin.idea.core.script.ScriptDefinitionSourceFromContributor, definitions = [Kotlin Script]
2023-11-29 10:27:38,495 [  81805]   FINE - #o.j.k.i.script - [KOTLIN_SCRIPTING] Definitions loading total time: 96 ms
```

###### Gradle import (script dependencies)

Important: `KotlinDslScriptModel` (see its `file` and `classpath`) is a data structure we get from Gradle and use as an insight about script dependencies.  

```
2023-11-29 10:34:07,617 [ 470927]   FINE - #o.j.k.i.script - [KOTLIN_SCRIPTING] gradle project info after import: KotlinGradleDslSync(workingDir=/Users/klunnyi/jb/tbe-server, gradleVersion=8.4, gradleHome=/Users/klunnyi/.gradle/wrapper/dists/gradle-8.4-bin/1w5dpkrfk8irigvoxmyhowfim/gradle-8.4, javaHome=/Users/klunnyi/Library/Java/JavaVirtualMachines/jbr-17.0.6-1/Contents/Home, projectRoots=[/Users/klunnyi/jb/tbe-server, /Users/klunnyi/jb/tbe-server/tbe-backend, /Users/klunnyi/jb/tbe-server/tbe-backend-docker, /Users/klunnyi/jb/tbe-server/tbe-cli, /Users/klunnyi/jb/tbe-server/tbe-common, /Users/klunnyi/jb/tbe-server/tbe-demo-setup, /Users/klunnyi/jb/tbe-server/tbe-feed, /Users/klunnyi/jb/tbe-server/tbe-filter, /Users/klunnyi/jb/tbe-server/tbe-import-scripts, /Users/klunnyi/jb/tbe-server/tbe-launcher, /Users/klunnyi/jb/tbe-server/tbe-mock-auth, /Users/klunnyi/jb/tbe-server/tbe-test-common, /Users/klunnyi/jb/tbe-server/tbe-test-postgre, /Users/klunnyi/jb/tbe-server/tbe-ui, /Users/klunnyi/jb/tbe-server/tbe-ui-tests, /Users/klunnyi/jb/tbe-server/mods/tbe-cwm, /Users/klunnyi/jb/tbe-server/mods/tbe-license-vault, /Users/klunnyi/jb/tbe-server/mods/tbe-license-vault-wrapper, /Users/klunnyi/jb/tbe-server/mods/tbe-shared-indexes, /Users/klunnyi/jb/tbe-server/tbe-ui-tests/ide, /Users/klunnyi/jb/tbe-server/tbe-ui-tests/tba, /Users/klunnyi/jb/tbe-server/tbe-ui-tests/tools, /Users/klunnyi/jb/tbe-server/buildSrc], failed=false)
...
2023-11-29 10:34:07,623 [ 470933]   FINE - #o.j.k.i.script - [KOTLIN_SCRIPTING] save script models after import: [KotlinDslScriptModel(file=/Users/klunnyi/jb/tbe-server/settings.gradle.kts, inputs=GradleKotlinScriptConfigurationInputs(sections=pluginManagement{
..., buildRoot=/Users/klunnyi/jb/tbe-server), classPath=[/Users/klunnyi/.gradle/caches/8.4/generated-gradle-jars/gradle-api-8.4.jar, ...] ...

```

###### Dependencies resolution: standalone vs in-module

```
2023-11-29 10:27:39,575 [  82885]   FINE - #o.j.k.i.script - [KOTLIN_SCRIPTING] /Users/klunnyi/jb/tbe-server/mods/tbe-cwm/build.gradle.kts [resolve-scope] language-feature: true, backward-compatibility-flag: false
2023-11-29 10:27:39,575 [  82885]   FINE - #o.j.k.i.script - [KOTLIN_SCRIPTING] /Users/klunnyi/jb/tbe-server/mods/tbe-cwm/build.gradle.kts [resolve-scope] standalone-by-design: true
2023-11-29 10:27:39,576 [  82886]   FINE - #o.j.k.i.script - [KOTLIN_SCRIPTING] /Users/klunnyi/jb/tbe-server/mods/tbe-cwm/build.gradle.kts [resolve-scope] under-source-root: false
2023-11-29 10:27:39,576 [  82886]   FINE - #o.j.k.i.script - [KOTLIN_SCRIPTING] /Users/klunnyi/jb/tbe-server/mods/tbe-cwm/build.gradle.kts [resolve-scope] => standalone
```

###### Compiler arguments used at resolution

```
2023-11-29 10:34:08,674 [ 471984]   FINE - #o.j.k.i.script - [KOTLIN_SCRIPTING] /Users/klunnyi/jb/tbe-server/buildSrc/build.gradle.kts compiler options: [-language-version, 1.9, -language-version, 1.8, -api-version, 1.8, -Xjvm-default=all, -Xjsr305=strict, -Xskip-metadata-version-check, -Xskip-prerelease-check, -Xallow-unstable-dependencies, -XXLanguage:+DisableCompatibilityModeForNewInference, -XXLanguage:-TypeEnhancementImprovementsInStrictMode, -P=plugin:org.jetbrains.kotlin.assignment:annotation=org.gradle.api.SupportsKotlinAssignmentOverloading]
```


## Script Definition

**What is script definition**

Script definition is a container having:
1. Script compilation configuration (its zero-state, not refined).
2. Script host configuration.


At the moment, we support two types of script definitions represented by the following classes:
1. `org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition` - outdated
2. `kotlin.script.experimental.host.ScriptDefinition` as a more modern one (`@KotlinScript`).  
3. `org.jetbrains.kotlin.scripting.definitions.ScriptDefinition` wrapper over outdated `KotlinScriptDefinition`(1) and modern
`ScriptDefinition`(2).
There is a hierarchy with this class as its root. Child-classes reflect possible ways of configuration construction. E.g.
`FromNewDefinition` 

**How definitions are loaded**

The first thing to know about definitions loading is that it's not on demand. All definitions are loaded after project opening, see
`LoadScriptDefinitionsStartupActivity`.

The entry point into loading mechanics is `ScriptDefinitionsManager.findDefinition` and `ScriptDefinitionsManager.currentDefinitions`
The method just goes through caches and delegates via its `super` back to the property. 

An Important point to know here is that there is a UI allowing to control enabled/disabled status per definition. You can find it the
`Settings => Languages & Frameworks => Kotlin => Kotlin Scripting`.

There are many `ScriptDefinitionsSource`-s, objects responsible for loading.


## From Definition to definition/configuration manager and ScriptClassRootsCache

Script definition is a container having, among others, script compilation configuration.
The latter has at least two incarnations: inc-zero (stored in Definition) representing empty-object and inc-refined reflecting the last 
analyzed script PSI tree (contains an actual set of imports, receivers, dependencies, etc.).

There are two EPs or managers, each providing either inc-zero or inc-refined configurations: `ScriptDefinitionsManager` and
`ScriptConfigurations` manager. 

Definitions can be thought as mainly static entities. They are loaded in memory once and reloaded relatively seldom:
* in case of unsuccessful loading attempts in the past.
* as no longer needed (e.g. console closure or explicit disabling via settings UI) 
* in case, external parameters affecting definitions discovery changed.

All loaded definitions specify a file type they handle. On IDEA level all these types are mapped to `KotlinFileType.INSTANCE` to be handled
by the Kotlin plugin.

## Resolution

*K1*

`KotlinCacheServiceImpl#getFacadeToAnalyzeFile`

What is ModuleInfo from the compiler standpoint? What are script related ones?


## Resolution scope

When a script file (or its part) is resolved the Platform requires an instance of `GlobalSearchScope`.
This scope can be thought as a set of files to be scanned when a class by its FQN is searched. For scripts, it means both its source code
and its dependencies.

PSI file (`KtFile`) has its scope defined at `PsiFileImpl.getResolveScope`. At this point `ResolveScopeManager` comes into play.
Its key method `ResolveScopeManagerImpl.createScopeByFile` combines several scopes.
First, it delegates scope calculation to `ResolveScopeProvider.EP_NAME` (`KotlinScriptResolveScopeProvider` and
`ScriptDependenciesResolveScopeProvider`). In case none provides anything, `ResolveScopeManagerImpl` uses the most relevant (inherent)
scope: module, library, project. 

`KotlinScriptResolveScopeProvider` calls `ModuleInfoProviderUtils.getModuleInfoOrNull (PsiElement.moduleInfoOrNull)` which defines.

ModuleProductionSourceInfo, ScriptModuleInfo


## Why having separate mechanisms (managers) for getting/loading definitions and configurations

At first glance, looks like `configuration = definition.configuration`.


## What is important for highlighting

### Dependencies

to-be-described

### SDK

Having SDK "known" is crucial by the moment a script is analysed. SDK (besides StdLib) participates in the algo defining Kotlin Built-Ins.
Missing SDK results in default Built-Ins having for instance no idea about class `kotlin.Clonable`
(see `IdeaResolverForProject.BuiltInsCache.getOrCreateIfNeeded`). 

It's expected that the SDK is available in the `SdkInfoCache (SdkInfoCacheImpl)` by corresponding `ModuleInfo` (i.e. `ScriptModuleInfo` and
`ScriptDependenciesInfo`). The value is calculated  at `SdkInfoCacheImpl.calculate`. Closer look at the method leads to
`ModuleInfo.dependencies` (i.e. `ScriptModuleInfo.dependencies` => `ScriptDependenciesInfo.ForFile.getSdk` => ... `ScriptClassRootsCache.getScriptSdk`).

To have required `script ==> SDK` mapping in the `SdkInfoCache` it's vital to have this `ScriptClassRootsCache` filled by the right moment.
See `ScriptClassRootsUpdater.recreateRootsCacheAndDiff`.

## Tests

```
[FIND USAGES]

    org.jetbrains.kotlin.findUsages.AbstractKotlinScriptFindUsagesTest
    org.jetbrains.kotlin.findUsages.KotlinScriptFindUsagesTestGenerated
```

```
[RUN CONFIGURATIONS]

    org.jetbrains.kotlin.idea.run.StandaloneScriptRunConfigurationTest
```

```
[PROJECT WIZARD]

    community/plugins/kotlin/project-wizard

PROBLEMS:

    Wizard is no longer used. Tests should be Ignored (as minimum). Generated scripts should be probably 
    moved to highliting group of tests.
```

```
[DEBUGGER EVALUATE EXPRESSION]

    org.jetbrains.kotlin.idea.debugger.test.AbstractIrKotlinScriptEvaluateExpressionTest
    org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeScriptEvaluateExpressionTestGenerated (BROKEN)
    org.jetbrains.kotlin.idea.debugger.test.IrKotlinScriptEvaluateExpressionTestGenerated (BROKEN)
```


```
[DEFINITIONS DISCOVERY]

    org.jetbrains.kotlin.idea.script.ScriptTemplatesFromDependenciesTestGenerated
    org.jetbrains.kotlin.idea.script.ScriptTemplatesFromDependenciesProviderTest (BROKEN)

PROBLEMS:

    - ScriptTemplatesFromDependenciesProviderTest#testCustomDefinitionInJar is broken but looks important
    - Probably might become outdated after https://youtrack.jetbrains.com/issue/KT-61947/Scripting-get-rid-of-outdated-definitions.
```

```
[DEFINITIONS MANAGEMENT]

    org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManagerTest
    org.jetbrains.kotlin.idea.script.ScriptDefinitionsOrderTestGenerated
```

```
[CONFIGURATION LOADING]

    org.jetbrains.kotlin.idea.script.ScriptConfigurationLoadingTest8
```

```
[HIGHLIGHTING OF .KTS]

    org.jetbrains.kotlin.idea.script.ScriptConfigurationHighlightingTestGenerated
    org.jetbrains.kotlin.checkers.KotlinHighlightVisitorTestGenerated.Scripts
    org.jetbrains.kotlin.idea.highlighter.HighlightingTestGenerated.Uncategorized.testKotlinInJavaInjection
```

```
[HIGHLIGHTING OF EMBEDDED SCRIPTS]

        org.jetbrains.kotlin.idea.highlighter.HighlightingTestGenerated.Uncategorized.testKotlinInJavaInjection
```


```
[HIGHLIGHTING OF .GRADLE.KTS]

    org.jetbrains.kotlin.idea.codeInsight.gradle.GradleBuildFileHighlightingTest
    org.jetbrains.kotlin.tools.projectWizard.wizard.ScriptHighlightingGradleDistributionTypeTest (BROKEN!)
    org.jetbrains.kotlin.idea.codeInsight.gradle.GradleBuildFileHighlightingTest.MultiplesJdkTableEntriesWithSamePathButFirstHasCorruptedRoots


GENERAL PROBLEMS:

    - What Gradle versions do we test against? (https://youtrack.jetbrains.com/issue/KTIJ-27067/Support-for-Gradle-7.x-and-8.x-in-tests)
    - Probably we need to have an updatable set of scripts to test highlighting for.

PROBLEMS:

    - GradleBuildFileHighlightingTest.KtsInJsProject2114 is only for Gradle < 6.0.
        Should it support other versions?
    - GradleBuildFileHighlightingTest.ComplexBuildGradleKts is ignored at creation time.
        Probably we need to have a set of scripts to test highlighting for.
```

```
[GRADLE IMPORT => CONFIGURATION AVAILABILITY]

    org.jetbrains.kotlin.idea.codeInsight.gradle.GradleKtsImportTest
```

```
[.GRALDE.KTS UPDATE => CONFIGURATION REFINEMENT]

    org.jetbrains.kotlin.idea.scripting.gradle.GradleScriptListenerTest
```

```
[SCRATCH/WORKSHEET]

    org.jetbrains.kotlin.idea.scratch.ScratchOptionsTest
    org.jetbrains.kotlin.idea.scratch.SequentialScratchExecutorTest
    org.jetbrains.kotlin.idea.scratch.ScratchRunActionTestGenerated
    org.jetbrains.kotlin.idea.scratch.CustomScratchRunActionTest
    org.jetbrains.kotlin.idea.scratch.AbstractScratchLineMarkersTest
```

```
[SCRIPT SETTINGS PERSISTENCE]

    org.jetbrains.kotlin.idea.script.ScriptOptionsSaveTest

PROBLEMS: 

    - definitions order isn't checked
```

```
[LIGHT CLASSES]

    org.jetbrains.kotlin.asJava.classes.IdeLightClassesByPsiTestGenerated.Scripts
    org.jetbrains.kotlin.asJava.classes.IdeLightClassesByFqNameTestGenerated.Script
```

```
[REFACTORINGS]

    org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated.IntroduceVariable.Script
    org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated.ExtractFunction.Script
    org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated.IntroduceParameter.Script
    org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated.IntroduceProperty.Script
```

```
[CONFIGURATION INFLUENCE ON COMPLETION, IMPORTS, NAVIGATION]

    org.jetbrains.kotlin.idea.script.ScriptConfigurationNavigationTestGenerated
    org.jetbrains.kotlin.idea.script.AbstractScriptConfigurationInsertImportOnPasteTest
    org.jetbrains.kotlin.idea.script.ScriptConfigurationCompletionTestGenerated
```

```
[WORKSPACE MODEL]

    org.jetbrains.kotlin.idea.codeInsight.gradle.GradleKtsImportTest.WorkspaceModelSyncTest
    org.jetbrains.kotlin.idea.script.ScriptWorkspaceModelRepresentationTest
```

```
[QUICK FIXES, OPTIN]

    org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated.OptIn.Scripts.WithLocalElements
```

```
[PRESSING ENTER, FORMATTING, PERFORMANCE]
   
    org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated.DirectSettings.Script
    org.jetbrains.kotlin.idea.perf.synthetic.PerformanceTypingIndentationTestGenerated.DirectSettings.Script
```

## Gradle build scripts

### How buildSrc is supported

`GradleScriptAdditionalIdeaDependenciesProvider`


## IDEA modules relevant for scripting