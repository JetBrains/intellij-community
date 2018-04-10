/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.process;

import com.intellij.openapi.util.Key;

public interface ProcessOutputTypes {
  Key SYSTEM = new ProcessOutputType("system");

  /**
   * Represents process standard output stream.<p>
   * Please note that stdout colored output type doesn't equal to this instance: use
   * <pre>{@code ProcessOutputType.isStdout(key)}</pre>
   * instead of
   * <pre>{@code ProcessOutputTypes.STDOUT.equals(key)} or ProcessOutputTypes.STDOUT == key</pre>
   */
  Key STDOUT = new ProcessOutputType("stdout");

  /**
   * Represents process standard error stream.<p>
   * Please note that stderr colored output type doesn't equal to this instance: use
   * <pre>{@code ProcessOutputType.isStderr(key)}</pre>
   * instead of
   * <pre>{@code ProcessOutputTypes.STDERR.equals(key) or ProcessOutputTypes.STDERR == key}</pre>
   */
  Key STDERR = new ProcessOutputType("stderr");
}
