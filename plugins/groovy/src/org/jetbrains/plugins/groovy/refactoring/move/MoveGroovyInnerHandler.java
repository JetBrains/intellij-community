// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.refactoring.move.moveInner.MoveInnerOptions;
import com.intellij.refactoring.move.moveInner.MoveJavaInnerHandler;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.annotator.intentions.CreateClassActionBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.Collection;

import static org.jetbrains.plugins.groovy.refactoring.move.MoveGroovyClassHandler.removeAllAliasImportedUsages;

public class MoveGroovyInnerHandler extends MoveJavaInnerHandler {

  @Override
  public void preprocessUsages(Collection<UsageInfo> results) {
    removeAllAliasImportedUsages(results);
  }

  @Override
  protected PsiClass createNewClass(MoveInnerOptions options) {
    PsiClass innerClass = options.getInnerClass();
    if (!(innerClass instanceof GrTypeDefinition)) {
      return super.createNewClass(options);
    }

    PsiDirectory dir = (PsiDirectory)options.getTargetContainer();
    return CreateClassActionBase.createClassByType(dir, options.getNewClassName(), options.getInnerClass().getManager(),
                                                   options.getInnerClass(), GroovyTemplates.GROOVY_CLASS, false);
  }
}
