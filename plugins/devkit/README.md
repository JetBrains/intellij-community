# Plugin DevKit Implementation Notes

## Inspections

See `org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil` for common utility methods.

Inspections should avoid running whenever they're not applicable in current context.
This includes:

- required minimum platform version (`org.jetbrains.idea.devkit.util.PluginPlatformInfo`)
- required present platform API class (only available in newer platform versions). It is safe to assume most plugin do **not** target the latest platform.
- required present plugin API class (e.g., checks specific to Java PSI)
- checked class is not a suitable candidate (e.g., a class without FQN cannot be registered in `plugin.xml`)
- only applicable to IDEA project (`org.jetbrains.idea.devkit.util.PsiUtil.isIdeaProject()`)

### Code: JVM Languages

Use `org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase` or `org.jetbrains.idea.devkit.inspections.DevKitJvmInspection`.
See their docs for important considerations.

Implement and register in `intellij.devkit.core` module.

Tests must cover both Java and Kotlin sources explicitly.

### Code: Kotlin Only

Use regular `LocalInspectionTool` with immediate `DevKitInspectionUtil.isAllowed(holder.file)` check to skip running in non-plugin context.

Implement and register in `intellij.kotlin.devkit` module.

### Plugin Descriptor (plugin.xml)

Extend `org.jetbrains.idea.devkit.inspections.DevKitPluginXmlInspectionBase` and override `checkDomElement()` to check specific DOM
elements.