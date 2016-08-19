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
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.Convertor;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class FindAllRootsHelper {
  private FindAllRootsHelper() {
  }

  public static List<VirtualFile> findVersionedUnder(final List<VirtualFile> coll) {
    final List<FilePath> pathList = ObjectsConvertor.vf2fp(coll);
    return impl(pathList.iterator());
  }

  public static FilePath[] findVersionedUnder(final FilePath[] roots) {
    final List<VirtualFile> found = impl(Arrays.asList(roots).iterator());
    return ObjectsConvertor.vf2fp(found).toArray(new FilePath[found.size()]);
  }

  private static List<VirtualFile> impl(final Iterator<FilePath> iterator) {
    final MyProcessor processor = new MyProcessor();

    for (; iterator.hasNext();) {
      final FilePath root = iterator.next();
      final VirtualFile vf = root.getVirtualFile();
      if (vf == null) continue;
      VfsUtil.processFilesRecursively(vf, processor, processor);
    }
    return processor.getFound();
  }

  private static class MyProcessor implements Processor<VirtualFile>, Convertor<VirtualFile, Boolean> {
    private final List<VirtualFile> myFound;

    private MyProcessor() {
      myFound = new LinkedList<>();
    }

    public Boolean convert(VirtualFile o) {
      return ! myFound.contains(o);
    }

    public boolean process(VirtualFile file) {
      if (CvsUtil.fileIsUnderCvsMaybeWithVfs(file)) {
        myFound.add(file);
      }
      return true;
    }

    public List<VirtualFile> getFound() {
      return myFound;
    }
  }
}
