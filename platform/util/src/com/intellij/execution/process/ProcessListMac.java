/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.util.containers.ContainerUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Use through PlatformUtils.
 */
public class ProcessListMac implements IProcessList {
  public ProcessListMac() { }

  @Override
  public ProcessInfo[] getProcessList() {
    Process ps;
    try {
      String[] command = {"/bin/ps", "-a", "-x", "-o", "pid,command"};
      ps = ProcessUtils.createProcess(command, null, null);
    }
    catch (Exception e) {
      return ProcessInfo.EMPTY_ARRAY;
    }

    //Read the output and parse it into an array list
    List<ProcessInfo> procInfo = ContainerUtil.newArrayList();

    try {
      BufferedReader psOutput = new BufferedReader(new InputStreamReader(ps.getInputStream()));
      try {
        String line;
        while ((line = psOutput.readLine()) != null) {
          //The format of the output should be
          //PID space name
          line = line.trim();
          int index = line.indexOf(' ');
          if (index != -1) {
            String pidString = line.substring(0, index).trim();
            try {
              int pid = Integer.parseInt(pidString);
              String arg = line.substring(index + 1);
              procInfo.add(new ProcessInfo(pid, arg));
            }
            catch (NumberFormatException ignored) { }
          }
        }
      }
      finally {
        psOutput.close();
      }
    }
    catch (Exception ignored) { }

    ps.destroy();

    return procInfo.isEmpty() ? ProcessInfo.EMPTY_ARRAY : procInfo.toArray(new ProcessInfo[procInfo.size()]);
  }
}