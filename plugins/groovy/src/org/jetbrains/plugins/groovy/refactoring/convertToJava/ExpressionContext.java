/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.project.Project;
import com.intellij.util.containers.hash.HashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class ExpressionContext {
  List<String> myStatements = new ArrayList<String>();
  Set<String> myUsedVarNames;
  Project project;
  private boolean myShouldInsertCurlyBrackets = false;

  ExpressionContext(Project project, Set<String> usedVarNames) {
    this.project = project;
    myUsedVarNames = usedVarNames;
  }

  ExpressionContext(Project project) {
    this(project, new HashSet<String>());
  }

  ExpressionContext copy() {
    return new ExpressionContext(project, myUsedVarNames);
  }

  ExpressionContext extend() {
    final HashSet<String> usedVarNames = new HashSet<String>();
    usedVarNames.addAll(myUsedVarNames);
    return new ExpressionContext(project, usedVarNames);
  }

  public void setInsertCurlyBrackets() {
    myShouldInsertCurlyBrackets = true;
  }

  public boolean shouldInsertCurlyBrackets() {
    return myShouldInsertCurlyBrackets;
  }
}