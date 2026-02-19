// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  public @NotNull Project getProject() {
    return myInitialInfo.getProject();
  }

  @Override
  public ParameterInfo @NotNull [] getParameterInfos() {
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
  public VariableInfo @NotNull [] getOutputVariableInfos() {
    return myInitialInfo.getOutputVariableInfos();
  }

  /**
   * Get old names of parameters to be pasted as method call arguments
   *
   * @return array of argument names
   */
  @Override
  public String @NotNull [] getArgumentNames() {
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
  public @NotNull PsiType getOutputType() {
    return myInitialInfo.getOutputType();
  }

  @Override
  public PsiElement @NotNull [] getInnerElements() {
    return myInitialInfo.getInnerElements();
  }

  @Override
  public GrStatement @NotNull [] getStatements() {
    return myInitialInfo.getStatements();
  }

  @Override
  public @Nullable StringPartInfo getStringPartInfo() {
    return myInitialInfo.getStringPartInfo();
  }

  @Override
  public @Nullable GrVariable getVar() {
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
