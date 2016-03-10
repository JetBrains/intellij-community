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
package org.jetbrains.java.decompiler.main.extern;

public interface IIdentifierRenamer {

  /**
   * Determines whether or not the specified class should be renamed
   */
  boolean shouldRenameClass(String simpleName, String fullName);

  /**
   * Determines whether or not the specified field should be renamed
   */
  boolean shouldRenameField(String owner, String name, String descriptor);

  /**
   * Determines whether or not the specified method should be renamed
   */
  boolean shouldRenameMethod(String owner, String name, String descriptor);

  /**
   * Generates the next simple name for a class
   * @param simpleName the current full name of the class
   * @param fullName the current simple name of the class
   * @return a generated String that is a valid class name
   */
  String getNextClassName(String simpleName, String fullName);

  /**
   * Generates the next name for a field
   * @param owner the fields owner
   * @param name the fields current name
   * @param descriptor the fields descriptor
   * @return a generated String that is a valid field name
   */
  String getNextFieldName(String owner, String name, String descriptor);

  /**
   * Generates the next name for a method
   * @param owner the methods owner
   * @param name the methods current name
   * @param descriptor the methods descriptor
   * @return a generated String that is a valid method name
   */
  String getNextMethodName(String owner, String name, String descriptor);
}