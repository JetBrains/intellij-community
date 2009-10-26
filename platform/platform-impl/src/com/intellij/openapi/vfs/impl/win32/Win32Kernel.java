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

  private static Kernel32 ourKernel;

  private static final int MAX_PATH = 0x00000104;
  public static final int FILE_ATTRIBUTE_DIRECTORY = 0x00000010;
  public static final int FILE_ATTRIBUTE_READONLY = 0x0001;

  private synchronized static Kernel32 getKernel() {
    if (ourKernel == null) {
      ourKernel = (Kernel32)Native.loadLibrary("kernel32", Kernel32.class, new HashMap<Object, Object>() {
        {
          put(Library.OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
          put(Library.OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
        }});
    }
    return ourKernel;
  }

  private static class FileInfo {
    private FileInfo(WIN32_FIND_DATA data) {
      this.dwFileAttributes = data.dwFileAttributes;
      this.ftLastWriteTime = data.ftLastWriteTime.toLong();
    }

    private FileInfo(BY_HANDLE_FILE_INFORMATION information) {
      this.dwFileAttributes = information.dwFileAttributes;
      this.ftLastWriteTime = information.ftLastWriteTime.toLong();
    }

    int dwFileAttributes;
    long ftLastWriteTime;
  }

  private static W32API.HANDLE INVALID_HANDLE_VALUE = new W32API.HANDLE(Pointer.createConstant(0xFFFFFFFFl));
  private static WIN32_FIND_DATA DATA = new WIN32_FIND_DATA();

  public static void release() {
    DATA = null;
  }

  private Map<String, FileInfo> myCache = new HashMap<String, FileInfo>();

  public String[] list(String absolutePath) {

    myCache.clear();

    ArrayList<String> list = new ArrayList<String>();
    W32API.HANDLE hFind = getKernel().FindFirstFile(absolutePath.replace('/', '\\') + "\\*", DATA);
    if (hFind.equals(INVALID_HANDLE_VALUE)) return new String[0];
    do {
      String name = toString(DATA.cFileName);
      if (name.equals(".")) {
        myCache.put(absolutePath, new FileInfo(DATA));
        continue;
      }
      else if (name.equals("..")) {
        continue;
      }
      myCache.put(absolutePath + "/" + name, new FileInfo(DATA));
      list.add(name);
    }
    while (getKernel().FindNextFile(hFind, DATA));
    getKernel().FindClose(hFind);
    return list.toArray(new String[list.size()]);
  }

  public boolean exists(String path) {
    myCache.clear();
    try {
      getData(path);
      return true;
    }
    catch (FileNotFoundException e) {
      return false;
    }
  }

  public boolean isDirectory(String path) throws FileNotFoundException {
    FileInfo data = getData(path);
    return (data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0;
  }

  public boolean isWritable(String path) throws FileNotFoundException {
    return (getData(path).dwFileAttributes & FILE_ATTRIBUTE_READONLY) == 0;
  }

  public long getTimeStamp(String path) throws FileNotFoundException {
    return getData(path).ftLastWriteTime;
  }

  private FileInfo getData(String path) throws FileNotFoundException {
    FileInfo data = myCache.get(path);
    if (data == null) {
      myCache.clear();
      if (!getKernel().GetFileAttributesEx(path.replace('/', '\\'), 0, BY_HANDLE_FILE_INFORMATION.INSTANCE)) {
        throw new FileNotFoundException(path);
      }
      data = new FileInfo(BY_HANDLE_FILE_INFORMATION.INSTANCE);
      myCache.put(path, data);
    }
    return data;
  }

  private static String toString(char[] array) {
    for (int i = 0; i < array.length; i++) {
      if (array[i] == 0) return new String(array, 0, i);
    }
    return new String(array);
  }

  public interface Kernel32 extends StdCallLibrary {

    W32API.HANDLE FindFirstFile(String lpFileName, WIN32_FIND_DATA lpFindFileData);

    boolean GetFileAttributesEx(String lpFileName, int level, BY_HANDLE_FILE_INFORMATION lpFileInformation);

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
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public static class BY_HANDLE_FILE_INFORMATION extends Structure {

    public static BY_HANDLE_FILE_INFORMATION INSTANCE = new BY_HANDLE_FILE_INFORMATION();

    public int dwFileAttributes;
    public FILETIME ftCreationTime;
    public FILETIME ftLastAccessTime;
    public FILETIME ftLastWriteTime;
    public int nFileSizeHigh;
    public int nFileSizeLow;
  }
}
