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

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * @author spleaner
 */
public interface FoundationLibrary extends Library {
  void NSLog(Pointer pString, Object thing);

  Pointer objc_allocateClassPair(Pointer supercls, String name, int extraBytes);
  void objc_registerClassPair(Pointer cls);

  ID CFStringCreateWithCString(Pointer allocator, String string, int encoding);
  ID CFStringCreateWithBytes(Pointer allocator, byte[] bytes, int byteCount, int encoding, byte isExternalRepresentation);
  String CFStringGetCStringPtr(Pointer string, int encoding);
  byte CFStringGetCString(Pointer theString, byte[] buffer, int bufferSize, int encoding);
  int CFStringGetLength(Pointer theString);

  void CFRetain(Pointer cfTypeRef);
  void CFRelease(Pointer cfTypeRef);
  int CFGetRetainCount (Pointer cfTypeRef);

  Pointer objc_getClass(String className);
  Pointer class_createInstance(Pointer pClass, int extraBytes);
  Pointer sel_registerName(String selectorName);

  ID objc_msgSend(Pointer receiver, Pointer selector, Object... args);
  Structure objc_msgSend_stret(Pointer receiver, Pointer selector, Object... args);

  boolean class_respondsToSelector(Pointer cls, Pointer selName);

  boolean class_addMethod(Pointer cls, Pointer selName, Callback imp, String types);
  boolean class_replaceMethod(Pointer cls, Pointer selName, Callback imp, String types);

  Pointer objc_getClass(Pointer clazz);
}
