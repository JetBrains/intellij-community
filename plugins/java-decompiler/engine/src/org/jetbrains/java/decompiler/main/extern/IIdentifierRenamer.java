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
package org.jetbrains.java.decompiler.main.extern;

public interface IIdentifierRenamer {
  /**
   * Determines if the package should be renamed.
   * @param name the package name
   * @return true if the package should be renamed
   */
  boolean shouldRenamePackage(String name);

  boolean shouldRenameClass(String simpleName, String fullName);

  boolean shouldRenameField(String className, String field, String descriptor);

  boolean shouldRenameMethod(String className, String method, String descriptor);

  String getNextPackageName(String name);

  /**
   * Generates the next simple name for a class
   * @param simpleName the current simple name of the class
   * @param fullName the current full name of the class
   * @return a generated String that is a valid class name
   */
  String getNextClassName(String simpleName, String fullName);

  String getNextFieldName(String className, String field, String descriptor);

  String getNextMethodName(String className, String method, String descriptor);
}
