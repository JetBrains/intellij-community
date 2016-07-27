/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.api;

import com.intellij.tasks.impl.gson.Mandatory;
import com.intellij.tasks.impl.gson.RestModel;
import org.jetbrains.annotations.NotNull;

@RestModel
@SuppressWarnings("UnusedDeclaration")
class GithubBranchRaw {
  @Mandatory private String name;

  @NotNull
  public String getName() {
    return name;
  }
}
