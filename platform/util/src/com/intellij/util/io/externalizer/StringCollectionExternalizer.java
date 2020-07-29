// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.externalizer;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;

public final class StringCollectionExternalizer<C extends Collection<String>> implements DataExternalizer<C> {
  @NotNull
  public static final StringCollectionExternalizer<List<String>> STRING_LIST_EXTERNALIZER = new StringCollectionExternalizer<>(ArrayList::new);

  private final @NotNull IntFunction<? extends C> myGenerator;

  public StringCollectionExternalizer(@NotNull IntFunction<? extends C> generator) {myGenerator = generator;}

  @Override
  public void save(@NotNull DataOutput out, C collection) throws IOException {
    IOUtil.writeStringList(out, collection);
  }

  @Override
  public C read(@NotNull DataInput in) throws IOException {
    return IOUtil.readStringCollection(in, myGenerator);
  }
}
