/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.properties;

import com.intellij.codeInsight.daemon.impl.quickfix.RenameElementFix;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class UnusedMessageFormatParameterInspection extends PropertiesInspectionBase {
  public static final String REGEXP = "regexp";

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!(file instanceof PropertiesFile)) return null;
    PropertiesFile propertiesFile = (PropertiesFile)file;
    final List<IProperty> properties = propertiesFile.getProperties();
    List<ProblemDescriptor> problemDescriptors = new ArrayList<>();
    for (IProperty property : properties) {
      @NonNls String name = property.getName();
      if (name != null) {
        if (name.startsWith("log4j")) continue;
        if (name.startsWith(REGEXP + ".") || name.endsWith("." + REGEXP)) continue;
      }
      String value = property.getValue();
      IntSet parameters = new IntOpenHashSet();
      if (value != null) {
        int index = value.indexOf('{');
        while (index != -1) {
          value = value.substring(index + 1);
          final int comma = value.indexOf(',');
          final int brace = value.indexOf('}');
          if (brace == -1) break; //misformatted string
          if (comma == -1) {
            index = brace;
          }
          else {
            index = Math.min(comma, brace);
          }
          try {
            parameters.add(Integer.parseInt(value.substring(0, index)));
          }
          catch (NumberFormatException e) {
            break;
          }
          index = value.indexOf('{');
        }
        for (int integer : parameters.toIntArray()) {
          for (int i = 0; i < integer; i++) {
            if (!parameters.contains(i)) {
              ASTNode[] nodes = property.getPsiElement().getNode().getChildren(null);
              PsiElement valElement = nodes.length < 3 ? property.getPsiElement() : nodes[2].getPsi();
              final String message = JavaI18nBundle.message("unused.message.format.parameter.problem.descriptor", integer, Integer.toString(i));
              final String propertyKey = property.getKey();
              final LocalQuickFix[] fixes = isOnTheFly ? new LocalQuickFix[]{new RenameElementFix(((Property)property), propertyKey == null ? REGEXP : propertyKey + "." + REGEXP)} : null;
              problemDescriptors.add(manager.createProblemDescriptor(valElement, message, isOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
              break;
            }
          }
        }
      }
    }
    return problemDescriptors.isEmpty() ? null : problemDescriptors.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }
}
