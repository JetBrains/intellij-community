/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.dbCommitted;

import com.intellij.openapi.vcs.changes.Change;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/10/12
 * Time: 5:24 PM
 */
public enum ChangeTypeEnum {
  ADD(0),
  DELETE(1),
  MODIFY(2),
  ADD_PLUS(3),
  MOVE(4),
  REPLACE(5);

  private final int myCode;

  private ChangeTypeEnum(int code) {
    myCode = code;
  }

  public int getCode() {
    return myCode;
  }

  public static ChangeTypeEnum getChangeType(final long type) {
    final ChangeTypeEnum[] values = values();
    for (ChangeTypeEnum value : values) {
      if (value.getCode() == type) {
        return value;
      }
    }
    return null;
  }

  public static ChangeTypeEnum getChangeType(final Change change) {
    if (change.getBeforeRevision() == null) {
      return ADD;
    }
    if (change.getAfterRevision() == null) {
      return DELETE;
    }
    if (change.isIsReplaced()) {
      return REPLACE;
    }
    if (change.isMoved() || change.isRenamed()) {
      return MOVE;
    }
    return MODIFY;
  }
}
