package com.intellij.ui.mac

import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import org.jetbrains.annotations.ApiStatus
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS

@ApiStatus.Internal
object MacWindowPreferences {
  private val copyAppValue = Linker.nativeLinker().downcallHandle(
    SymbolLookup.libraryLookup(
      "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global()
    ).findOrThrow("CFPreferencesCopyAppValue"),
    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS),
  )

  fun readString(key: String, applicationId: String): String? {
    val pool = Foundation.NSAutoreleasePool()
    try {
      val value = copyAppValue.invokeExact(
        Foundation.nsString(key).asMemorySegment(), Foundation.nsString(applicationId).asMemorySegment()
      ) as MemorySegment
      if (value.address() == 0L) return null
      val identifier = ID(value.address())
      try {
        return Foundation.toStringViaUTF8(identifier)
      }
      finally {
        Foundation.cfRelease(identifier)
      }
    }
    finally {
      pool.drain()
    }
  }
}
