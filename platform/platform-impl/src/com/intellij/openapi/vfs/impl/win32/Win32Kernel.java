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
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.examples.win32.W32API;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class Win32Kernel {

  private static final int MAX_PATH = 0x00000104;

  public static final int FILE_ATTRIBUTE_DIRECTORY = 0x00000010;
  public static final int FILE_ATTRIBUTE_READONLY = 0x0001;

  private static W32API.HANDLE INVALID_HANDLE_VALUE = new W32API.HANDLE(Pointer.createConstant(0xFFFFFFFFl));

  private static Kernel32 myKernel = (Kernel32)Native.loadLibrary("kernel32", Kernel32.class, new HashMap<Object, Object>() {
        {
          put(Library.OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
          put(Library.OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
        }});

  private final WIN32_FIND_DATA myData = new WIN32_FIND_DATA();

  void clearCache() {
    myCache.clear();
  }

  private static class FileInfo {
    private FileInfo(WIN32_FIND_DATA data) {
      this.dwFileAttributes = data.dwFileAttributes;
      this.ftLastWriteTime = data.ftLastWriteTime.toLong();
    }

    int dwFileAttributes;
    long ftLastWriteTime;
  }

  private Map<String, FileInfo> myCache = new HashMap<String, FileInfo>();

  public String[] list(String absolutePath) {

    ArrayList<String> list = new ArrayList<String>();
    WIN32_FIND_DATA data = myData;
    W32API.HANDLE hFind = myKernel.FindFirstFile(absolutePath.replace('/', '\\') + "\\*", data);
    if (hFind.equals(INVALID_HANDLE_VALUE)) return new String[0];
    try {
      do {
        String name = Native.toString(data.cFileName);
        if (name.equals(".")) {
          myCache.put(absolutePath, new FileInfo(data));
          continue;
        }
        else if (name.equals("..")) {
          continue;
        }
        myCache.put(absolutePath + "/" + name, new FileInfo(data));
        list.add(name);
      }
      while (myKernel.FindNextFile(hFind, data));
    }
    finally {
      myKernel.FindClose(hFind);
    }
    return ArrayUtil.toStringArray(list);
  }

  public boolean exists(String path) {
    try {
      getInfo(path);
      return true;
    }
    catch (FileNotFoundException e) {
      return false;
    }
  }

  public boolean isDirectory(String path) throws FileNotFoundException {
    FileInfo data = getInfo(path);
    return (data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0;
  }

  public boolean isWritable(String path) throws FileNotFoundException {
    FileInfo fileInfo = getInfo(path);
    myCache.remove(path);
    return (fileInfo.dwFileAttributes & FILE_ATTRIBUTE_READONLY) == 0;
  }

  public long getTimeStamp(String path) throws FileNotFoundException {
    return getInfo(path).ftLastWriteTime;
  }

  private FileInfo getInfo(String path) throws FileNotFoundException {
    FileInfo info = myCache.get(path);
    if (info == null) {
      WIN32_FIND_DATA data = myData;
      W32API.HANDLE handle = myKernel.FindFirstFile(path.replace('/', '\\'), data);
      if (handle.equals(INVALID_HANDLE_VALUE)) {
        throw new FileNotFoundException(path);
      }
      myKernel.FindClose(handle);
      info = new FileInfo(data);
      myCache.put(path, info);
    }
    return info;
  }

  public void release() throws Throwable {
    myData.release();
  }

  public interface Kernel32 extends StdCallLibrary {

    W32API.HANDLE FindFirstFile(String lpFileName, WIN32_FIND_DATA lpFindFileData);

    boolean FindNextFile(W32API.HANDLE hFindFile, WIN32_FIND_DATA lpFindFileData);

    boolean FindClose(W32API.HANDLE hFindFile);
  }

  public static class FILETIME extends Structure implements Structure.ByValue {

    public int dwLowDateTime;
    public int dwHighDateTime;

    private static long l(int i) {
      if (i >= 0) {
        return i;
      }
      else {
        return ((long)i & 0x7FFFFFFFl) + 0x80000000l;
      }
    }

    public long toLong() {
      return (((long)dwHighDateTime << 32) + l(dwLowDateTime)) / 10000 - 11644473600000l;
    }
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public static class WIN32_FIND_DATA extends Structure {

    public int dwFileAttributes;

    public FILETIME ftCreationTime;

    public FILETIME ftLastAccessTime;

    public FILETIME ftLastWriteTime;

    public int nFileSizeHigh;

    public int nFileSizeLow;

    public int dwReserved0;

    public int dwReserved1;

    public char[] cFileName = new char[MAX_PATH];

    public char[] cAlternateFileName = new char[14];

    public void release() throws Throwable {
      finalize();
    }
  }
}
