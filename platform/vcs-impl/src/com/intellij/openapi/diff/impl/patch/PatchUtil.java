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
package com.intellij.openapi.diff.impl.patch;

public class PatchUtil {
  public final static int REGULAR_FILE_MODE = 100644;
  public final static int EXECUTABLE_FILE_MODE = 100755;
  @SuppressWarnings("unused")
  public final static int SYMBOLIC_LINK_MODE = 120000; //now we do not support such cases, but need to keep in mind
}
