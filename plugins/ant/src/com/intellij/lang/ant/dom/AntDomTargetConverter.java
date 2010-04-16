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
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntSupport;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 16, 2010
 */
public class AntDomTargetConverter extends ResolvingConverter<AntDomTarget>{
  @NotNull
  public Collection<? extends AntDomTarget> getVariants(ConvertContext context) {
    // todo: should we add imported targets?
    final AntDomElement element = AntSupport.getInvocationAntDomElement(context);
    if (element != null) {
      return element.getAntProject().getDeclaredTargets();
    }
    return Collections.emptyList();
  }

  @Nullable
  public AntDomTarget fromString(@Nullable @NonNls String s, ConvertContext context) {
    final AntDomElement element = AntSupport.getInvocationAntDomElement(context);
    if (element != null) {
      return element.getAntProject().findTarget(s);
    }
    return null;
  }

  @Nullable
  public String toString(@Nullable AntDomTarget target, ConvertContext context) {
    if (target == null) {
      return null;
    }
    final GenericAttributeValue<String> name = target.getName();
    return name != null? name.getValue() : null;
  }
}
