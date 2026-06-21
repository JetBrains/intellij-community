// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.filewatch.impl.watchservice.jna

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference

@Suppress("FunctionName")
internal interface MacFSEventsApi : Library {
  interface FSEventStreamCallback : Callback {
    fun invoke(
      streamRef: FSEventStreamRef,
      clientCallBackInfo: Pointer?,
      numEvents: NativeLong,
      eventPaths: Pointer,
      eventFlags: Pointer?,
      eventIds: Pointer?,
    )
  }

  fun CFArrayCreate(allocator: CFAllocatorRef?, values: Array<Pointer>, numValues: CFIndex, callbacks: Void?): CFArrayRef

  fun CFStringCreateWithCharacters(alloc: Void?, chars: CharArray, numChars: CFIndex): CFStringRef

  fun FSEventStreamCreate(
    allocator: Pointer?,
    callback: FSEventStreamCallback,
    context: Pointer?,
    pathsToWatch: CFArrayRef,
    sinceWhen: Long,
    latency: Double,
    flags: Int,
  ): FSEventStreamRef

  fun FSEventStreamSetDispatchQueue(streamRef: FSEventStreamRef, dispatchQueue: DispatchQueueRef)

  fun FSEventStreamStart(streamRef: FSEventStreamRef): Boolean

  fun FSEventStreamStop(streamRef: FSEventStreamRef)

  fun FSEventStreamInvalidate(streamRef: FSEventStreamRef)

  fun FSEventStreamRelease(streamRef: FSEventStreamRef)

  fun CFRelease(ref: PointerByReference)
}

internal val macFSEventsApi: MacFSEventsApi = Native.load("CoreServices", MacFSEventsApi::class.java)

internal const val K_FSEVENT_STREAM_CREATE_FLAG_NO_DEFER: Int = 0x00000002
internal const val K_FSEVENT_STREAM_CREATE_FLAG_FILE_EVENTS: Int = 0x00000010

@Suppress("FunctionName")
internal interface LibDispatchApi : Library {
  fun dispatch_queue_create(label: String, attr: Pointer?): DispatchQueueRef

  fun dispatch_release(dispatchObject: DispatchQueueRef)
}

internal val libDispatchApi: LibDispatchApi = Native.load("System", LibDispatchApi::class.java)

internal class CFAllocatorRef : PointerByReference()

internal class CFArrayRef : PointerByReference()

internal class FSEventStreamRef : PointerByReference()

internal class DispatchQueueRef : PointerByReference()

internal class CFIndex() : NativeLong() {
  constructor(value: Long) : this() {
    setValue(value)
  }

  override fun toByte(): Byte = toLong().toByte()

  override fun toShort(): Short = toLong().toShort()
}

internal fun cfIndex(value: Int): CFIndex = CFIndex(value.toLong())

internal class CFStringRef : PointerByReference()

internal fun String.toCFString(): CFStringRef {
  val chars = toCharArray()
  return macFSEventsApi.CFStringCreateWithCharacters(null, chars, cfIndex(chars.size))
}