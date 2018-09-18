// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractInfoHelper;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractInfoHelperBase;
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo;

/**
 * @author Max Medvedev
 */
public class IntroduceParameterInfoImpl extends ExtractInfoHelperBase implements IntroduceParameterInfo, ExtractInfoHelper {
  private final GrParameterListOwner myOwner;
  private final PsiElement myToSearchFor;

  public IntroduceParameterInfoImpl(InitialInfo info, GrParameterListOwner owner, PsiElement toSearchFor) {
    super(info);
    myOwner = owner;
    myToSearchFor = toSearchFor;
  }

  @Override
  public String getName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement getToSearchFor() {
    return myToSearchFor;
  }

  @Override
  public GrParameterListOwner getToReplaceIn() {
    return myOwner;
  }
}
