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
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMethodOwner;

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
  private final GrMethodOwner myOwner;
  private final boolean myIsStatic;
  private boolean mySpecifyType;
  private final PsiElement[] myInnerElements;
  private String myVisibility;
  private final Project myProject;
  private final GrStatement[] myStatements;

  public ExtractMethodInfoHelper(String[] inputNames,
                                 String outputName,
                                 Map<String, PsiType> typeMap,
                                 PsiElement[] innerElements,
                                 GrStatement[] statements,
                                 GrMethodOwner owner, boolean isStatic) {
    myInnerElements = innerElements;
    myStatements = statements;
    myOwner = owner;
    myIsStatic = isStatic;
    myVisibility = PsiModifier.PRIVATE;
    assert myStatements.length > 0;
    myProject = myStatements[0].getProject();
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
          myOutputType = TypeConversionUtil.erasure(type);
        } else {
          myOutputType = PsiType.VOID;
        }
      } else {
        PsiType returnType = referTypeFromContext(myStatements);
        myOutputType = returnType == null ? PsiType.VOID : returnType;
      }
    }
    mySpecifyType = !(myOutputType == PsiType.VOID || myOutputType.equalsToText("java.lang.Object"));
  }

  private PsiType referTypeFromContext(GrStatement[] statements) {
    assert statements.length > 0;
    GrStatement finalStatement = statements[statements.length - 1];
    if (finalStatement instanceof GrExpression) {
      GrExpression expr = (GrExpression) finalStatement;
      PsiElement parent = expr.getParent();
      GrStatement[] grStatements = GrStatement.EMPTY_ARRAY;
      if (parent instanceof GrClosableBlock) {
        grStatements = ((GrClosableBlock) parent).getStatements();
      } else if (parent instanceof GrOpenBlock && parent.getParent() instanceof GrMethod) {
        grStatements = ((GrOpenBlock) parent).getStatements();
      }
      if (grStatements.length > 0 && grStatements[grStatements.length - 1] == expr) {
        PsiType type = expr.getType();
        if (type != null) {
          type = TypeConversionUtil.erasure(type);
        }
        return type;
      }
    }
    return null;
  }

  @NotNull
  public Project getProject() {
    return myProject;
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
      argNames[position] = info.passAsParameter() ? info.getOldName() : "";
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

  public boolean isStatic() {
    return myIsStatic;
  }

  public String getVisibility() {
    return myVisibility;
  }

  public void setVisibility(String visibility) {
    myVisibility = visibility;
  }

  public boolean specifyType() {
    return mySpecifyType;
  }

  public void setSpecifyType(boolean specifyType) {
    mySpecifyType = specifyType;
  }

  public GrMethodOwner getOwner() {
    return myOwner;
  }
}
