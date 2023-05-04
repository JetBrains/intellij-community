// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.instrumentation

import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.EntityStorageSnapshot
import com.intellij.workspaceModel.storage.MutableEntityStorage

interface EntityStorageInstrumentation : EntityStorage
interface EntityStorageSnapshotInstrumentation : EntityStorageSnapshot, EntityStorageInstrumentation
interface MutableEntityStorageInstrumentation : MutableEntityStorage, EntityStorageInstrumentation
