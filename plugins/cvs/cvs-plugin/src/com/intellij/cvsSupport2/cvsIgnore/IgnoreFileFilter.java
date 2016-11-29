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
package com.intellij.cvsSupport2.cvsIgnore;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.util.containers.HashMap;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.util.IIgnoreFileFilter;

import java.io.File;
import java.util.Map;

/**
 * author: lesya
 */

public class IgnoreFileFilter implements IIgnoreFileFilter{
  private final Map<File, IgnoredFilesInfo> myParentToFilterMap = new HashMap<>();

  public boolean shouldBeIgnored(AbstractFileObject abstractFileObject, ICvsFileSystem cvsFileSystem) {
    File file = cvsFileSystem.getLocalFileSystem().getFile(abstractFileObject);
    File parent = file.getParentFile();
    if (!myParentToFilterMap.containsKey(parent)){
      myParentToFilterMap.put(parent, IgnoredFilesInfoImpl.createForFile(new File(parent, CvsUtil.CVS_IGNORE_FILE)));
    }
    return myParentToFilterMap.get(parent).shouldBeIgnored(file.getName());
  }
}
