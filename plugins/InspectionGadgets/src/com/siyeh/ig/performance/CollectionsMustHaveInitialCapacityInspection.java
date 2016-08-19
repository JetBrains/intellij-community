/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class CollectionsMustHaveInitialCapacityInspection
  extends BaseInspection {

  private final CollectionsListSettings mySettings = new CollectionsListSettings() {
    @Override
    protected Set<String> getDefaultSettings() {
      final Set<String> classes = new TreeSet<>(DEFAULT_COLLECTION_LIST);
      classes.add("java.util.BitSet");
      return classes;
    }
  };

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    mySettings.readSettings(node);
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    mySettings.writeSettings(node);
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return mySettings.createOptionsPanel();
  }

  @Override
  @NotNull
  public String getID() {
    return "CollectionWithoutInitialCapacity";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "collections.must.have.initial.capacity.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "collections.must.have.initial.capacity.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CollectionInitialCapacityVisitor();
  }

  private class CollectionInitialCapacityVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiType type = expression.getType();
      if (!isCollectionWithInitialCapacity(type)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null || argumentList.getExpressions().length != 0) {
        return;
      }
      registerNewExpressionError(expression);
    }

    private boolean isCollectionWithInitialCapacity(@Nullable PsiType type) {
      if (!(type instanceof PsiClassType)) {
        return false;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass resolved = classType.resolve();
      if (resolved == null) {
        return false;
      }
      final String className = resolved.getQualifiedName();
      return mySettings.getCollectionClassesRequiringCapacity().contains(className);
    }
  }
}