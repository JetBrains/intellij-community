// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

abstract class Resource {
  public enum Attribute {
    SPEC_TITLE, SPEC_VERSION, SPEC_VENDOR, IMPL_TITLE, IMPL_VERSION, IMPL_VENDOR
  }

  public abstract URL getURL();

  public abstract InputStream getInputStream() throws IOException;

  public abstract byte[] getBytes() throws IOException;

  public String getValue(Attribute key) {
    return null;
  }

  @Override
  public String toString() {
    return getURL().toString();
  }
}
