/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.util.ImageLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.ByteArrayInputStream;

import static com.intellij.ui.mac.foundation.Foundation.*;
import static com.intellij.ui.mac.foundation.FoundationLibrary.NSBitmapImageFileTypePNG;

public class NSWorkspace {
  @Nullable
  public static String absolutePathForAppBundleWithIdentifier(@NotNull String bundleID) {
    NSAutoreleasePool pool = new NSAutoreleasePool();
    try {
      ID workspace = getInstance();
      return toStringViaUTF8(invoke(workspace, "absolutePathForAppBundleWithIdentifier:",
                                                          nsString(bundleID)));
    }
    finally {
      pool.drain();
    }
  }

  @NotNull
  private static ID getInstance() {
    return invoke(getObjcClass("NSWorkspace"), "sharedWorkspace");
  }

  @Nullable
  public static Image imageForFileType(@NotNull String fileType) {
    NSAutoreleasePool pool = new NSAutoreleasePool();
    try {
      ID workspace = getInstance();
      ID image = invoke(workspace, "iconForFileType:", nsString(fileType));
      ID cgImage = invoke(image, "CGImageForProposedRect:context:hints:", ID.NIL, ID.NIL, ID.NIL);
      ID bitmapRepresentation = invoke(invoke(getObjcClass("NSBitmapImageRep"), "alloc"),
                                       "initWithCGImage:", cgImage);
      ID nsData = invoke(bitmapRepresentation, "representationUsingType:properties:", NSBitmapImageFileTypePNG, ID.NIL);
      if (isNil(nsData)) {
        return null;
      }

      byte[] imageBytes = new NSData(nsData).bytes();
      return ImageLoader.loadFromStream(new ByteArrayInputStream(imageBytes));
    }
    finally {
      pool.drain();
    }
  }
}
