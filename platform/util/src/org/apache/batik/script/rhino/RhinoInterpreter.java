// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.apache.batik.script.rhino;

import org.apache.batik.script.ImportInfo;

import java.net.URL;

// A workaround for the class rename in Batik 1.10
public class RhinoInterpreter extends org.apache.batik.bridge.RhinoInterpreter {
  public RhinoInterpreter(URL documentURL) {
    super(documentURL);
  }

  public RhinoInterpreter(URL documentURL, ImportInfo imports) {
    super(documentURL, imports);
  }
}
