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
package org.jetbrains.plugins.groovy.intentions.other;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Created by Max Medvedev on 10/01/14
 */
public class GrSortMapKeysIntention extends Intention {

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    PsiElement parent = element.getParent();

    if (parent instanceof GrArgumentLabel) {
      PsiElement pparent = parent.getParent().getParent();
      if (pparent instanceof GrListOrMap && !ErrorUtil.containsError(pparent)) {
        GrListOrMap map = (GrListOrMap)pparent;
        if (map.getInitializers().length == 0) {
          GrNamedArgument[] namedArgs = map.getNamedArguments();
          if (isLiteralKeys(namedArgs)) {
            GrListOrMap newMap = constructNewMap(namedArgs, project);
            map.replace(newMap);
          }
        }
      }
    }
  }

  @NotNull
  private static GrListOrMap constructNewMap(@NotNull GrNamedArgument[] args, Project project) {
    StringBuilder builder = new StringBuilder();

    builder.append("[");

    Arrays.sort(args, new Comparator<GrNamedArgument>() {
      @Override
      public int compare(GrNamedArgument o1, GrNamedArgument o2) {
        final String l1 = o1.getLabelName();
        final String l2 = o2.getLabelName();
        assert l1 != null && l2 != null;

        return l1.compareTo(l2);
      }
    });

    for (GrNamedArgument arg : args) {
      builder.append(arg.getText()).append(",\n");
    }


    builder.replace(builder.length() - 2, builder.length(), "]");

    return (GrListOrMap)GroovyPsiElementFactory.getInstance(project).createExpressionFromText(builder);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        PsiElement parent = element.getParent();
        if (parent instanceof GrArgumentLabel &&
            ((GrArgumentLabel)parent).getNameElement().equals(element) &&
            parent.getParent() != null &&
            parent.getParent().getParent() instanceof GrListOrMap) {
          GrListOrMap map = DefaultGroovyMethods.asType(parent.getParent().getParent(), GrListOrMap.class);
          if (!ErrorUtil.containsError(map) && map.getInitializers().length == 0 && isLiteralKeys(map.getNamedArguments())) {
            return true;
          }
        }


        return false;
      }
    };
  }

  private static boolean isLiteralKeys(GrNamedArgument[] args) {
    return DefaultGroovyMethods.find(args, new Closure<Boolean>(null, null) {
      public Boolean doCall(GrNamedArgument it) {
        return it.getLabel().getNameElement() == null;
      }

      public Boolean doCall() {
        return doCall(null);
      }
    }) == null;
  }
}
