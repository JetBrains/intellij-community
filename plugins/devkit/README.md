# Plugin DevKit Implementation Notes

## Inspections

See `org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil` for common utility methods.

Consider marking inspections with _safe-only_ fixes ready for _Code | Code Cleanup..._
(`com.intellij.codeInspection.CleanupLocalInspectionTool`).

Inspections should avoid running whenever they are not applicable in the current context.
Add `projectType="INTELLIJ_PLUGIN"` in `plugin.xml` registration to avoid loading inspection in non-plugin projects.

Additional contexts include:

- requires minimum platform version (`org.jetbrains.idea.devkit.util.PluginPlatformInfo`)
- requires present platform API class (only available in newer platform versions).
  It is safe to assume most plugins do **not** target the latest platform.
- requires present plugin API class (e.g., checks specific to Java PSI)
- checked class is not a suitable candidate (e.g., a class without FQN cannot be registered in `plugin.xml`)
- only applicable to IDEA project (`com.intellij.openapi.project.IntelliJProjectUtil.isIntelliJPlatformProject()`)

### Code: JVM Languages

Use `org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase` or `org.jetbrains.idea.devkit.inspections.DevKitJvmInspection`.
See their docs for important considerations.

Implement and register in `intellij.devkit.core` module.

See [Testing Code: JVM Languages](#testing-code-jvm-languages).

### Code: Kotlin Only

Use regular `LocalInspectionTool` with immediate `DevKitInspectionUtil.isAllowed(holder.file)` check to skip running in non-plugin context.

Implement and register in `intellij.kotlin.devkit` module.

### Plugin Descriptor

Extend `org.jetbrains.idea.devkit.inspections.DevKitPluginXmlInspectionBase` and override `checkDomElement()` to check specific DOM
elements.

See [Testing: Plugin Descriptor](#testing-plugin-descriptor)

## Tests

Use _light_ tests only ideally.
See existing base classes like `org.jetbrains.idea.devkit.inspections.PluginModuleTestCase`
or `org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase`.

Add required classes in the test as "mock" containing the minimum set of required signatures.
Same for declaring platform extension points, declare them as "fake" directly in the testdata `plugin.xml`.

### Testing Code: JVM Languages

Always write explicit tests for both Java and Kotlin sources.

Tests and test data go to `intellij.devkit.java.tests` and `intellij.devkit.kotlin.tests`, respectively.
Related `plugin.xml` can be put on same level as code test data files.

Test data path constants: `org.jetbrains.idea.devkit.DevkitJavaTestsUtil` & `org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil`.

Kotlin test data: add `@file:Suppress("MISSING_DEPENDENCY_SUPERCLASS")` to mute errors from missing Mock SDK classes.

### Testing: Plugin Descriptor

Extend from `org.jetbrains.idea.devkit.inspections.PluginXmlDomInspectionTestBase`.