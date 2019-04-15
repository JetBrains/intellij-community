/*
 * Copyright 2007-2010 Bas Leijdekkers
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

import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class CollectionContainsUrlInspection extends MapOrSetKeyInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "collection.contains.url.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final ClassType type = (ClassType)infos[0];
    return InspectionGadgetsBundle.message(
      "collection.contains.url.problem.descriptor", type);
  }

  @Override
  protected boolean shouldTriggerOnKeyType(PsiType argumentType) {
    return argumentType.equalsToText("java.net.URL");
  }
}
