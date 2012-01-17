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
package com.intellij.ui.mac.foundation;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * @author spleaner
 */
public class Foundation {
  private static final FoundationLibrary myFoundationLibrary;

  static {
    // Set JNA to convert java.lang.String to char* using UTF-8, and match that with
    // the way we tell CF to interpret our char*
    // May be removed if we use toStringViaUTF16
    System.setProperty("jna.encoding", "UTF8");

    Map<String, Object> foundationOptions = new HashMap<String, Object>();
    //foundationOptions.put(Library.OPTION_TYPE_MAPPER, FoundationTypeMapper.INSTANCE);

    myFoundationLibrary = (FoundationLibrary)Native.loadLibrary("Foundation", FoundationLibrary.class, foundationOptions);
  }

  static Callback ourRunnableCallback;


  public static void init() { /* fake method to init foundation */ }

  private Foundation() {
  }

  /**
   * Get the ID of the NSClass with className
   */
  public static ID getObjcClass(String className) {
    return myFoundationLibrary.objc_getClass(className);
  }
  
  public static ID getProtocol(String name) {
    return myFoundationLibrary.objc_getProtocol(name);
  }

  public static Pointer createSelector(String s) {
    return myFoundationLibrary.sel_registerName(s);
  }

  public static ID invoke(final ID id, final Pointer selector, Object... args) {
    return myFoundationLibrary.objc_msgSend(id, selector, args);
  }

  public static ID invoke(final String cls, final String selector, Object... args) {
    return invoke(getObjcClass(cls), createSelector(selector), args);
  }

  public static ID invoke(final ID id, final String selector, Object... args) {
    return invoke(id, createSelector(selector), args);
  }

  public static ID allocateObjcClassPair(ID superCls, String name) {
    return myFoundationLibrary.objc_allocateClassPair(superCls, name, 0);
  }

  public static void registerObjcClassPair(ID cls) {
    myFoundationLibrary.objc_registerClassPair(cls);
  }

  public static boolean isClassRespondsToSelector(ID cls, Pointer selectorName) {
    return myFoundationLibrary.class_respondsToSelector(cls, selectorName);
  }

  public static boolean addMethod(ID cls, Pointer selectorName, Callback impl, String types) {
    return myFoundationLibrary.class_addMethod(cls, selectorName, impl, types);
  }
  
  public static boolean addProtocol(ID aClass, ID protocol) {
    return myFoundationLibrary.class_addProtocol(aClass, protocol);
  }

  public static boolean addMethodByID(ID cls, Pointer selectorName, ID impl, String types) {
    return myFoundationLibrary.class_addMethod(cls, selectorName, impl, types);
  }

  public static boolean isMetaClass(ID cls) {
    return myFoundationLibrary.class_isMetaClass(cls);
  }

  @Nullable
  public static String stringFromSelector(Pointer selector) {
    ID id = myFoundationLibrary.NSStringFromSelector(selector);
    if (id.intValue() > 0) {
      return toStringViaUTF8(id);
    }

    return null;
  }

  public static Pointer getClass(Pointer clazz) {
    return myFoundationLibrary.objc_getClass(clazz);
  }

  public static String fullUserName() {
    return toStringViaUTF8(myFoundationLibrary.NSFullUserName());
  }

  public static ID class_replaceMethod(ID cls, Pointer selector, Callback impl, String types) {
    return myFoundationLibrary.class_replaceMethod(cls, selector, impl, types);
  }

  public static ID getMetaClass(String className) {
    return myFoundationLibrary.objc_getMetaClass(className);
  }

  public static boolean isPackageAtPath(@NotNull final String path) {
    final ID workspace = invoke("NSWorkspace", "sharedWorkspace");
    final ID result = invoke(workspace, createSelector("isFilePackageAtPath:"), nsString(path));

    return result.intValue() == 1;
  }

  public static boolean isPackageAtPath(@NotNull final File file) {
    if (!file.isDirectory()) return false;
    return isPackageAtPath(file.getPath());
  }

  public static ID nsString(String s) {
    // Use a byte[] rather than letting jna do the String -> char* marshalling itself.
    // Turns out about 10% quicker for long strings.
    try {
      if (s.length() == 0) {
        return invoke("NSString", "string");
      }

      byte[] utf16Bytes = s.getBytes("UTF-16LE");
      return invoke(invoke("NSString", "alloc"), "initWithBytes:length:encoding:", utf16Bytes, utf16Bytes.length,
                    myFoundationLibrary.CFStringConvertEncodingToNSStringEncoding(FoundationLibrary.kCFStringEncodingUTF16LE));
    }
    catch (UnsupportedEncodingException x) {
      throw new RuntimeException(x);
    }
  }

  @Nullable
  public static String toStringViaUTF8(ID cfString) {
    if (cfString.intValue() == 0) return null;

    int lengthInChars = myFoundationLibrary.CFStringGetLength(cfString);
    int potentialLengthInBytes = 3 * lengthInChars + 1; // UTF8 fully escaped 16 bit chars, plus nul

    byte[] buffer = new byte[potentialLengthInBytes];
    byte ok = myFoundationLibrary.CFStringGetCString(cfString, buffer, buffer.length, FoundationLibrary.kCFStringEncodingUTF8);
    if (ok == 0) throw new RuntimeException("Could not convert string");
    return Native.toString(buffer);
  }

  @Nullable
  public static String getEncodingName(long nsStringEncoding) {
    int cfEncoding = myFoundationLibrary.CFStringConvertNSStringEncodingToEncoding(nsStringEncoding);
    ID pointer = myFoundationLibrary.CFStringConvertEncodingToIANACharSetName(cfEncoding);
    return toStringViaUTF8(pointer);
  }

  public static long getEncodingCode(@Nullable String encodingName) {
    if (StringUtil.isEmptyOrSpaces(encodingName)) return -1;

    ID converted = nsString(encodingName);
    int cfEncoding = myFoundationLibrary.CFStringConvertIANACharSetNameToEncoding(converted);
    if (cfEncoding == FoundationLibrary.kCFStringEncodingInvalidId) return -1;

    return myFoundationLibrary.CFStringConvertEncodingToNSStringEncoding(cfEncoding);
  }

  public static void cfRetain(ID id) {
    myFoundationLibrary.CFRetain(id);
  }

  public static void cfRelease(ID... id) {
    for (ID id1 : id) {
      myFoundationLibrary.CFRelease(id1);
    }
  }

  public static boolean isMainThread() {
    return invoke("NSThread", "isMainThread").intValue() > 0;
  }

  private static Map<String, RunnableInfo> ourMainThreadRunnables = new HashMap<String, RunnableInfo>();
  private static long ourCurrentRunnableCount = 0;
  private static final Object RUNNABLE_LOCK = new Object();

  static class RunnableInfo {
    RunnableInfo(Runnable runnable, boolean useAutoreleasePool) {
      myRunnable = runnable;
      myUseAutoreleasePool = useAutoreleasePool;
    }

    Runnable myRunnable;
    boolean myUseAutoreleasePool;
  }

  public static void executeOnMainThread(final Runnable runnable, final boolean withAutoreleasePool, final boolean waitUntilDone) {
    initRunnableSupport();

    synchronized (RUNNABLE_LOCK) {
      ourCurrentRunnableCount++;
      ourMainThreadRunnables.put(String.valueOf(ourCurrentRunnableCount), new RunnableInfo(runnable, withAutoreleasePool));
    }

    final ID ideaRunnable = getObjcClass("IdeaRunnable");
    final ID runnableObject = invoke(invoke(ideaRunnable, "alloc"), "init");
    invoke(runnableObject, "performSelectorOnMainThread:withObject:waitUntilDone:", createSelector("run:"),
           nsString(String.valueOf(ourCurrentRunnableCount)), Boolean.valueOf(waitUntilDone));
    invoke(runnableObject, "release");
  }

  private static void initRunnableSupport() {
    if (ourRunnableCallback == null) {
      final ID runnableClass = allocateObjcClassPair(getObjcClass("NSObject"), "IdeaRunnable");
      registerObjcClassPair(runnableClass);

      final Callback callback = new Callback() {
        public void callback(ID self, String selector, ID keyObject) {
          final String key = toStringViaUTF8(keyObject);


          RunnableInfo info;
          synchronized (RUNNABLE_LOCK) {
            info = ourMainThreadRunnables.remove(key);
          }

          if (info == null) return;


          ID pool = null;
          try {
            if (info.myUseAutoreleasePool) {
              pool = invoke("NSAutoreleasePool", "new");
            }

            info.myRunnable.run();
          }
          finally {
            if (pool != null) {
              invoke(pool, "release");
            }
          }
        }
      };
      if (!addMethod(runnableClass, createSelector("run:"), callback, "v@:*")) {
        throw new RuntimeException("Unable to add method to objective-c runnableClass class!");
      }
      ourRunnableCallback = callback;
    }
  }
  
  public static class NSDictionary {
    private ID myDelegate;
    
    public NSDictionary(ID delegate) {
      myDelegate = delegate;
    }
    
    public ID get(ID key) {
      return invoke(myDelegate, "objectForKey:", key);
    }
    
    public ID get(String key) {
      return get(nsString(key));
    }

    public int count() {
      return invoke(myDelegate, "count").intValue();
    }
  }

  public static class NSArray {
    private ID myDelegate;

    public NSArray(ID delegate) {
      myDelegate = delegate;
    }

    public int count() {
      return invoke(myDelegate, "count").intValue();
    }

    public ID at(int index) {
      return invoke(myDelegate, "objectAtIndex:", index);
    }
  }
  
  public static class NSAutoreleasePool {
    private ID myDelegate;

    public NSAutoreleasePool() {
      myDelegate = invoke(invoke("NSAutoreleasePool", "alloc"), "init");
    }

    public void drain() {
      invoke(myDelegate, "drain");
    }
  }
}
