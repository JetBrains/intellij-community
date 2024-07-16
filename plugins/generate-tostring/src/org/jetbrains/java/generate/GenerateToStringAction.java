/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate;

import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiMethodUtil;
import org.jetbrains.annotations.Nullable;

/**
 * This action handles the generation of a {@code toString()} method that dumps the fields
 * of the class.
 */
public class GenerateToStringAction extends BaseGenerateAction implements DumbAware {

  public GenerateToStringAction() {
    super(new GenerateToStringActionHandlerImpl());
  }

  @Override
  protected @Nullable PsiClass getTargetClass(Editor editor, PsiFile file) {
    PsiClass targetClass = super.getTargetClass(editor, file);
    if (targetClass != null) return targetClass;
    if (file instanceof PsiJavaFile javaFile) {
      PsiClass[] classes = javaFile.getClasses();
      if (classes.length == 1 && classes[0] instanceof PsiImplicitClass implicitClass &&
          implicitClass.getFirstChild() != null && PsiMethodUtil.hasMainMethod(implicitClass)) {
        return classes[0];
      }
    }
    return null;
  }
}