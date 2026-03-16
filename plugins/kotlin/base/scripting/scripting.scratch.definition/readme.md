# Kotlin Scratch Script Definition Module

## Purpose

This module contains the definition of `KotlinScratchScript` - the script definition used for Kotlin scratch files (`.kts`) in the IDE.

## Design Rationale

This module was intentionally extracted from the larger `scripting.k2` module to maintain specific characteristics:

- **Lightweight**: Minimal dependencies (only Kotlin scripting libraries)
- **Java 1.8 Compatible**: Can run on older JVM versions
- **Standalone**: Can be packaged as a JAR and passed to Java command classpath

## Use Case

The primary use case is to provide the scratch script definition as a standalone JAR that can be passed to the Java command line via the `-classpath` option when executing Kotlin scratch scripts. This allows the script host to load the definition without requiring the entire IntelliJ plugin infrastructure.

## Constraints

⚠️ **Important**: Keep this module lightweight and avoid adding dependencies on IntelliJ Platform APIs or other heavy modules. If you need to extend scratch script functionality that requires platform integration, consider doing so in the consuming modules (e.g., `intellij.kotlin.jvm`) rather than here.
