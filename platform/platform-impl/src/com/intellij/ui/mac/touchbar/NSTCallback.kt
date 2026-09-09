package com.intellij.ui.mac.touchbar

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.invoke.MethodHandles

/**
 * Keeps the stub alive because native peers can retain callbacks after release.
 * [clear] detaches the Java target without invalidating a pending native call.
 */
@ApiStatus.Internal
class NSTCallback private constructor(target: Any?, method: String, descriptor: FunctionDescriptor) {
  @Volatile
  var target: Any? = target

  val stub: MemorySegment = Linker.nativeLinker().upcallStub(
    MethodHandles.lookup().findVirtual(javaClass, method, descriptor.toMethodType()).bindTo(this), descriptor, Arena.global()
  )

  fun clear() {
    target = null
  }

  fun execute() {
    safely(Unit) { (target as NSTLibrary.Action?)?.execute() }
  }

  fun createItem(uid: MemorySegment): MemorySegment = safely(MemorySegment.NULL) {
    (target as NSTLibrary.ItemCreator?)?.createItem(uid.reinterpret(Long.MAX_VALUE).getString(0)) ?: MemorySegment.NULL
  }

  fun executeScrubber(index: Int) {
    safely(Unit) { (target as NSTLibrary.ScrubberDelegate?)?.execute(index) }
  }

  fun update(): Int = safely(0) { (target as NSTLibrary.ScrubberCacheUpdater?)?.update() ?: 0 }

  private inline fun <T> safely(fallback: T, action: () -> T): T {
    try {
      return action()
    }
    catch (error: Throwable) {
      try {
        logger<NSTCallback>().warn("The Touch Bar callback failed", error)
      }
      catch (_: Throwable) {
      }
      return fallback
    }
  }

  companion object {
    fun action(target: NSTLibrary.Action?): NSTCallback = NSTCallback(target, "execute", FunctionDescriptor.ofVoid())
    fun creator(target: NSTLibrary.ItemCreator?): NSTCallback = NSTCallback(target, "createItem", FunctionDescriptor.of(ADDRESS, ADDRESS))
    fun delegate(target: NSTLibrary.ScrubberDelegate?): NSTCallback =
      NSTCallback(target, "executeScrubber", FunctionDescriptor.ofVoid(JAVA_INT))
    fun updater(target: NSTLibrary.ScrubberCacheUpdater?): NSTCallback = NSTCallback(target, "update", FunctionDescriptor.of(JAVA_INT))
  }
}
