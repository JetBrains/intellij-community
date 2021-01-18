// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
enum class StrictHostKeyChecking {
  /** Never automatically add host keys to the known hosts file. */
  YES,

  /** Automatically add new host keys to the user known hosts files, but not permit connections to hosts with changed host keys. */
  ACCEPT_NEW,

  /** Automatically add new host keys to the user known hosts files and allow connections to hosts with changed host keys to proceed. */
  NO,

  /** New host keys will be added to the user known host files only after the user has confirmed that is what they really want to do. */
  ASK,
}