// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct;

public interface IDecompiledData {

  String getClassEntryName(StructClass cl, String entryname);

  String getClassContent(StructClass cl);
}
