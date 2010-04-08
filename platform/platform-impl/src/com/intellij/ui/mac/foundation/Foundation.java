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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashMap;
import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * @author spleaner
 */
public class Foundation {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.mac.foundation.Foundation");

  private static final FoundationLibrary myFoundationLibrary;

  static {
    // Set JNA to convert java.lang.String to char* using UTF-8, and match that with
    // the way we tell CF to interpret our char*
    // May be removed if we use toStringViaUTF16
    System.setProperty("jna.encoding", "UTF8");

    Map<String, Object> foundationOptions = new HashMap<String, Object>();
    foundationOptions.put(Library.OPTION_TYPE_MAPPER, FoundationTypeMapper.INSTANCE);

    myFoundationLibrary = (FoundationLibrary)Native.loadLibrary("Foundation", FoundationLibrary.class, foundationOptions);
  }

  private Foundation() {
  }

  /**
   * Get the ID of the NSClass with className
   */
  public static Pointer getClass(String className) {
    return myFoundationLibrary.objc_getClass(className);
  }

  public static Pointer createSelector(String s) {
    return myFoundationLibrary.sel_registerName(s);
  }

  public static ID invoke(final Pointer id, final Pointer selector, Object... args) {
    return myFoundationLibrary.objc_msgSend(id, selector, args);
  }

  public static Pointer registerObjcClass(Pointer superCls, String name) {
    return myFoundationLibrary.objc_allocateClassPair(superCls, name, 0);
  }

  public static void registerObjcClassPair(Pointer cls) {
    myFoundationLibrary.objc_registerClassPair(cls);
  }

  public static boolean isClassRespondsToSelector(Pointer cls, Pointer selectorName) {
    return myFoundationLibrary.class_respondsToSelector(cls, selectorName);
  }

  public static Pointer createClassInstance(Pointer cls) {
    return myFoundationLibrary.class_createInstance(cls, 0);
  }

  public static boolean addMethod(Pointer cls, Pointer selectorName, Callback impl, String types) {
    return myFoundationLibrary.class_addMethod(cls, selectorName, impl, types);
  }

  public static Pointer getClass(Pointer clazz) {
    return myFoundationLibrary.objc_getClass(clazz);
  }

  /**
   * Return a CFString as an ID, toll-free bridged to NSString.
   * <p/>
   * Note that the returned string must be freed with {@link #cfRelease(ID)}.
   */
  public static ID cfString(String s) {
    // Use a byte[] rather than letting jna do the String -> char* marshalling itself.
    // Turns out about 10% quicker for long strings.
    try {
      byte[] utf16Bytes = s.getBytes("UTF-16LE");
      return myFoundationLibrary.CFStringCreateWithBytes(null, utf16Bytes, utf16Bytes.length, 0x14000100,
                                                         (byte)0); /* kTextEncodingUnicodeDefault + kUnicodeUTF16LEFormat */
    }
    catch (UnsupportedEncodingException x) {
      throw new RuntimeException(x);
    }
  }

  public static String toStringViaUTF8(Pointer cfString) {
    int lengthInChars = myFoundationLibrary.CFStringGetLength(cfString);
    int potentialLengthInBytes = 3 * lengthInChars + 1; // UTF8 fully escaped 16 bit chars, plus nul

    byte[] buffer = new byte[potentialLengthInBytes];
    byte ok = myFoundationLibrary.CFStringGetCString(cfString, buffer, buffer.length, 0x08000100);
    if (ok == 0) throw new RuntimeException("Could not convert string");
    return Native.toString(buffer);
  }

  public static void cfRetain(final Pointer id) {
    myFoundationLibrary.CFRetain(id);
  }

  public static void cfRelease(final Pointer id) {
    myFoundationLibrary.CFRelease(id);
  }
}
