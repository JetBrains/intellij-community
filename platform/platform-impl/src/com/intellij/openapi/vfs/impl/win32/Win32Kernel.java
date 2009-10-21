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

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class Win32Kernel {

  private static Kernel32 myKernel = (Kernel32)Native.loadLibrary("kernel32", Kernel32.class, new HashMap<Object, Object>() {
    {
      put(Library.OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
      put(Library.OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
    }});

  private static final int MAX_PATH = 0x00000104;
  public static final int FILE_ATTRIBUTE_DIRECTORY = 0x00000010;
  public static final int FILE_ATTRIBUTE_READONLY = 0x0001;

  private static HANDLE INVALID_HANDLE_VALUE = new HANDLE() {
    {
      super.setPointer(Pointer.createConstant(-1));
    }};

  public static class HANDLE extends PointerType {
    public Object fromNative(Object nativeValue, FromNativeContext context) {
      Object o = super.fromNative(nativeValue, context);
      if (INVALID_HANDLE_VALUE.equals(o)) return INVALID_HANDLE_VALUE;
      return o;
    }
  }

  private static int DATA_SIZE = new WIN32_FIND_DATA().size();

  private Map<String, WIN32_FIND_DATA> myCache = new HashMap<String, WIN32_FIND_DATA>();
  private List<WIN32_FIND_DATA> myDatas = new ArrayList<WIN32_FIND_DATA>();

  private WIN32_FIND_DATA getData() {
    if (myDatas.isEmpty()) {
      myDatas.add(new WIN32_FIND_DATA());
    }
    return myDatas.remove(0);
  }

  public String[] list(String absolutePath) {

    myDatas.addAll(myCache.values());
    myCache.clear();

    ArrayList<String> list = new ArrayList<String>();
    WIN32_FIND_DATA data = getData();
    HANDLE hFind = myKernel.FindFirstFile(absolutePath.replace('/', '\\') + "\\*", data);
    if (hFind == INVALID_HANDLE_VALUE) return new String[0];
    do {
      String name = toString(data.cFileName);
      if (name.equals(".") || name.equals("..")) {
        continue;
      }
      myCache.put(absolutePath + "/" + name, data);
      list.add(name);
      data = getData();
    }
    while (myKernel.FindNextFile(hFind, data));
    myKernel.FindClose(hFind);
    return list.toArray(new String[list.size()]);
  }

  public boolean isDirectory(String path) throws NotAvailableException {
    WIN32_FIND_DATA data = getData(path);
    return (data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0;
  }

  public boolean isWritable(String path) throws NotAvailableException {
    return (getData(path).dwFileAttributes & FILE_ATTRIBUTE_READONLY) == 0;
  }


  public long getTimeStamp(String path) throws NotAvailableException {
    return getData(path).ftLastWriteTime.toLong();
  }

  private WIN32_FIND_DATA getData(String path) throws NotAvailableException {
    WIN32_FIND_DATA data = myCache.get(path);
    if (data == null) {
      throw new NotAvailableException(path);
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

    HANDLE FindFirstFile(String lpFileName, WIN32_FIND_DATA lpFindFileData);

    boolean FindNextFile(HANDLE hFindFile, WIN32_FIND_DATA lpFindFileData);

    boolean FindClose(HANDLE hFindFile);

    int GetLastError();
  }

  public static class FILETIME extends Structure implements Structure.ByValue {
    
    public int dwLowDateTime;
    public int dwHighDateTime;

    private static long l(int i) {
        if (i >= 0) {
            return i;
        } else {
            return ((long) i & 0x7FFFFFFFl) + 0x80000000l;
        }
    }

    public long toLong() {
      long result = dwHighDateTime;
      result = result << 32;
      result = result + l(dwLowDateTime);
      result = result / 10000;
      result = result - 11644473600000l;
      return result;
    }
  }

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
  
  public static class NotAvailableException extends Exception {
    public NotAvailableException(String message) {
      super(message);
    }
  }
}
