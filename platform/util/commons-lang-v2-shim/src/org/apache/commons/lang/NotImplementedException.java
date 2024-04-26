// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.apache.commons.lang;

/**
 * @deprecated To continue using the functionality offered by commons-lang2,
 * please consider migrating to either the commons-lang3 or commons-text libraries and bundling them with your plugin.
 * Or consider using the corresponding API from IJ Platform.
 */
@Deprecated(forRemoval = true)
public final class NotImplementedException extends org.apache.commons.lang3.NotImplementedException {
  public NotImplementedException() {
  }

  public NotImplementedException(String message) {
    super(message);
  }

  public NotImplementedException(Throwable cause) {
    super(cause);
  }

  public NotImplementedException(String message, Throwable cause) {
    super(message, cause);
  }

  public NotImplementedException(String message, String code) {
    super(message, code);
  }

  public NotImplementedException(Throwable cause, String code) {
    super(cause, code);
  }

  public NotImplementedException(String message, Throwable cause, String code) {
    super(message, cause, code);
  }
}
