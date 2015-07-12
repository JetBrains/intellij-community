/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;

import java.util.Collection;

public class Members {

  public static final Members EMPTY = new Members() {
    @Override
    public void addFrom(Members other) {
      // do nothing
    }
  };

  public final Collection<PsiMethod> methods = ContainerUtil.newArrayList();
  public final Collection<GrField> fields = ContainerUtil.newArrayList();
  public final Collection<PsiClass> classes = ContainerUtil.newArrayList();

  public void addFrom(Members other) {
    methods.addAll(other.methods);
    fields.addAll(other.fields);
    classes.addAll(other.classes);
  }
}
