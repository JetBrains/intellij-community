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

import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsCheckOut.CheckoutFilesOperation;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.CvsBundle;

/**
 * author: lesya
 */
public class CheckoutHandler extends CommandCvsHandler{
  public CheckoutHandler(FilePath[] files, CvsConfiguration configuration) {
    super(CvsBundle.message("operation.name.check.out.files"), new CheckoutFilesOperation(files, configuration), FileSetToBeUpdated.selectedFiles(files));
  }

}
