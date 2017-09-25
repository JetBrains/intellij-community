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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.remote.RemoteSdkCredentials;
import com.intellij.remote.RemoteSdkException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface XRemoteProcessListProvider {
  ExtensionPointName<XRemoteProcessListProvider> EP = ExtensionPointName.create("com.intellij.xdebugger.remoteProcessListProvider");

  /**
   * @return the group to which all connections provided by this provider belong
   */
  XAttachGroup<RemoteSettings> getAttachGroup();

  /**
   * @param settings specifies the connection from which you want to get the list of processes
   * @return a list of remote processes
   */
  List<ProcessInfo> getProcessList(Project project, RemoteSettings settings);

  String getName();

  /**
   * @return a list of connections of this type
   */
  List<RemoteSettings> getSettingsList();

  int getId();

  /**
   * @param data    sets the connection on which you want to run the command
   * @param command you want to execute
   * @return output of the corresponding process
   */
  @NotNull
  ProcessOutput execAndGetOutput(@Nullable Project project, @NotNull RemoteSdkCredentials data,
                                 @NonNls GeneralCommandLine command) throws RemoteSdkException;
}
