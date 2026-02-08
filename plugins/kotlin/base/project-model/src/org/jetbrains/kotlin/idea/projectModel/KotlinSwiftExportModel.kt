package org.jetbrains.kotlin.idea.projectModel

import java.io.Serializable

/**
 * Model representing Swift Export configuration extracted from the Kotlin Gradle Plugin.
 *
 * This model captures the configuration from the `swiftExport {}` DSL block in build.gradle.kts
 * and is serialized during Gradle sync for use by IDE features.
 *
 * ## KGP Reference
 *
 * `org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.SwiftExportExtension`
 *
 * ## Usage in build.gradle.kts
 *
 * ```kotlin
 * kotlin {
 *     swiftExport {
 *         moduleName = "MyModule"
 *         flattenPackage = "com.example.app"
 *     }
 * }
 * ```
 */
interface KotlinSwiftExportModel : Serializable {
  /**
   * The configured module name for Swift Export.
   *
   * KGP: `SwiftExportExtension.moduleName: Property<String>` (inherited from `SwiftExportedModuleMetadata`)
   *
   * If not set, the project name is used as the default module name.
   */
  val moduleName: String?

  /**
   * The package to flatten in the exported Swift module.
   *
   * KGP: `SwiftExportExtension.flattenPackage: Property<String>` (inherited from `SwiftExportedModuleMetadata`)
   *
   * When set, the specified package prefix is collapsed in the generated Swift API.
   */
  val flattenPackage: String?
}
