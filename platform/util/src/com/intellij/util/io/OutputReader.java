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

import java.io.InputStream;
import java.nio.charset.Charset;

/** @deprecated use {@link BaseOutputReader}. to be removed in IDEA 2018.1 */
@Deprecated 
public abstract class OutputReader extends BaseOutputReader {
  /** @deprecated use {@link BaseOutputReader}. to be removed in IDEA 2018.1 */
  public OutputReader(@NotNull InputStream stream, @Nullable Charset charset, @NotNull String name) {
    super(stream, charset);
    start(name);
  }

  /** @deprecated use {@link BaseOutputReader}. to be removed in IDEA 2018.1 */
  public OutputReader(@NotNull InputStream stream, @Nullable Charset charset, @Nullable SleepingPolicy policy, @NotNull String name) {
    super(stream, charset, Options.withPolicy(policy));
    start(name);
  }

  /** @deprecated use {@link BaseOutputReader}. to be removed in IDEA 2018.1 */
  public void readFully() throws InterruptedException {
  }
}