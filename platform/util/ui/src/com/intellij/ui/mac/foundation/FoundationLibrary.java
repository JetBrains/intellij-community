// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  /**
   * Note: Vararg version. Should only be used only for selectors with a single fixed argument followed by varargs.
   */
  ID objc_msgSend(ID receiver, Pointer selector, Object firstArg, Object... args);
  double objc_msgSend_fpret(ID receiver, Pointer selector, Object... args); // the same as objc_msgSend but returns double (32-bit only)

  boolean class_respondsToSelector(ID cls, Pointer selName);
  boolean class_addMethod(ID cls, Pointer selName, Callback imp, String types);

  boolean class_addMethod(ID cls, Pointer selName, ID imp, String types);
  boolean class_addProtocol(ID aClass, ID protocol);

  boolean class_isMetaClass(ID cls);

  ID NSStringFromSelector(Pointer selector);
  ID NSStringFromClass(ID aClass);

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
