/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.serialization;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class SerializableInspectionBase extends BaseInspection {
  private static final JComponent[] EMPTY_COMPONENT_ARRAY = {};
  @SuppressWarnings({"PublicField"})
  public boolean ignoreAnonymousInnerClasses = false;
  @Deprecated @SuppressWarnings({"PublicField"})
  public String superClassString = "java.awt.Component";
  protected List<String> superClassList = new ArrayList<>();

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    parseString(superClassString, superClassList);
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    superClassString = formatString(superClassList);
    super.writeSettings(node);
  }

  protected JComponent[] createAdditionalOptions() {
    return EMPTY_COMPONENT_ARRAY;
  }

  protected boolean isIgnoredSubclass(PsiClass aClass) {
    if (SerializationUtils.isDirectlySerializable(aClass)) {
      return false;
    }
    for (String superClassName : superClassList) {
      if (InheritanceUtil.isInheritor(aClass, superClassName)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String getAlternativeID() {
    return "serial";
  }
}
