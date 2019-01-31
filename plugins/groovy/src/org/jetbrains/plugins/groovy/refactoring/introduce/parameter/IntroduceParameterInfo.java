// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractInfoHelper;

/**
 * @author Max Medvedev
 */
public interface IntroduceParameterInfo extends ExtractInfoHelper {
  PsiElement getToSearchFor();

  GrParameterListOwner getToReplaceIn();
}
