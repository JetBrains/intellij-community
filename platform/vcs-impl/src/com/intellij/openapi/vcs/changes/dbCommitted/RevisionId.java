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

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/18/12
 * Time: 3:48 PM
 */
public class RevisionId {
  public static final long ourFake = -1;
  public static final RevisionId FAKE = new RevisionId(-1, -1);

  private final long myNumber;
  private final long myTime;

  public static RevisionId createNumber(final long number) {
    return new RevisionId(number, -1);
  }

  public static RevisionId createTime(final long time) {
    return new RevisionId(-1, time);
  }

  public RevisionId(long number, long time) {
    myNumber = number;
    myTime = time;
  }

  public long getNumber() {
    return myNumber;
  }

  public long getTime() {
    return myTime;
  }

  public boolean isNumberFake() {
    return ourFake == myNumber;
  }

  public boolean isFake() {
    return ourFake == myNumber && ourFake == myTime;
  }
}
