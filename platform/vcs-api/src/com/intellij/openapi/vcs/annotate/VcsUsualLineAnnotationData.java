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
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;

/**
 * @author irengrig
 *         Date: 3/10/11
 *         Time: 4:14 PM
 */
public class VcsUsualLineAnnotationData implements VcsLineAnnotationData {
  private final VcsRevisionNumber[] myData;

  public VcsUsualLineAnnotationData(int size) {
    assert size > 0;
    myData = new VcsRevisionNumber[size];
  }

  public void put(final int lineNumber, final VcsRevisionNumber revisionNumber) {
    assert lineNumber >= 0 && myData.length > lineNumber;
    myData[lineNumber] = revisionNumber;
  }

  @Override
  public int getNumLines() {
    return myData.length;
  }

  @Override
  public VcsRevisionNumber getRevision(int lineNumber) {
    assert lineNumber >= 0 && myData.length > lineNumber;
    return myData[lineNumber];
  }
}
