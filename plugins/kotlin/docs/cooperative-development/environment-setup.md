# Cooperative Development Environment Setup

## Repositories

* `kotlin` repository. Default branch is `master`
* `intellij` repository. Default branch is `kt-master`. `kt-master` can be compiled against the latest Kotlin compiler from `master`.

## Setup

1. Clone [kotlin](https://github.com/JetBrains/kotlin) repository. Kotlin repository contains Analysis API and Kotlin compiler
2. Set up kotlin repository. See [kotlin/readme](https://github.com/JetBrains/kotlin/blob/master/ReadMe.md) for more info. But generally, it should be enough to add this line to the `local.properties` file inside `kotlin` repository
```
kotlin.build.isObsoleteJdkOverrideEnabled=true
```

3. Clone IntelliJ Ultimate/IntelliJ Community (this repository) with branch `kt-master` into `kotlin` repository directory, so directory structure is the following:
```
kotlin [branch = master]
├── intellij [branch = kt-master]
│   └── ...
└── ...
```

4. Build compiler for IDE: run `Kotlin Coop: Publish compiler-for-ide JARs` run configuration in `intellij` repository.
   This configuration will compile Analysis API and related compilers and put them into jars.
   Those jars are used to build `intellij`.
   You should execute this task every time you update the code inside `kotlin` repository.
