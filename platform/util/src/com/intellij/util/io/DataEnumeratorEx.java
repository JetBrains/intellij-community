// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import java.io.IOException;

public interface DataEnumeratorEx<Data> extends DataEnumerator<Data> {
  int tryEnumerate(Data name) throws IOException;
}
