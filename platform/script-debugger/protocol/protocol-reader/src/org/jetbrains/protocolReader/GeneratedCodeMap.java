// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jetbrains.protocolReader;

import java.util.Map;

/**
 * Maps json type interfaces to full class name of their generated implementations.
 */
public class GeneratedCodeMap {
  private final Map<Class<?>, String> typeToImplClassName;

  public GeneratedCodeMap(Map<Class<?>, String> typeToImplClassName) {
    this.typeToImplClassName = typeToImplClassName;
  }

  String getTypeImplementationReference(Class<?> type) {
    return typeToImplClassName.get(type);
  }
}
