/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class VoidDataExternalizer implements DataExternalizer<Void> {
  public static final VoidDataExternalizer INSTANCE = new VoidDataExternalizer();

  @Override
  public void save(@NotNull final DataOutput out, final Void value) throws IOException {
  }

  @Override
  @Nullable
  public Void read(@NotNull final DataInput in) throws IOException {
    return null;
  }
}
