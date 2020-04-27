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
package com.intellij.remote.ext;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Irina.Chernushina on 7/29/2016.
 */
public class UnknownCredentialsHolder {
  private String myInterpreterPath;

  @Nullable
  private Element myElement;

  public UnknownCredentialsHolder() {
    myInterpreterPath = "";
  }

  public String getInterpreterPath() {
    return myInterpreterPath;
  }

  public void setInterpreterPath(String interpreterPath) {
    myInterpreterPath = interpreterPath;
  }

  public void save(@NotNull Element element) {
    if (myElement != null) {
      JDOMUtil.copyMissingContent(myElement, element);
    }
  }

  public void load(@NotNull Element element) {
    myElement = element.clone();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UnknownCredentialsHolder holder = (UnknownCredentialsHolder)o;
    return Objects.equals(myInterpreterPath, holder.myInterpreterPath) &&
           Objects.equals(myElement, holder.myElement);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myInterpreterPath, myElement);
  }
}
