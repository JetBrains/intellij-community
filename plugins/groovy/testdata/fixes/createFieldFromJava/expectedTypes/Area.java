// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import java.io.*;

class Test {
  Object foo(A a, File container) {
    if (a.p<caret>anel == null) {
      a.panel = new File();
      return new File(container, a.panel.getName());
    }
  }
}