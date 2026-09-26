package convention.multiplatform

import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property

abstract class MultiplatformOptionsExtension @Inject constructor(
  objects: ObjectFactory,
) {
  val jvm: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
  val linux: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
  val iOS: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
  val tvOS: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
  val macOS: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
  val watchOS: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
  val windows: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

  operator fun component1(): Boolean = jvm.get()
  operator fun component2(): Boolean = linux.get()
  operator fun component3(): Boolean = iOS.get()
  operator fun component4(): Boolean = tvOS.get()
  operator fun component5(): Boolean = macOS.get()
  operator fun component6(): Boolean = watchOS.get()
  operator fun component7(): Boolean = windows.get()

  companion object {
    internal const val NAME = "multiplatform"
  }
}

val ExtensionContainer.multiplatformOptions: MultiplatformOptionsExtension
  get() = getByType(MultiplatformOptionsExtension::class.java)
