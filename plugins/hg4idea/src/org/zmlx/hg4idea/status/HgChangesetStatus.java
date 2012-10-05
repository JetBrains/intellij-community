// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.status;

import com.intellij.openapi.application.ApplicationManager;


public class HgChangesetStatus {

  private final String myName;
  private int numChanges;
  private String toolTip;

  public HgChangesetStatus(String name) {
    myName = name;
  }

  public void setChanges(final int count, final ChangesetWriter formatter) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (count == 0) {
          numChanges = 0;
          toolTip = "";
          return;
        }

        numChanges = count;
        toolTip = formatter.asString();
      }
    });
  }

  public String getStatusName() {
    return myName;
  }


  public int getNumChanges() {
    return numChanges;
  }


  public String getToolTip() {
    return toolTip;
  }


  public interface ChangesetWriter {
    String asString();
  }

  public void dispose() {
  }
}
