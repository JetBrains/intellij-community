// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.containers.hash.EqualityPolicy;

/**
 * @author max
 */
public interface KeyDescriptor<T> extends EqualityPolicy<T>, DataExternalizer<T> { }