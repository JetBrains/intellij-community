/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.cvsoperations.cvsErrors.ErrorProcessor;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesListener;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.openapi.project.Project;
import org.netbeans.lib.cvsclient.ICvsCommandStopper;

/**
 * author: lesya
 */
public class CvsExecutionEnvironment {

  public final static ICvsCommandStopper DUMMY_STOPPER = new ICvsCommandStopper() {
    @Override
    public boolean isAborted() {
      return false;
    }

    @Override
    public boolean isAlive() {
      return true;
    }

    @Override
    public void resetAlive() {
    }
  };

  private final CvsMessagesListener myListener;
  private final ICvsCommandStopper myCommandStopper;
  private final ErrorProcessor myErrorProcessor;
  private final PostCvsActivity myPostCvsActivity;
  private final Project myProject;
  private ReadWriteStatistics myReadWriteStatistics;

  public CvsExecutionEnvironment(CvsMessagesListener listener,
                                 ICvsCommandStopper commandStopper,
                                 ErrorProcessor errorProcessor,
                                 PostCvsActivity postCvsActivity,
                                 Project project) {
    myListener = listener;
    myCommandStopper = commandStopper;
    myErrorProcessor = errorProcessor;
    myPostCvsActivity = postCvsActivity;
    myProject = project;
  }

  public CvsMessagesListener getCvsMessagesListener() {
    return myListener;
  }

  public ICvsCommandStopper getCvsCommandStopper() {
    return myCommandStopper;
  }

  public ErrorProcessor getErrorProcessor() { return myErrorProcessor; }

  public Project getProject() {
    return myProject;
  }

  public PostCvsActivity getPostCvsActivity() {
    return myPostCvsActivity;
  }

  public ReadWriteStatistics getReadWriteStatistics() {
    if (myReadWriteStatistics == null){
      myReadWriteStatistics = new ReadWriteStatistics();
    }
    return myReadWriteStatistics;
  }
}
