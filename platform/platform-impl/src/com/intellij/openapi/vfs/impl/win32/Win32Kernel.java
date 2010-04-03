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
package com.intellij.openapi.vfs.impl.win32;

import com.intellij.util.ArrayUtil;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class Win32Kernel {

  public static final int FILE_ATTRIBUTE_DIRECTORY = 0x0010;
  public static final int FILE_ATTRIBUTE_READONLY =  0x0001;

  private IdeaWin32 myKernel = new IdeaWin32();

  private Map<String, FileInfo> myCache = new HashMap<String, FileInfo>();

  void clearCache() {
    myCache.clear();
  }

  public String[] list(String absolutePath) {

    FileInfo[] fileInfos = myKernel.listChildren(absolutePath.replace('/', '\\') + "\\*.*");
    if (fileInfos == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    ArrayList<String> names = new ArrayList<String>(fileInfos.length);
    for (int i = 0, fileInfosLength = fileInfos.length; i < fileInfosLength; i++) {
      FileInfo info = fileInfos[i];
      if (info.name.equals(".")) {
        myCache.put(absolutePath, info);
        continue;
      }
      else if (info.name.equals("..")) {
        continue;
      }
      myCache.put(absolutePath + "/" + info.name, info);
      names.add(info.name);
    }

    return ArrayUtil.toStringArray(names);
  }

  public void exists(String path) throws FileNotFoundException {
      getInfo(path);
  }

  public boolean isDirectory(String path) throws FileNotFoundException {
    FileInfo data = getInfo(path);
    return (data.attributes & FILE_ATTRIBUTE_DIRECTORY) != 0;
  }

  public boolean isWritable(String path) throws FileNotFoundException {
    FileInfo fileInfo = getInfo(path);
    myCache.remove(path);
    return (fileInfo.attributes & FILE_ATTRIBUTE_READONLY) == 0;
  }

  public long getTimeStamp(String path) throws FileNotFoundException {
    long timestamp = getInfo(path).timestamp;
    return timestamp / 10000 - 11644473600000l;
  }

  private FileInfo getInfo(String path) throws FileNotFoundException {
    FileInfo info = myCache.get(path);
    if (info == null) {

      info = myKernel.getInfo(path.replace('/', '\\'));
      if (info == null) {
        throw new FileNotFoundException(path);
      }
      myCache.put(path, info);
    }
    return info;
  }
}
