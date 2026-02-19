// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.io.mandatory;

import com.google.gson.JsonParseException;

public final class JsonMandatoryException extends JsonParseException {
  public JsonMandatoryException(String msg) {
    super(msg);
  }
}
