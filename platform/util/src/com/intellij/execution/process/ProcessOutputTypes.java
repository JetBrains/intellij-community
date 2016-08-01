/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;

import java.util.Set;

public interface ProcessOutputTypes {
  String SYSTEM_STRING = "system";
  String STDOUT_STRING = "stdout";
  String STDERR_STRING = "stderr";
  
  Key SYSTEM = new Key(SYSTEM_STRING);
  Key STDOUT = new Key(STDOUT_STRING);
  Key STDERR = new Key(STDERR_STRING);

  Set<Key> TYPES = ContainerUtil.newHashSet(SYSTEM, STDOUT, STDERR);
}
