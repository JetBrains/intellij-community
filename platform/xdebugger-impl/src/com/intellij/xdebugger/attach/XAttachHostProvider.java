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
package com.intellij.xdebugger.attach;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * This interface describes the provider to enumerate different hosts/environments capable of attaching to with the debugger.
 * The examples are local machine environment (see {@link LocalAttachHost}) or SSH connection to a remote server.
 */
@ApiStatus.Experimental
public interface XAttachHostProvider<T extends XAttachHost> {
  ExtensionPointName<XAttachHostProvider> EP = ExtensionPointName.create("com.intellij.xdebugger.attachHostProvider");

  /**
   * @return the group to which all connections provided by this provider belong
   */
  @NotNull
  XAttachPresentationGroup<? extends XAttachHost> getPresentationGroup();

  /**
   * @return a list of connections of this type, which is characterized by the provider
   */
  @NotNull
  List<T> getAvailableHosts(@Nullable Project project);

}
