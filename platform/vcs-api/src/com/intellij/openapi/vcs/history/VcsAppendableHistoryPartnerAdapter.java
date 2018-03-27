/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.VcsException;

/**
 * @author irengrig
 */
public class VcsAppendableHistoryPartnerAdapter implements VcsAppendableHistorySessionPartner {
  private VcsAbstractHistorySession mySession;
  private VcsException myException;

  @Override
  public void reportCreatedEmptySession(VcsAbstractHistorySession session) {
    mySession = session;
  }

  @Override
  public void acceptRevision(VcsFileRevision revision) {
    mySession.appendRevision(revision);
  }

  @Override
  public void reportException(VcsException exception) {
    myException = exception;
  }

  @Override
  public void finished() {
  }

  @Override
  public void forceRefresh() {
  }

  @Override
  public void beforeRefresh() {
  }

  public void check() throws VcsException {
    if (myException != null) throw myException;
  }

  public VcsAbstractHistorySession getSession() {
    return mySession;
  }
}
