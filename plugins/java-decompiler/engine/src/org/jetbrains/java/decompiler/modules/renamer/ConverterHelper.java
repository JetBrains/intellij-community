/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.modules.renamer;

import org.jetbrains.java.decompiler.code.CodeConstants;

public class ConverterHelper {

  // *****************************************************************************
  // static methods
  // *****************************************************************************

  public static String getSimpleClassName(String fullName) {
    return fullName.substring(fullName.lastIndexOf('/') + 1);
  }

  public static String replaceSimpleClassName(String fullName, String newName) {
    return getPackageName(fullName) + newName;
  }

  public static String getPackageName(String fullName) {
    return fullName.substring(0, fullName.lastIndexOf('/') + 1);
  }

  public static String getNextClassNamePrefix(int accessFlags) {
    if ((accessFlags & CodeConstants.ACC_ENUM) == CodeConstants.ACC_ENUM) {
      return "enum_";
    }
    //ACC_INTERFACE must be set whenever ACC_ANNOTATION is set, so we need to do the ACC_ANNOTATION check first
    else if ((accessFlags & CodeConstants.ACC_ANNOTATION) == CodeConstants.ACC_ANNOTATION) {
      return "annotation_";
    }
    else if ((accessFlags & CodeConstants.ACC_INTERFACE) == CodeConstants.ACC_INTERFACE) {
      return "interface_";
    }
    else {
      return "class_";
    }
  }
}