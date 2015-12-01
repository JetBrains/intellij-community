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

/*******************************************************************************
 * Copyright (c) 2000, 2010 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     QNX Software Systems - Initial API and implementation
 *******************************************************************************/
package com.intellij.execution.process;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;


/**
 * Use through PlatformUtils.
 */
public class ProcessListLinux implements IProcessList {

  ProcessInfo[] empty = new ProcessInfo[0];

  public ProcessListLinux() {
  }

  /**
   * Insert the method's description here.
   *
   * @see IProcessList#getProcessList
   */
  public ProcessInfo[] getProcessList() {
    File proc = new File("/proc"); //$NON-NLS-1$
    File[] pidFiles = null;

    // We are only interested in the pid so filter the rest out.
    try {
      FilenameFilter filter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
          boolean isPID = false;
          try {
            Integer.parseInt(name);
            isPID = true;
          }
          catch (NumberFormatException e) {
          }
          return isPID;
        }
      };
      pidFiles = proc.listFiles(filter);
    }
    catch (SecurityException e) {
    }

    ProcessInfo[] processInfo = empty;
    if (pidFiles != null) {
      processInfo = new ProcessInfo[pidFiles.length];
      for (int i = 0; i < pidFiles.length; i++) {
        File cmdLine = new File(pidFiles[i], "cmdline"); //$NON-NLS-1$
        String name;
        try {
          name = new String(ProcessUtils.loadFileText(cmdLine, null)).replace('\0', ' ');
        }
        catch (IOException e) {
          name = "";
        }
        if (name.length() == 0) {
          name = "Unknown"; //$NON-NLS-1$
        }
        processInfo[i] = new ProcessInfo(pidFiles[i].getName(), name);
      }
    }
    else {
      pidFiles = new File[0];
    }
    return processInfo;
  }
}