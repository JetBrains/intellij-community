// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.workspace.api

import com.intellij.workspace.api.pstorage.PEntityStorage
import com.intellij.workspace.api.pstorage.PEntityStorageBuilder
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import it.unimi.dsi.fastutil.objects.ReferenceSet
import org.jetbrains.annotations.TestOnly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@TestOnly
fun TypedEntityStorage.checkConsistency() {

  if (this is PEntityStorage) {
    this.assertConsistency()
    return
  }

  if (this is PEntityStorageBuilder) {
    this.assertConsistency()
    return
  }
}

internal data class SampleEntitySource(val name: String) : EntitySource
