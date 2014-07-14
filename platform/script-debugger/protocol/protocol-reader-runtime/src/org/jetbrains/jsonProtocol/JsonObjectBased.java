// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jetbrains.jsonProtocol;

import org.jetbrains.io.JsonReaderEx;

/**
 * Optional base interface for JSON type interface. Underlying JSON object becomes available
 * to user this way.
 */
public interface JsonObjectBased {
  JsonReaderEx getDeferredReader();
}