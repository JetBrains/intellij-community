// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;

import java.util.ArrayList;
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

      private final Collection<PsiMethod> methods = new ArrayList<>();
      private final Collection<GrField> fields = new ArrayList<>();
      private final Collection<PsiClass> classes = new ArrayList<>();
      private final Collection<PsiClassType> implementsTypes = new ArrayList<>();

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
