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
package org.jetbrains.plugins.groovy.refactoring.extract;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.VariableInfo;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Max Medvedev
 */
public abstract class ExtractInfoHelperBase implements ExtractInfoHelper {
  protected final ExtractInfoHelper myInitialInfo;
  protected final Map<String, ParameterInfo> myInputNamesMap;

  public ExtractInfoHelperBase(ExtractInfoHelper initialInfo) {
    myInitialInfo = initialInfo;

    final ParameterInfo[] infos = initialInfo.getParameterInfos();
    myInputNamesMap = new HashMap<>(infos.length);
    for (ParameterInfo info : infos) {
      myInputNamesMap.put(info.getName(), info);
    }
  }

  @Override
  @NotNull
  public Project getProject() {
    return myInitialInfo.getProject();
  }

  @NotNull
  @Override
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

  @Override
  @NotNull
  public VariableInfo[] getOutputVariableInfos() {
    return myInitialInfo.getOutputVariableInfos();
  }

  /**
   * Get old names of parameters to be pasted as method call arguments
   *
   * @return array of argument names
   */
  @NotNull
  @Override
  public String[] getArgumentNames() {
    Collection<ParameterInfo> infos = myInputNamesMap.values();
    String[] argNames = new String[infos.size()];
    for (ParameterInfo info : infos) {
      int position = info.getPosition();
      assert position < argNames.length;
      argNames[position] = info.passAsParameter ? info.getOriginalName() : "";
    }
    return argNames;

  }

  @Override
  @NotNull
  public PsiType getOutputType() {
    return myInitialInfo.getOutputType();
  }

  @Override
  @NotNull
  public PsiElement[] getInnerElements() {
    return myInitialInfo.getInnerElements();
  }

  @Override
  @NotNull
  public GrStatement[] getStatements() {
    return myInitialInfo.getStatements();
  }

  @Nullable
  @Override
  public StringPartInfo getStringPartInfo() {
    return myInitialInfo.getStringPartInfo();
  }

  @Nullable
  @Override
  public GrVariable getVar() {
    return myInitialInfo.getVar();
  }

  @Override
  public boolean hasReturnValue() {
    return myInitialInfo.hasReturnValue();
  }

  @Override
  public PsiElement getContext() {
    return myInitialInfo.getContext();
  }

  @Override
  public boolean isForceReturn() {
    return myInitialInfo.isForceReturn();
  }
}
