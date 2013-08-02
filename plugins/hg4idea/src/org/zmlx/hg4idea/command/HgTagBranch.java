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
package org.zmlx.hg4idea.command;

import com.intellij.openapi.util.text.StringUtil;
import org.zmlx.hg4idea.HgRevisionNumber;

public final class HgTagBranch {

  private static final int SPACINGAFTERFIRSTLETTER = 20;

  private final String name;
  private final String description;
  private final HgRevisionNumber head;
  private final String presentation;

  public HgTagBranch(String name, String description, HgRevisionNumber head) {
    this.name = name;
    this.description = description;
    this.head = head;
    int whitespaceNum = SPACINGAFTERFIRSTLETTER - name.length();
    String presentationName = whitespaceNum <= 0 ? name.substring(0, SPACINGAFTERFIRSTLETTER - 4).concat("...") : name;
    presentation = String.format("%s%s%s", presentationName, whitespaceNum > 0 ? StringUtil.repeatSymbol(' ', whitespaceNum) : " ", head);
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public HgRevisionNumber getHead() {
    return head;
  }

  @Override
  public String toString() {
    return presentation;
  }
}
