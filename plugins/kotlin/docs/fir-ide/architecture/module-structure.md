# Kotlin Plugin Module Structure

Kotlin IDE is currently presented in two plugins: **K1** (the current one) and **K2** (the one in development).
Both of those plugins coexist in this repository.

We group modules as the following:

1. **Shared** modules that are used between both plugins. Those modules cannot depend on **K1** or **K2** plugin modules.
2. **K1**-plugin modules are used only in the **K1** plugin and can depend only on other **K1**-plugin modules or **shared** modules.
3. **K2**-plugin modules are used only in the **K2** plugin and can depend only on other **K2**-plugin modules or **shared** modules.

## Module naming convention

> The directory name should not contain `k2` suffix. After **K1** plugin removal, the directory name will have to be renamed, and thus all
> files
> will be moved, which is probably not what we want to see in the git history. If only a module name had a module-type suffix, the renaming
> would be simply a few changes in `.iml` files. It's okay for **K1** modules to contain `k1` substring in a directory name.

To distinguish modules by their kinds, the following convention is used (please, note that currently, it's not satisfied for some modules in
the project):

1. **Shared** module names ends with `.shared`. E.g, `kotlin.code-insight.api.shared`.
2. **K1** plugin module names ends with `.k1`. E.g, `kotlin.completion.k1`.
3. **K2**-plugin module names name ends with `.k2`. E.g, `kotlin.refactoring.rename.k2`.

**K2** and **shared** modules have to be grouped by their subsystem.
If the subsystem is large enough, it should be implemented in the separated module to control its dependencies better.
Small subsystems may follow the same convention and be extracted into a separate module or can be grouped into a parent subsystem module.
For example, `code-insight/override-implement` may live in its own `kotlin.code-insight.override-implement.k2` module, or be in its parent
module `kotlin.code-insight.impl.k2`.

It's okay for the **K1** modules to not follow those rules.

Example of module structure:

```
kotlin/
├─ code-ingisht/
│  ├─ api (module name `kotlin.code-insight.api.shared, package name `org.jetbrains.kotlin.idea.code.insight.api`)`
├─ refactoring/
│  ├─ rename (module name `kotlin.refactoring.rename.k2`, package name `org.jetbrains.kotlin.idea.refactoring.rename`)
│  ├─ move (module name `kotlin.refactoring.move.k2`, package name `org.jetbrains.kotlin.idea.refactoring.move`)

```

> K2 Tests should be in the same module as the production code, there is no such requirement for **K1** modules.

## Package naming convention

> `k2` or `shared` substring should not be present in package name for the same reason as described
> in [Module naming convention](#module-naming-convention), `k1` substring may be present.

All Kotlin plugin production (i.e., non-test) package names for **K2** and **Shared** modules should match the corresponding module
structure.
It's formed as:

1. package prefix `org.jetbrains.kotlin.idea`
2. comma-separated module name parts, e.g. for the module `kotlin.code-insight.api.shared`, the resulted package
   is `org.jetbrains.kotlin.idea.code.insight.api`

The package name for K2 test e the same as corresponding production package except for the prefix: `org.jetbrains.kotlin.idea.k2`.
The prefix `.k2` is needed for the TeamCity to distinguish between **K2** and **K1** tests.
E.g., if the production package name is `org.jetbrains.kotlin.idea.refactoring.move`,
test package name should be `org.jetbrains.kotlin.idea.k2.refactoring.move`

## New Plugin model

New modules should use [the new plugin model](https://youtrack.jetbrains.com/articles/IDEA-A-65/Plugin-Model) when possible.
That also means that every module should have a unique package name.