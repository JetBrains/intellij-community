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
package org.jetbrains.plugins.groovy.refactoring.rename.inplace;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.ClosureSyntheticParameter;

/**
 * @author Max Medvedev
 */
public class GrVariableInplaceRenamer extends VariableInplaceRenamer {
  public GrVariableInplaceRenamer(PsiNameIdentifierOwner elementToRename, Editor editor) {
    super(elementToRename, editor);
  }

  @Override
  protected void renameSynthetic(String newName) {
    PsiNamedElement elementToRename = getVariable();
    if (elementToRename instanceof ClosureSyntheticParameter && !"it".equals(newName)) {
      final GrClosableBlock closure = ((ClosureSyntheticParameter)elementToRename).getClosure();
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);
      final PsiType type = ((ClosureSyntheticParameter)elementToRename).getTypeGroovy();
      final GrParameter newParam = factory.createParameter(newName, TypesUtil.unboxPrimitiveTypeWrapper(type));
      final GrParameter added = closure.addParameter(newParam);
      JavaCodeStyleManager.getInstance(added.getProject()).shortenClassReferences(added);
    }
  }
}
