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
package com.intellij.cvsSupport2.javacvsImpl;

import com.intellij.util.io.ReadOnlyAttributeUtil;
import org.netbeans.lib.cvsclient.file.IFileReadOnlyHandler;

import java.io.File;
import java.io.IOException;

/**
 * author: lesya
 */
public class FileReadOnlyHandler implements IFileReadOnlyHandler{
  @Override
  public void setFileReadOnly(File file, boolean readOnly) throws IOException {
    if (file.canWrite() != readOnly) return;
    ReadOnlyAttributeUtil.setReadOnlyAttribute(file.getAbsolutePath(), readOnly);
  }
}
