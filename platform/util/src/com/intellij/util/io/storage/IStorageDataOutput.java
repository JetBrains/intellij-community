// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.storage;

import com.intellij.util.io.RepresentableAsByteArraySequence;

import java.io.Closeable;
import java.io.DataOutput;
import java.io.Flushable;

public interface IStorageDataOutput extends Closeable, Flushable, DataOutput, RecordDataOutput,
                                            RepresentableAsByteArraySequence {}
