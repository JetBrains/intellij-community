// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.filewatch.impl.watchservice.jna

import com.sun.jna.LastErrorException
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure

internal interface MacKQueueApi : Library {
  @Throws(LastErrorException::class)
  fun kqueue(): Int

  @Throws(LastErrorException::class)
  fun kevent(
    kq: Int,
    changelist: KEvent?,
    nchanges: Int,
    eventlist: KEvent?,
    nevents: Int,
    timeout: Pointer?,
  ): Int

  @Throws(LastErrorException::class)
  fun open(path: String, flags: Int): Int

  @Throws(LastErrorException::class)
  fun close(fd: Int): Int
}

@Structure.FieldOrder("ident", "filter", "flags", "fflags", "data", "udata")
@Suppress("unused")
internal class KEvent() : Structure() {
  @JvmField var ident: NativeLong = NativeLong(0)
  @JvmField var filter: Short = 0
  @JvmField var flags: Short = 0
  @JvmField var fflags: Int = 0
  @JvmField var data: NativeLong = NativeLong(0)
  @JvmField var udata: Pointer? = null

  constructor(ident: Int, filter: Short, flags: Short, fflags: Int) : this() {
    this.ident = NativeLong(ident.toLong())
    this.filter = filter
    this.flags = flags
    this.fflags = fflags
  }
}

internal val macKQueueApi: MacKQueueApi = Native.load("System", MacKQueueApi::class.java)

internal const val K_QUEUE_OPEN_EVENT_ONLY: Int = 0x8000

internal const val K_EVENT_FILTER_VNODE: Short = -4
internal const val K_EVENT_ADD: Short = 0x0001
internal const val K_EVENT_CLEAR: Short = 0x0020

internal const val K_NOTE_DELETE: Int = 0x00000001
internal const val K_NOTE_WRITE: Int = 0x00000002
internal const val K_NOTE_EXTEND: Int = 0x00000004
internal const val K_NOTE_RENAME: Int = 0x00000020
internal const val K_NOTE_REVOKE: Int = 0x00000040
