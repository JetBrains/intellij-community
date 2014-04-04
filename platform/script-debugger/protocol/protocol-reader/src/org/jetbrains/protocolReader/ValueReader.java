// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jetbrains.protocolReader;

import org.jetbrains.annotations.Nullable;

/**
 * A parser that accepts value of JSON field and outputs value in another form (e.g. string
 * is converted to enum constant) to serve field getters in JsonType interfaces.
 */
abstract class ValueReader {
  private final boolean nullable;

  protected ValueReader(boolean nullable) {
    this.nullable = nullable;
  }

  public ObjectValueReader asJsonTypeParser() {
    return null;
  }

  abstract void appendFinishedValueTypeName(TextOutput out);

  void appendInternalValueTypeName(FileScope scope, TextOutput out) {
    appendFinishedValueTypeName(out);
  }

  abstract void writeReadCode(ClassScope methodScope, boolean subtyping, String fieldName, TextOutput out);

  public boolean isNullable() {
    return nullable;
  }

  abstract void writeArrayReadCode(ClassScope scope, boolean subtyping, boolean nullable, String fieldName, TextOutput out);

  protected void beginReadCall(String readPostfix, boolean subtyping, TextOutput out, @Nullable String fieldName) {
    out.append("read");
    if (isNullable()) {
      out.append("Nullable");
    }
    out.append(readPostfix).append('(');
    addReaderParameter(subtyping, out);
    if (!isNullable()) {
      out.comma();
      if (subtyping) {
        out.append("null");
      }
      else if (fieldName == null) {
        out.append("name");
      }
      else {
        out.quoute(fieldName);
      }
    }
  }

  protected static void addReaderParameter(boolean subtyping, TextOutput out) {
    out.append(subtyping ? Util.PENDING_INPUT_READER_NAME : Util.READER_NAME);
  }

  public boolean isThrowsIOException() {
    return false;
  }
}
