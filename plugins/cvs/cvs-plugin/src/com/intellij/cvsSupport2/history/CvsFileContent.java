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
package com.intellij.cvsSupport2.history;

import com.intellij.CvsBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileContent;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class CvsFileContent implements VcsFileContent{
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.history.CvsFileContent");
  protected final ComparableVcsRevisionOnOperation myComparableCvsRevisionOnOperation;

  protected CvsFileContent(final ComparableVcsRevisionOnOperation comparableCvsRevisionOnOperation) {
    myComparableCvsRevisionOnOperation = comparableCvsRevisionOnOperation;
  }

  public boolean isDeleted() {
    return myComparableCvsRevisionOnOperation.isDeleted();
  }

  public boolean isLoaded() {
    return myComparableCvsRevisionOnOperation.isLoaded();
  }

  @Nullable
  public byte[] getContent() throws IOException, VcsException {
    if (! isLoaded()) return null;
    return myComparableCvsRevisionOnOperation.getContent();
  }

  public abstract VcsRevisionNumber getRevisionNumber();

  public byte[] loadContent() throws IOException, VcsException {
    myComparableCvsRevisionOnOperation.loadContent();
    if (!isLoaded()) {
      throw new VcsException(CvsBundle.message("exception.text.cannot.load.revision", getRevisionNumber()));
    }
    if (fileNotFound()) {
      throw new VcsException(CvsBundle.message("exception.text.cannot.find.revision", getRevisionNumber()));
    }

    return myComparableCvsRevisionOnOperation.getContent();
  }

  public boolean fileNotFound() {
    return myComparableCvsRevisionOnOperation.fileNotFound();
  }
}
