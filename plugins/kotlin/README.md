# Kotlin plugin [![official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

Kotlin plugin is an IntelliJ IDEA plugin for the [Kotlin programming language](https://kotlinlang.org/).

## Contents

1. [Set up instructions](#1-set-up-instructions)
2. [Frequently Asked Questions](#2-frequently-asked-questions)

## 1. Set up instructions

### 1.1. Requirements
- [IntelliJ IDEA](https://jetbrains.com/idea/download) Community or Ultimate, 2020.3 or later
- [Kotlin Plugin](https://plugins.jetbrains.com/plugin/6954-kotlin) 1.4.21 or later

### 1.2. Clone required repository

Clone the [intellij-community](https://github.com/JetBrains/intellij-community) repository:

```bash
git clone https://github.com/JetBrains/intellij-community.git
```

### 1.3. Follow IntelliJ set up instructions

Follow the [set up instructions in IntelliJ Community](https://github.com/JetBrains/intellij-community/blob/master/README.md).

⚠️ You need to set up multiple JDKs and possibly attach `tools.jar` from the JDK distribution to some of them.  
⚠️ Set a higher value for build process heap memory size. Use the "Build process heap size (MBytes)" text field instead of
more generic "VM options" (shared or user-local). `3000` or `4000` megabytes should work well.  
⚠️ Check the "Compile independent modules in parallel" option, or the project will take ages to build.

### 1.4. Build the project

Now you should be able to open and build the project.

Use the provided `IDEA` build configuration to run an IntelliJ IDEA instance with your modifications to the Kotlin plugin.

## 2. Commits Requirements

We strive for transparency of changes. Therefore, all meaningful commits should reference a
[KTIJ](https://youtrack.jetbrains.com/issues/KTIJ) issue in their message.
It is permissible not to follow this rule if a change relates to tests, it is a minor refactoring or code formatting. 

## 3. Frequently Asked Questions

Q. How can I contribute to the Kotlin IDEA plugin?
A. We accept Pull Requests. Please read our [contribution guide](CONTRIBUTING.md) for more information.

Q. Kotlin IDEA plugin sources are used to be inside the [JetBrains/kotlin](https://github.com/JetBrains/kotlin) repository. What happened?  
A. We decided to split release cycles for the Kotlin compiler and the IDEA plugin. Now, the majority of new features will arrive together
with updates of IntelliJ IDEA.

Q. But the plugin sources are still in [JetBrains/kotlin](https://github.com/JetBrains/kotlin).  
A. Code transition is in progress. We will delete the IDE plugin sources from the old repository when we decide it is a good time to do so.

Q. What will happen with Pull Requests submitted to the [JetBrains/kotlin](https://github.com/JetBrains/kotlin) repository?  
A. We will process all existing PRs and replicate commits in the new repository. However, all new PRs to the Kotlin IDEA plugin code
should be done only in this repository.

## 3. License
Kotlin is distributed under the terms of the Apache License (Version 2.0). See [license folder](license/README.md) for details.
