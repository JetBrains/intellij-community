/*******************************************************************************
 * Copyright (c) 2000, 2006 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     QNX Software Systems - Initial API and implementation
 *******************************************************************************/

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

import com.google.common.base.Joiner;

/**
 * @author traff
 */
public class ProcessInfo {

  private final int myPid;
  private final String myCommand;
  private final String myArgs;

  public ProcessInfo(String pidString, String name) {
    this(Integer.parseInt(pidString), name);
  }

  public ProcessInfo(int pid, String name) {
    myPid = pid;
    String[] args = name.split(" ");
    myCommand = args.length > 0 ? args[0] : "";
    myArgs = name;
  }

  public int getPid() {
    return myPid;
  }

  public String getCommand() {
    return myCommand;
  }

  @Override
  public String toString() {
    return Joiner.on("").join(String.valueOf(myPid), " (", myArgs, ")");
  }

  public String getArgs() {
    return myArgs;
  }
}