/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;

import java.util.ArrayList;
import java.util.List;

public class FindAllRootsHelper {
  private FindAllRootsHelper() {
  }

  public static FilePath[] findVersionedUnder(final FilePath[] roots) {
    final List<FilePath> result = new ArrayList<FilePath>();

    final Processor<VirtualFile> processor = new Processor<VirtualFile>() {
      public boolean process(VirtualFile file) {
        final boolean underCvs = CvsUtil.fileIsUnderCvs(file);
        if (underCvs) {
          result.add(new FilePathImpl(file));
        }
        return ! underCvs;
      }
    };

    for (FilePath root : roots) {
      final VirtualFile vf = root.getVirtualFile();
      if (vf == null) continue;
      VfsUtil.processFilesRecursively(vf, processor);
    }
    return result.toArray(new FilePath[result.size()]);
  }
}
