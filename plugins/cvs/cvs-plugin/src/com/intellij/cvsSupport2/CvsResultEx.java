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
package com.intellij.cvsSupport2;

import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.vcs.VcsException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * author: lesya
 */
public class CvsResultEx implements CvsResult {
  private final List<VcsException> myErrors = new ArrayList<>();
  private boolean myIsCanceled = false;

  @Override
  public void setIsCanceled() {
    myIsCanceled = true;
  }

  @Override
  public List<VcsException> getErrors() {
    final ArrayList<VcsException> errors = new ArrayList<>();
    for (VcsException error : myErrors) {
      if (!error.isWarning()) {
        errors.add(error);
      }
    }
    return errors;
  }

  public List<VcsException> getWarnings() {
    final ArrayList<VcsException> warnings = new ArrayList<>();
    for (VcsException error : myErrors) {
      if (error.isWarning()) {
        warnings.add(error);
      }
    }
    return warnings;
  }

  @Override
  public boolean isCanceled() {
    return myIsCanceled;
  }

  @Override
  public void addAllErrors(Collection<VcsException> errors) {
    myErrors.addAll(errors);
  }

  @Override
  public boolean hasErrors() {
    for (VcsException error : myErrors) {
      if (!error.isWarning()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public VcsException composeError() {
    return myErrors.iterator().next();
  }

  @Override
  public void addError(VcsException error) {
    myErrors.add(error);
  }

  @Override
  public List<VcsException> getErrorsAndWarnings() {
    return myErrors;
  }

  public boolean finishedUnsuccessfully(CvsHandler handler) {
    checkIsCanceled(handler);
    if (hasErrors()) return true;
    if (isCanceled()) return true;
    return false;
  }

  private void checkIsCanceled(CvsHandler handler) {
    if (handler.isCanceled()) setIsCanceled();
  }
}
