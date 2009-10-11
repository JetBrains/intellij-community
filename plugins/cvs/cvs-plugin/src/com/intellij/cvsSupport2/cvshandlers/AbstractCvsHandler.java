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
package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesListener;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsCompositeListener;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsCompositeListener;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesListener;

/**
 * author: lesya
 */
public abstract class AbstractCvsHandler extends CvsHandler {
  protected final CvsCompositeListener myCompositeListener = new CvsCompositeListener();

  public AbstractCvsHandler(String title, FileSetToBeUpdated files) {
    super(title, files);
  }

  public void addCvsListener(CvsMessagesListener listener) {
    myCompositeListener.addCvsListener(listener);
  }

  public void removeCvsListener(CvsMessagesListener listener) {
    myCompositeListener.removeCvsListener(listener);
  }
}
