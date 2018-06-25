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
package com.intellij.xdebugger.attach;

import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use XAttachProcessPresentationGroup (will be removed in 2018.2)
 */
@Deprecated
@ApiStatus.Experimental
public interface XLocalAttachGroup extends XAttachProcessPresentationGroup {
  /**
   * @deprecated will be removed in 2018.2
   */
  @Deprecated @NotNull
  XLocalAttachGroup DEFAULT = new XDefaultLocalAttachGroup();

  /**
   * @deprecated use {@link #compare(Object, Object)} (will be removed in 2018.2)
   */
  @Deprecated
  default int compare(@NotNull Project project, @NotNull ProcessInfo a, @NotNull ProcessInfo b, @NotNull UserDataHolder dataHolder) {
    return compare(a, b);
  }
}
