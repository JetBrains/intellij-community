// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.foundation;

public interface FoundationLibrary {

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
