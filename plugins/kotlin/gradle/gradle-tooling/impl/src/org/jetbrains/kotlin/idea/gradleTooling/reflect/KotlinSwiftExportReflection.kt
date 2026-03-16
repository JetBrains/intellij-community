package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Reflection wrapper for `SwiftExportExtension` from the Kotlin Gradle Plugin.
 *
 * This class accesses the Swift Export model configured by DSL via reflection.
 *
 * ## KGP reference
 * `org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.SwiftExportExtension`
 *
 * ## DSL example in build.gradle.kts
 *
 * ```kotlin
 * kotlin {
 *     swiftExport {
 *         moduleName.set("MyModule")
 *         flattenPackage.set("com.example")
 *     }
 * }
 * ```
 *
 * @see KotlinExtensionReflection.swiftExport
 */
fun KotlinSwiftExportReflection(swiftExportExtension: Any): KotlinSwiftExportReflection = KotlinSwiftExportReflectionImpl(swiftExportExtension)

interface KotlinSwiftExportReflection {
  /**
   * The configured module name for Swift Export, or null if not configured.
   *
   * KGP: `SwiftExportExtension.moduleName: Property<String>`
   */
  val moduleName: String?

  /**
   * The package to flatten in the exported Swift module, or null if not configured.
   *
   * KGP: `SwiftExportExtension.flattenPackage: Property<String>`
   */
  val flattenPackage: String?
}

private class KotlinSwiftExportReflectionImpl(private val instance: Any) : KotlinSwiftExportReflection {

  override val moduleName: String? by lazy {
    getPropertyValue("getModuleName")
  }

  override val flattenPackage: String? by lazy {
    getPropertyValue("getFlattenPackage")
  }

  /**
   * Helper to get value from a Gradle `Property<String>` via reflection.
   *
   * The Swift Export extension uses Gradle's lazy properties (`Property<String>`),
   * so we need to call `.orNull` to get the actual value.
   */
  private fun getPropertyValue(getterName: String): String? {
    return try {
      val property = instance.callReflectiveAnyGetter(getterName, logger)
      when (property) {
        is Property<*> -> property.orNull as? String
        is Provider<*> -> property.orNull as? String
        else -> property as? String
      }
    }
    catch (_: Exception) {
      null
    }
  }

  companion object {
    private val logger = ReflectionLogger(KotlinSwiftExportReflection::class.java)
  }
}
