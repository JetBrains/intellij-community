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
package com.intellij.cvsSupport2.checkinProject;

import com.intellij.openapi.vfs.*;
import org.netbeans.lib.cvsclient.admin.*;

/**
 * author: lesya
 */
public class VirtualFileEntry {
  private final VirtualFile myVirtualFile;
  private final Entry myEntry;

  public VirtualFileEntry(VirtualFile virtualFile, Entry entry) {
    myVirtualFile = virtualFile;
    myEntry = entry;
  }

  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public Entry getEntry() {
    return myEntry;
  }
}
