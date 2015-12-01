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
 * Copyright (c) 2005, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.intellij.execution.process;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

import com.google.common.collect.Lists;

/**
 * Use through PlatformUtils.
 */
public class ProcessListMac implements IProcessList {

    ProcessInfo[] empty = new ProcessInfo[0];

    public ProcessListMac() {
    }

    /**
     * Insert the method's description here.
     * @see IProcessList#getProcessList
     */
    public ProcessInfo[] getProcessList() {
        Process ps;
        BufferedReader psOutput;
        String[] args = { "/bin/ps", "-a", "-x", "-o", "pid,command" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ 

        try {
            ps = ProcessUtils.createProcess(args, null, null);
            psOutput = new BufferedReader(new InputStreamReader(ps.getInputStream()));
        } catch (Exception e) {
            return new ProcessInfo[0];
        }

        //Read the output and parse it into an array list
        List<ProcessInfo> procInfo = Lists.newArrayList();

        try {
            String lastline;
            while ((lastline = psOutput.readLine()) != null) {
                //The format of the output should be 
                //PID space name
                lastline = lastline.trim();
                int index = lastline.indexOf(' ');
                if (index != -1) {
                    String pidString = lastline.substring(0, index).trim();
                    try {
                        int pid = Integer.parseInt(pidString);
                        String arg = lastline.substring(index + 1);
                        procInfo.add(new ProcessInfo(pid, arg));
                    } catch (NumberFormatException e) {
                    }
                }
            }
            psOutput.close();
        } catch (Exception e) {
            /* Ignore */
        }

        ps.destroy();
        return procInfo.toArray(new ProcessInfo[procInfo.size()]);
    }
}