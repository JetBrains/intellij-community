/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.junit2.info;

import com.intellij.execution.Location;
import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

public abstract class ClassBasedInfo extends TestInfo {
  private final DisplayTestInfoExtractor myClassInfo;
  private PsiClassLocator myClass;
  private String myComment = null;

  public ClassBasedInfo(final DisplayTestInfoExtractor classInfo) {
    myClassInfo = classInfo;
  }

  protected void readClass(final ObjectReader reader) {
    setClassName(reader.readLimitedString());
  }

  protected void setClassName(final String name) {
    myClass = PsiClassLocator.fromQualifiedName(name);
    myComment = null;
  }

  @Nullable
  public Location getLocation(final Project project, GlobalSearchScope searchScope) {
    return myClass.getLocation(project, searchScope);
  }

  public String getComment() {
    if (myComment == null) {
      myComment = myClassInfo.getComment(myClass);
    }
    return myComment;
  }

  public String getName() {
    return myClassInfo.getName(myClass);
  }
}
