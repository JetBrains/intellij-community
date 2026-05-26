# BTA CRI test fixture

A minimal Compiler Reference Index artifact set produced by `source-project/`,
a Kotlin/JVM Gradle build with `kotlin.compiler.generateCompilerRefIndex=true`.
Used by the BTA CRI tests to assert lookup and subtype behaviour without invoking
a real compiler at test time.

Currently built with **Kotlin 2.3.20**.

## Fixed assertions the fixture supports

- `FqName("fixtures.Animal")` is referenced by all four source files
  (`Animal.kt`, `Dog.kt`, `Cat.kt`, `Main.kt`) — convenient multi-file lookup.
- `FqName("fixtures.Animal")` has direct subtypes `[fixtures.Dog, fixtures.Cat]`
  (only entry in `subtypes.table`).

Other usable FqNames in `lookups.table`: `fixtures.Dog`, `fixtures.Cat`,
`fixtures.Animal.speak`, `fixtures.main`.

## Regeneration

```bash
./regenerate.sh
```

Requires Gradle 8+ on `PATH`. The script runs `gradle --no-daemon clean assemble`
inside `source-project/` and copies the three `.table` files back here. To bump
Kotlin, edit `source-project/build.gradle.kts`. After regenerating, verify the
two assertions above still hold.
