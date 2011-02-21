/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.ui;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import javax.swing.*;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class GrTypeComboBox extends JComboBox {
  private final PsiType myType;

  public GrTypeComboBox(PsiType type) {
    super();
    myType = type;
    initialize();
  }

  private void initialize() {
    addItem(new PsiTypeItem(null));
    if (myType != null) {
      final Map<String, PsiType> myTypes = GroovyRefactoringUtil.getCompatibleTypeNames(myType);
      for (String typeName : myTypes.keySet()) {
        addItem(new PsiTypeItem(myTypes.get(typeName)));
      }
    }
  }

  @Nullable
  public PsiType getSelectedType() {
    final Object selected = getSelectedItem();
    assert selected instanceof PsiTypeItem;
    return ((PsiTypeItem)selected).getType();
  }

  private static class PsiTypeItem {
    @Nullable
    private final PsiType myType;

    private PsiTypeItem(final PsiType type) {
      myType = type;
    }

    @Nullable
    public PsiType getType() {
      return myType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PsiTypeItem that = (PsiTypeItem)o;

      if (myType == null) {
        if (that.myType != null) return false;
      }
      else {
        if (!myType.equals(that.myType)) return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      return myType == null ? 0 : myType.hashCode();
    }

    @Override
    public String toString() {
      return myType == null ? "def" : myType.getPresentableText();
    }
  }
}
