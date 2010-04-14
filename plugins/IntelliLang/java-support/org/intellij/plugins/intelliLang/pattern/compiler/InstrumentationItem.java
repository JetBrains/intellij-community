/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.pattern.compiler;

import com.intellij.openapi.compiler.FileProcessingCompiler;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

class InstrumentationItem implements FileProcessingCompiler.ProcessingItem {

  private final VirtualFile myClassFile;
  private final boolean myJDK6;

  public InstrumentationItem(@NotNull VirtualFile classFile, boolean jdk6) {
    myJDK6 = jdk6;
    myClassFile = classFile;
  }

  @NotNull
  public VirtualFile getFile() {
    return myClassFile;
  }

  @NotNull
  public VirtualFile getClassFile() {
    return myClassFile;
  }

  public ValidityState getValidityState() {
//        return new TimestampValidityState(myClassFile.getModificationStamp());
    return null;
  }

  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final InstrumentationItem item = (InstrumentationItem)o;

    if (!myClassFile.equals(item.myClassFile)) return false;

    return true;
  }

  public int hashCode() {
    return myClassFile.hashCode();
  }

  public String toString() {
    return "Item: " + myClassFile.getPresentableUrl();
  }

  public boolean isJDK6() {
    return myJDK6;
  }
}
