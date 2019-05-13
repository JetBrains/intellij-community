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

import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Irina.Chernushina on 7/29/2016.
 */
public class UnknownCredentialsHolder {
  private String myInterpreterPath;
  @NotNull
  private final Map<String, String> myAttributes = new HashMap<>();

  public UnknownCredentialsHolder(String interpreterPath) {
    myInterpreterPath = interpreterPath;
  }

  public UnknownCredentialsHolder() {
    myInterpreterPath = "";
  }

  public String getInterpreterPath() {
    return myInterpreterPath;
  }

  public void setInterpreterPath(String interpreterPath) {
    myInterpreterPath = interpreterPath;
  }

  @NotNull
  public Map<String, String> getAttributes() {
    return myAttributes;
  }

  public void save(@NotNull Element element) {
    for (Map.Entry<String, String> entry : myAttributes.entrySet()) {
      element.setAttribute(entry.getKey(), entry.getValue());
    }
  }

  public void load(@NotNull Element element) {
    myAttributes.clear();
    for (Attribute attribute : element.getAttributes()) {
      myAttributes.put(attribute.getName(), attribute.getValue());
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    UnknownCredentialsHolder holder = (UnknownCredentialsHolder)o;

    if (myInterpreterPath != null ? !myInterpreterPath.equals(holder.myInterpreterPath) : holder.myInterpreterPath != null) return false;
    if (!myAttributes.equals(holder.myAttributes)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myInterpreterPath != null ? myInterpreterPath.hashCode() : 0;
    result = 31 * result + myAttributes.hashCode();
    return result;
  }
}
