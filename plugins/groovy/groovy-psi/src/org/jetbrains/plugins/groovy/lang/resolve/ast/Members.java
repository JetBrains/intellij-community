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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;

import java.util.Collection;
import java.util.Collections;

public abstract class Members {

  @NotNull
  public abstract Collection<PsiMethod> getMethods();

  @NotNull
  public abstract Collection<GrField> getFields();

  @NotNull
  public abstract Collection<PsiClass> getClasses();

  @NotNull
  public abstract Collection<PsiClassType> getImplementsTypes();

  public abstract void addFrom(@NotNull Members other);

  public static final Members EMPTY = new Members() {

    @NotNull
    @Override
    public Collection<PsiMethod> getMethods() {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public Collection<GrField> getFields() {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public Collection<PsiClass> getClasses() {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public Collection<PsiClassType> getImplementsTypes() {
      return Collections.emptyList();
    }

    @Override
    public void addFrom(@NotNull Members other) {
      // do nothing
    }
  };

  @NotNull
  public static Members create() {
    return new Members() {

      private final Collection<PsiMethod> methods = ContainerUtil.newArrayList();
      private final Collection<GrField> fields = ContainerUtil.newArrayList();
      private final Collection<PsiClass> classes = ContainerUtil.newArrayList();
      private final Collection<PsiClassType> implementsTypes = ContainerUtil.newArrayList();

      @NotNull
      @Override
      public Collection<PsiMethod> getMethods() {
        return methods;
      }

      @NotNull
      @Override
      public Collection<GrField> getFields() {
        return fields;
      }

      @NotNull
      @Override
      public Collection<PsiClass> getClasses() {
        return classes;
      }

      @NotNull
      @Override
      public Collection<PsiClassType> getImplementsTypes() {
        return implementsTypes;
      }

      @Override
      public void addFrom(@NotNull Members other) {
        methods.addAll(other.getMethods());
        fields.addAll(other.getFields());
        classes.addAll(other.getClasses());
        implementsTypes.addAll(other.getImplementsTypes());
      }
    };
  }
}
