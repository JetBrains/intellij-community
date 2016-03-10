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

  boolean shouldRenamePackage(String name);

  boolean shouldRenameClass(String shortName, String fullName);

  boolean shouldRenameField(String className, String field, String descriptor);

  boolean shouldRenameMethod(String className, String method, String descriptor);

  String getNextPackageName(String name);

  String getNextClassName(String shortName, String fullName);

  String getNextFieldName(String className, String field, String descriptor);

  String getNextMethodName(String className, String method, String descriptor);
}
