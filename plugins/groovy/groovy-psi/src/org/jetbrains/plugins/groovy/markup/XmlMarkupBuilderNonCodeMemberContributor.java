/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.markup;

import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author Sergey Evdokimov
 */
public class XmlMarkupBuilderNonCodeMemberContributor extends NonCodeMembersContributor {

  @Nullable
  @Override
  protected String getParentClassName() {
    return "groovy.xml.MarkupBuilder";
  }

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiClass aClass,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    String nameHint = ResolveUtil.getNameHint(processor);
    if (nameHint == null) return;

    if (!ResolveUtil.shouldProcessMethods(processor.getHint(ElementClassHint.KEY))) return;

    GrLightMethodBuilder res = new GrLightMethodBuilder(aClass.getManager(), nameHint);
    res.addParameter("attrs", CommonClassNames.JAVA_UTIL_MAP, false);
    res.addParameter("content", CommonClassNames.JAVA_LANG_OBJECT, false);
    res.setOriginInfo("XML tag");

    if (!processor.execute(res, state)) return;

    res = new GrLightMethodBuilder(aClass.getManager(), nameHint);
    res.addParameter("contentOrAttrs", CommonClassNames.JAVA_LANG_OBJECT, true);
    res.setOriginInfo("XML tag");

    processor.execute(res, state);
  }
}
