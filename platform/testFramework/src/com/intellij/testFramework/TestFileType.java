/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.testFramework;

/**
 * Encapsulates information about target test file type in order to reduce possibility of mis-typing.
 * 
 * @author Denis Zhdanov
 * @since 11/19/10 11:31 AM
 */
public enum TestFileType {

  JAVA("java"), SQL("sql"), TEXT("txt"), XML("xml"), HTML("html"), XHTML("xhtml"), JS("js");

  private final String myExtension;

  TestFileType(String extension) {
    myExtension = "." + extension;
  }

  public String getExtension() {
    return myExtension;
  }
}
