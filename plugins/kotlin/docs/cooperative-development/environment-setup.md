# Cooperative Environment Guide

The Kotlin plugin for IntelliJ IDEA is built on top of the Kotlin compiler and the [Kotlin Analysis API](https://kotl.in/analysis-api).
To improve the pace of development and bug fix delivery, compiler artifacts in the `master` branch of IntelliJ IDEA are regularly advanced
to match the state of the Kotlin compiler's `master`.

Keeping development branches of two different repositories synchronized is problematic. Instead, we use a buffer branch, `kt-master`, 
which is aligned with commits from the Kotlin compiler's `master`. Approximately every week, this branch is two-way synchronized with the
`master` branch of IntelliJ IDEA.

In the `kt-master` branch, we accumulate changes that depend on the latest commits in the compiler repository. These are a minority of all
changes in the Kotlin plugin — most of them are related either to new language features or to changes in the Analysis API. All other
changes simply go into the `master` branch.

```
──▲────▲────▲────▲────► master
  │    │    │    │
──▼────▼────▼────▼────► kt-master
  1w   1w   1w   1w
```

To simplify the development process, we provide the *cooperative environment* — a way of setting up the Kotlin and IntelliJ IDEA projects
so it becomes possible to work on both simultaneously.

## Setup

**If you are not changing the Kotlin compiler, you don't need to set up the cooperative environment.**  
**Submit your pull request directly to the `master` branch of IntelliJ IDEA.**

1. Clone the [Kotlin](https://github.com/JetBrains/kotlin) repository.
2. Clone the [intellij-community](https://github.com/JetBrains/intellij-community) repository (or `ultimate` for JetBrains developers).
3. Set up both repositories
   ([README](https://github.com/JetBrains/kotlin/blob/master/ReadMe.md) for Kotlin,
   [README](https://github.com/JetBrains/intellij-community/blob/master/README.md) for IntelliJ IDEA).
4. Switch to the `kt-master` branch of the IntelliJ repository.
5. In the IntelliJ repository, create the `local.properties` file in `plugins/kotlin/util/project-model-updater` of `intellij-community`.
6. In the created file, specify the path to the Kotlin repository: `kotlinCompilerRepoPath=/path/to/kotlin`. The path can be absolute or
   relative to the IntelliJ's project root directory.
7. Run the `Kotlin Coop: Publish Compiler JARs` run configuration (*Run* menu -> *Run…*) — it will assemble all required artifacts and put
   them into `lib/kotlin-snapshot`.
8. You should now be able to compile the IntelliJ project normally.

Now, whenever you change something in the Kotlin repository, run `Kotlin Coop: Publish Compiler JARs` again so the changes propagate to
IntelliJ IDEA.

As an alternative, you can clone IntelliJ IDEA into the `intellij` directory of the Kotlin repository.
In this case, you can avoid specifying the path to the Kotlin repository in the `local.properties` file.
The folder structure should look like this:

```
kotlin [branch = master]
├── intellij [branch = kt-master]
│   └── ...
└── ...
```

The setup is simpler, but it ties both projects together.
If you usually work on both projects separately, you may want to choose to specify the Kotlin repository path explicitly.

## Contributing to `kt-master`

Submit pull requests to both the Kotlin and IntelliJ IDEA repositories, adding cross-links between them.
Ideally, use the same branch name in both repositories.
The Kotlin team will review and merge pull requests manually.