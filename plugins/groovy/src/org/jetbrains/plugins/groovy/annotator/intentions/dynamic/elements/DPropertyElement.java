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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import com.intellij.psi.*;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrDynamicImplicitProperty;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.LightModifierList;

import java.lang.reflect.Modifier;

/**
 * User: Dmitry.Krasilschikov
 * Date: 20.12.2007
 */
public class DPropertyElement extends DItemElement {
  private PsiVariable myPsi;

  //Do not use directly! Persistence component uses default constructor for deserializable
  public DPropertyElement() {
    super(null, null, null);
  }

  public DPropertyElement(Boolean isStatic, String name, String type) {
    super(isStatic, name, type);
  }

  public void clearCache() {
    myPsi = null;
  }

  @NotNull
  public PsiVariable getPsi(PsiManager manager, final String containingClassName) {
    if (myPsi != null) return myPsi;

    Boolean isStatic = isStatic();
    int modifiers = (isStatic != null && isStatic.booleanValue()) ? ModifierFlags.STATIC_MASK : 0;

    String type = getType();
    if (type == null) {
      type = CommonClassNames.JAVA_LANG_OBJECT;
    }
    myPsi = new GrDynamicImplicitProperty(manager, getName(), type, containingClassName, new LightModifierList(manager, modifiers), null) {
      @Override
      public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        DynamicManager.getInstance(getProject()).replaceDynamicPropertyName(containingClassName, getName(), name);
        return super.setName(name);
      }
    };
    return myPsi;
  }
}
