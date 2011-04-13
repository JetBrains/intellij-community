/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.dom.converters;

import com.intellij.openapi.module.Module;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.android.dom.LookupClass;
import org.jetbrains.android.dom.LookupPrefix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class ConstantFieldConverter extends ResolvingConverter<String> {
    @NotNull
    public Collection<? extends String> getVariants(ConvertContext context) {
        List<String> result = new ArrayList<String>();
        DomElement element = context.getInvocationElement();
        LookupClass lookupClass = element.getAnnotation(LookupClass.class);
        LookupPrefix lookupPrefix = element.getAnnotation(LookupPrefix.class);
        if (lookupClass != null && lookupPrefix != null) {
          final Module module = context.getModule();
          final GlobalSearchScope scope = module != null ?
                                          GlobalSearchScope.allScope(module.getProject()) :
                                          context.getInvocationElement().getResolveScope();
          PsiClass psiClass = JavaPsiFacade.getInstance(context.getPsiManager().getProject()).findClass(lookupClass.value(), scope);
            if (psiClass != null) {
                PsiField[] psiFields = psiClass.getFields();
                for(PsiField field: psiFields) {
                    if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.PUBLIC)) {
                        PsiExpression initializer = field.getInitializer();
                        if (initializer instanceof PsiLiteralExpression) {
                            PsiLiteralExpression literalExpression = (PsiLiteralExpression) initializer;
                            Object o = literalExpression.getValue();
                            if (o instanceof String && o.toString().startsWith(lookupPrefix.value())) {
                                result.add(o.toString());
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    public String fromString(@Nullable @NonNls String s, ConvertContext context) {
        return s;
    }

    public String toString(@Nullable String  value, ConvertContext context) {
        return value;
    }
}
