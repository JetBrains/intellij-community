/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/**
 * @author spleaner
 */
public interface FoundationLibrary extends Library {
  void NSLog(Pointer pString, Object thing);

  ID NSFullUserName();

  ID objc_allocateClassPair(ID supercls, String name, int extraBytes);
  void objc_registerClassPair(ID cls);

  ID CFStringCreateWithBytes(Pointer allocator, byte[] bytes, int byteCount, int encoding, byte isExternalRepresentation);
  byte CFStringGetCString(ID theString, byte[] buffer, int bufferSize, int encoding);
  int CFStringGetLength(ID theString);

  long CFStringConvertNSStringEncodingToEncoding(long nsEncoding);
  ID CFStringConvertEncodingToIANACharSetName(long cfEncoding);

  long CFStringConvertIANACharSetNameToEncoding(ID encodingName);
  long CFStringConvertEncodingToNSStringEncoding(long cfEncoding);
  
  ID CGWindowListCreateImage(Foundation.NSRect screenBounds, int windowOption, ID windowID, int imageOption);

  void CFRetain(ID cfTypeRef);
  void CFRelease(ID cfTypeRef);
  int CFGetRetainCount (Pointer cfTypeRef);

  ID objc_getClass(String className);
  ID objc_getProtocol(String name);

  ID class_createInstance(ID pClass, int extraBytes);
  Pointer sel_registerName(String selectorName);

  ID class_replaceMethod(ID cls, Pointer selName, Callback impl, String types);

  ID objc_getMetaClass(String name);

  ID objc_msgSend(ID receiver, Pointer selector, Object... args);
  double objc_msgSend_fpret(ID receiver, Pointer selector, Object... args); // the same as objc_msgSend but returns double

  boolean class_respondsToSelector(ID cls, Pointer selName);
  boolean class_addMethod(ID cls, Pointer selName, Callback imp, String types);

  boolean class_addMethod(ID cls, Pointer selName, ID imp, String types);
  boolean class_addProtocol(ID aClass, ID protocol);

  boolean class_isMetaClass(ID cls);

  ID NSStringFromSelector(Pointer selector);

  Pointer objc_getClass(Pointer clazz);

  int kCFStringEncodingMacRoman = 0;
  int kCFStringEncodingWindowsLatin1 = 0x0500;
  int kCFStringEncodingISOLatin1 = 0x0201;
  int kCFStringEncodingNextStepLatin = 0x0B01;
  int kCFStringEncodingASCII = 0x0600;
  int kCFStringEncodingUnicode = 0x0100;
  int kCFStringEncodingUTF8 = 0x08000100;
  int kCFStringEncodingNonLossyASCII = 0x0BFF;

  int kCFStringEncodingUTF16 = 0x0100;
  int kCFStringEncodingUTF16BE = 0x10000100;
  int kCFStringEncodingUTF16LE = 0x14000100;
  int kCFStringEncodingUTF32 = 0x0c000100;
  int kCFStringEncodingUTF32BE = 0x18000100;
  int kCFStringEncodingUTF32LE = 0x1c000100;
  
  // https://developer.apple.com/library/mac/documentation/Carbon/Reference/CGWindow_Reference/Constants/Constants.html#//apple_ref/doc/constant_group/Window_List_Option_Constants
  int kCGWindowListOptionAll                 = 0;
  int kCGWindowListOptionOnScreenOnly        = 1;
  int kCGWindowListOptionOnScreenAboveWindow = 2;
  int kCGWindowListOptionOnScreenBelowWindow = 4;
  int kCGWindowListOptionIncludingWindow     = 8;
  int kCGWindowListExcludeDesktopElements    = 16;
  
  //https://developer.apple.com/library/mac/documentation/Carbon/Reference/CGWindow_Reference/Constants/Constants.html#//apple_ref/doc/constant_group/Window_Image_Types
  int kCGWindowImageDefault             = 0;
  int kCGWindowImageBoundsIgnoreFraming = 1;
  int kCGWindowImageShouldBeOpaque      = 2;
  int kCGWindowImageOnlyShadows         = 4;
  int kCGWindowImageBestResolution      = 8;
  int kCGWindowImageNominalResolution   = 16;


  // see enum NSBitmapImageFileType
  int NSBitmapImageFileTypeTIFF = 0;
  int NSBitmapImageFileTypeBMP = 1;
  int NSBitmapImageFileTypeGIF = 2;
  int NSBitmapImageFileTypeJPEG = 3;
  int NSBitmapImageFileTypePNG = 4;
  int NSBitmapImageFileTypeJPEG2000 = 5;
}
