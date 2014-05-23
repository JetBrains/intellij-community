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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ParamInfo;

import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 28.04.2008
 */
public class DynamicElementSettings {
  private String myName;
  private String myContainingClassName;
  private String myType;
  private boolean isMethod;
  private List<ParamInfo> myParams;
  private boolean isStatic;

  public void setContainingClassName(String newName) {
    myContainingClassName = newName;
  }

  public String getContainingClassName() {
    return myContainingClassName;
  }

  public String getType() {
    return myType;
  }

  public void setType(String type) {
    this.myType = type;
  }

  public boolean isMethod() {
    return isMethod;
  }

  public void setMethod(boolean method) {
    isMethod = method;
  }

  public List<ParamInfo> getParams() {
    return myParams;
  }

  public void setParams(List<ParamInfo> pairs) {
    myParams = pairs;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  public boolean isStatic() {
      return isStatic;
  }

  public void setStatic(boolean aStatic) {
    isStatic = aStatic;
  }
}
