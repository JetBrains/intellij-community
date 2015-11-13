// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jetbrains.jsonProtocol;

/**
 * A base interface for JSON subtype interface. This inheritance serves 2 purposes:
 * it declares base type (visible to human and to interface analyzer) and adds {@link #getBase()}
 * getter that may be directly used in programs.
 */
public interface JsonSubtype<BASE> {
  BASE getBase();
}
