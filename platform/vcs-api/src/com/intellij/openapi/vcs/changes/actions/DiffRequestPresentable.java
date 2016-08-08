/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;

import java.util.ArrayList;
import java.util.List;

public interface DiffRequestPresentable {
  MyResult step(DiffChainContext context);

  void haveStuff() throws VcsException;
  List<? extends AnAction> createActions(final DiffExtendUIFactory uiFactory);
  String getPathPresentation();

  class MyResult {
    private final List<String> myErrors;
    private final DiffRequest myRequest;
    private final DiffPresentationReturnValue myReturnValue;

    public MyResult(DiffRequest request, DiffPresentationReturnValue returnValue) {
      myRequest = request;
      myReturnValue = returnValue;
      myErrors = new ArrayList<>();
    }

    public MyResult(DiffRequest request, DiffPresentationReturnValue returnValue, final String error) {
      myRequest = request;
      myReturnValue = returnValue;
      myErrors = new ArrayList<>();
      if (! StringUtil.isEmptyOrSpaces(error)) {
        myErrors.add(error);
      }
    }

    public void addError(final String e) {
      myErrors.add(e);
    }

    public List<String> getErrors() {
      return myErrors;
    }

    public DiffRequest getRequest() {
      return myRequest;
    }

    public DiffPresentationReturnValue getReturnValue() {
      return myReturnValue;
    }

    public boolean hasErrors() {
      return ! myErrors.isEmpty();
    }

    public String getAsOneError() {
      if (myErrors.isEmpty()) return null;
      return StringUtil.join(myErrors, "\n");
    }
  }
}
