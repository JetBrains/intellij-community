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
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 07-Sep-2005
 */
public class UnusedMessageFormatParameterInspection extends PropertySuppressableInspectionBase {
  public static final String REGEXP = "regexp";
  @NotNull
  public String getGroupDisplayName() {
    return PropertiesBundle.message("properties.files.inspection.group.display.name");
  }

  @NotNull
  public String getDisplayName() {
    return PropertiesBundle.message("unused.message.format.parameter.display.name");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "UnusedMessageFormatParameter";
  }

  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
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
      Set<Integer> parameters = new HashSet<>();
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
            parameters.add(new Integer(value.substring(0, index)));
          }
          catch (NumberFormatException e) {
            break;
          }
          index = value.indexOf('{');
        }
        for (Integer integer : parameters) {
          for (int i = 0; i < integer.intValue(); i++) {
            if (!parameters.contains(new Integer(i))) {
              ASTNode[] nodes = property.getPsiElement().getNode().getChildren(null);
              PsiElement valElement = nodes.length < 3 ? property.getPsiElement() : nodes[2].getPsi();
              final String message = PropertiesBundle.message("unused.message.format.parameter.problem.descriptor", integer.toString(), Integer.toString(i));
              final String propertyKey = property.getKey();
              final LocalQuickFix[] fixes = isOnTheFly ? new LocalQuickFix[]{new RenameElementFix(((Property)property), propertyKey == null ? REGEXP : propertyKey + "." + REGEXP)} : null;
              problemDescriptors.add(manager.createProblemDescriptor(valElement, message, isOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
              break;
            }
          }
        }
      }
    }
    return problemDescriptors.isEmpty() ? null : problemDescriptors.toArray(new ProblemDescriptor[problemDescriptors.size()]);
  }
}
