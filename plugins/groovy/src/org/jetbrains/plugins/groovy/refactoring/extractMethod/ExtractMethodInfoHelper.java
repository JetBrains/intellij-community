/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.extractMethod;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author ilyas
 */
public class ExtractMethodInfoHelper {

  private final Map<String, ParameterInfo> myInputNamesMap = new HashMap<String, ParameterInfo>();
  private final String myOutputName;
  private final PsiType myOutputType;
  private final PsiElement[] myInnerElements;
  private final GrStatement[] myStatements;

  public ExtractMethodInfoHelper(String[] inputNames, String outputName, Map<String, PsiType> typeMap, PsiElement[] innerElements, GrStatement[] statements) {
    myInnerElements = innerElements;
    myStatements = statements;
    int i = 0;
    for (String name : inputNames) {
      PsiType type = typeMap.get(name);
      ParameterInfo info = new ParameterInfo(name, i, type);
      myInputNamesMap.put(name, info);
      i++;
    }
    myOutputName = outputName;
    if (myOutputName != null) {
      PsiType type = typeMap.get(myOutputName);
      if (type == null) myOutputType = PsiType.VOID;
      else myOutputType = type;
    } else {
      if (ExtractMethodUtil.isSingleExpression(statements) ||
          statements.length == 1 && statements[0] instanceof GrExpression &&
          !(statements[0] instanceof GrAssignmentExpression)) {
        PsiType type = ((GrExpression) statements[0]).getType();
        if (type != null) {
          myOutputType = type;
        } else {
          myOutputType = PsiType.VOID;
        }
      } else  {
        myOutputType = PsiType.VOID;
      }
    }
  }

  @NotNull
  public Project getProject() {
    assert getInnerElements().length > 0;
    return getInnerElements()[0].getProject();
  }

  public boolean validateName(String newName) {
    return true;
  }

  public ParameterInfo[] getParameterInfos() {
    Collection<ParameterInfo> collection = myInputNamesMap.values();
    ParameterInfo[] infos = new ParameterInfo[collection.size()];
    for (ParameterInfo info : collection) {
      int position = info.getPosition();
      assert position < infos.length && infos[position] == null;
      infos[position] = info;
    }
    return infos;
  }

  public boolean setNewName(@NotNull String oldName, @NotNull String newName) {
    ParameterInfo info = myInputNamesMap.remove(oldName);
    if (info == null) return false;
    info.setNewName(newName);
    myInputNamesMap.put(newName, info);
    return true;
  }

  @Nullable
  public String getOutputName() {
    return myOutputName;
  }

  /**
   * Get old names of parameters to be pasted as method call arguments
   *
   * @return array of argument names
   */
  public String[] getArgumentNames() {
    Collection<ParameterInfo> infos = myInputNamesMap.values();
    String[] argNames = new String[infos.size()];
    for (ParameterInfo info : infos) {
      int position = info.getPosition();
      assert position < argNames.length;
      argNames[position] = info.getOldName();
    }
    return argNames;
  }

  @NotNull
  public PsiType getOutputType() {
    return myOutputType;
  }

  @NotNull
  public PsiElement[] getInnerElements() {
    return myInnerElements;
  }

  @NotNull
  public GrStatement[] getStatements() {
    return myStatements;
  }
}
