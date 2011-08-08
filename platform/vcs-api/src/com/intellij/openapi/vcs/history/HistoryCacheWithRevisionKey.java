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
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsKey;
import org.jetbrains.annotations.NotNull;

/**
* Created by IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 8/8/11
* Time: 7:01 PM
*/
public class HistoryCacheWithRevisionKey extends HistoryCacheBaseKey {
  private final VcsRevisionNumber myRevisionNumber;

  public HistoryCacheWithRevisionKey(FilePath filePath, VcsKey vcsKey, @NotNull VcsRevisionNumber revisionNumber) {
    super(filePath, vcsKey);
    myRevisionNumber = revisionNumber;
  }

  public VcsRevisionNumber getRevisionNumber() {
    return myRevisionNumber;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    HistoryCacheWithRevisionKey that = (HistoryCacheWithRevisionKey)o;

    if (!myRevisionNumber.equals(that.myRevisionNumber)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myRevisionNumber.hashCode();
    return result;
  }
}
