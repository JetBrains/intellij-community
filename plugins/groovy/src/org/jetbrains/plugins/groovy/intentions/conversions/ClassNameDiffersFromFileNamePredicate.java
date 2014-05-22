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
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
* @author Maxim.Medvedev
*/
class ClassNameDiffersFromFileNamePredicate implements PsiElementPredicate {
  private final Consumer<GrTypeDefinition> myClassConsumer;
  private final boolean mySearchForClassInMultiClassFile;

  ClassNameDiffersFromFileNamePredicate(@Nullable Consumer<GrTypeDefinition> classConsumer, boolean searchForClassInMultiClassFile) {
    myClassConsumer = classConsumer;
    mySearchForClassInMultiClassFile = searchForClassInMultiClassFile;
  }

  ClassNameDiffersFromFileNamePredicate(@Nullable Consumer<GrTypeDefinition> classConsumer) {
    this(classConsumer, false);
  }

  @Override
  public boolean satisfiedBy(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof GrTypeDefinition)) return false;
    if (((GrTypeDefinition)parent).getNameIdentifierGroovy() != element) return false;

    final String name = ((GrTypeDefinition)parent).getName();
    if (name == null || name.isEmpty()) return false;
    if (myClassConsumer != null) myClassConsumer.consume(((GrTypeDefinition)parent));
    final PsiFile file = element.getContainingFile();
    if (!(file instanceof GroovyFile)) return false;
    if (!file.isPhysical()) return false;
    if (name.equals(FileUtil.getNameWithoutExtension(file.getName()))) return false;
    if (mySearchForClassInMultiClassFile) {
      return ((GroovyFile)file).getClasses().length > 1;
    }
    else {
      return !((GroovyFile)file).isScript();
    }
  }
}
