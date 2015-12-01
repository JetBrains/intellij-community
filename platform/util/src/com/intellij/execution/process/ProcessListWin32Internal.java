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

import com.google.common.collect.Lists;

import java.io.*;
import java.util.List;

/*
 * This implementation uses a listtasks which is shipped together (so, it should always work on windows).
 * 
 * Use through PlatformUtils.
 */
public class ProcessListWin32Internal implements IProcessList {

  private ProcessInfo[] NOPROCESS = new ProcessInfo[0];
  private String myHelpersRoot;

  public ProcessListWin32Internal(String helpersRoot) {

    myHelpersRoot = helpersRoot;
  }

  public ProcessInfo[] getProcessList() {
    Process p = null;
    String command = null;
    InputStream in = null;
    ProcessInfo[] procInfos = NOPROCESS;

    try {
      File file = new File(myHelpersRoot, "process/listtasks.exe");


      //TODO: use listtasks.exe
      //IPath relative = new Path("com/jetbrains/python/internal/win32").addTrailingSeparator().append("listtasks.exe");
      //file = BundleUtils.getRelative(relative, bundle);

      if (file != null && file.exists()) {
        command = file.getCanonicalPath();
        if (command != null) {
          try {
            p = ProcessUtils.createProcess(new String[]{command}, null, null);
            in = p.getInputStream();
            InputStreamReader reader = new InputStreamReader(in);
            procInfos = parseListTasks(reader);
          }
          finally {
            if (in != null) {
              in.close();
            }
            if (p != null) {
              p.destroy();
            }
          }
        }
      }
    }
    catch (IOException e) {
    }
    return procInfos;
  }

  public ProcessInfo[] parseListTasks(InputStreamReader reader) {
    BufferedReader br = new BufferedReader(reader);
    List<ProcessInfo> processList = Lists.newArrayList();
    try {
      String line;
      while ((line = br.readLine()) != null) {
        int tab = line.indexOf('\t');
        if (tab != -1) {
          String proc = line.substring(0, tab).trim();
          String name = line.substring(tab).trim();
          if (proc.length() > 0 && name.length() > 0) {
            try {
              int pid = Integer.parseInt(proc);
              processList.add(new ProcessInfo(pid, name));
            }
            catch (NumberFormatException e) {
            }
          }
        }
      }
    }
    catch (IOException e) {
    }
    return processList.toArray(new ProcessInfo[processList.size()]);
  }
}