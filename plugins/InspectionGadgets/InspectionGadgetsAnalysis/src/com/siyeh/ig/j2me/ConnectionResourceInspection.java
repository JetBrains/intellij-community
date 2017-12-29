/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.j2me;

import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.xmlb.XmlSerializer;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.resources.ResourceInspection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class ConnectionResourceInspection extends ResourceInspection {

  @Override
  @NotNull
  public String getID() {
    return "ConnectionOpenedButNotSafelyClosed";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("connection.opened.not.safely.closed.display.name");
  }

  @Override
  protected boolean isResourceCreation(PsiExpression expression) {
    if (!(expression instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
    return MethodCallUtils.isCallToMethod(methodCallExpression, "javax.microedition.io.Connector", null, "open", (PsiType[])null);
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    XmlSerializer.serializeInto(this, node, getSerializationFilter());
  }
}
