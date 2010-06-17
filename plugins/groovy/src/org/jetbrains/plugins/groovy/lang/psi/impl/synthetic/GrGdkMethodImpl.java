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

package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.impl.source.HierarchicalMethodSignatureImpl;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.ui.RowIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;

import javax.swing.*;
import java.lang.reflect.Modifier;

/**
 * @author ven
 */
public class GrGdkMethodImpl extends LightMethod implements GrGdkMethod {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl");
  private final PsiMethod myMethod;

  private LightParameterList myParameterList = null;

  private final LightModifierList myModifierList;

  @NotNull
  public Language getLanguage() {
    return GroovyFileType.GROOVY_FILE_TYPE.getLanguage();
  }

  public boolean isValid() {
    return true;
  }

  public GrGdkMethodImpl(PsiMethod method, boolean isStatic) {
    super(method.getManager(), method, null);
    myMethod = method;
    final PsiManager manager = method.getManager();
    myModifierList = new LightModifierList(manager, ModifierFlags.PUBLIC_MASK + (isStatic ? ModifierFlags.STATIC_MASK : 0));

    final PsiParameter[] originalParameters = method.getParameterList().getParameters();
    final String[] parmNames = new String[originalParameters.length - 1];
    for (int i = 1; i < originalParameters.length; i++) {
      PsiParameter originalParameter = originalParameters[i];
      String baseName;
      final PsiType type = originalParameter.getType();
      String[] nameSuggestions = JavaCodeStyleManager.getInstance(getProject()).suggestVariableName(VariableKind.PARAMETER, null,
          null, type).names;
      if (nameSuggestions.length > 0) {
        baseName = nameSuggestions[0];
      } else {
        baseName = "p";
      }

      int postfix = 1;

      String name = baseName;
      NextName:
      do {
        for (int j = 1; j < i; j++) {
          if (name.equals(parmNames[j - 1])) {
            name = baseName + postfix;
            postfix++;
            continue NextName;
          }
        }

        break;
      } while (true);

      parmNames[i - 1] = name;
    }

    myParameterList = new LightParameterList(manager, new Computable<LightParameter[]>() {
      public LightParameter[] compute() {
        LightParameter[] result = new LightParameter[parmNames.length];
        for (int i = 0; i < result.length; i++) {
          final PsiParameter parameter = originalParameters[i + 1];
          LOG.assertTrue(parameter.isValid());
          result[i] = new LightParameter(manager, parmNames[i], null, parameter.getType(), GrGdkMethodImpl.this);

        }
        return result;
      }
    });
  }

  @NotNull
  @Override
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return new HierarchicalMethodSignatureImpl((MethodSignatureBackedByPsiMethod)getSignature(PsiSubstitutor.EMPTY));
  }

  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    /*final PsiParameter[] parameters = getParameterList().getParameters();
    PsiType[] parameterTypes = new PsiType[parameters.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypes[i] = parameters[i].getType();
    }*/

    return MethodSignatureBackedByPsiMethod.create(this, substitutor); //todo
  }

  public PsiMethod getStaticMethod() {
    return myMethod;
  }

  @NotNull
  public PsiParameterList getParameterList() {
    return myParameterList;
  }

  @NotNull
  public PsiModifierList getModifierList() {
    return myModifierList;
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return myModifierList.hasModifierProperty(name);
  }

  public boolean canNavigate() {
    return myMethod.canNavigate();
  }

  @Override
  public boolean isConstructor() {
    return false;
  }

  @NotNull
  public PsiElement getNavigationElement() {
    return myMethod.getNavigationElement();
  }

  public void navigate(boolean requestFocus) {
    myMethod.navigate(requestFocus);
  }
  public PsiMethodReceiver getMethodReceiver() {
    return null;
  }

  @Override
  public Icon getIcon(int flags) {
    RowIcon baseIcon = createLayeredIcon(GroovyIcons.METHOD, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }
}
