// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.codeInspection.dataFlow.StringExpressionHelper;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public interface OverrideText extends DomElement {

  @NotNull
  @Required
  @Convert(PlaceConverter.class)
  GenericAttributeValue<String> getPlace();

  @NotNull
  @Required(false)
  @Convert(PlaceConverter.class)
  GenericAttributeValue<String> getUseTextOfPlace();

  @NotNull
  @Required(false)
  GenericAttributeValue<String> getText();

  class PlaceConverter extends ResolvingConverter.StringConverter {

    @NotNull
    @Override
    public Collection<String> getVariants(ConvertContext context) {
      return ContainerUtil.concat(getPlaceNames(context, "com.intellij.openapi.actionSystem.ActionPlaces"),
                                  getPlaceNames(context, "com.intellij.vcs.log.ui.VcsLogActionPlaces"));
    }

    @NotNull
    private static List<String> getPlaceNames(ConvertContext context, String className) {
      final PsiClass actionPlacesClass = DomJavaUtil.findClass(className, context.getInvocationElement());
      if (actionPlacesClass == null) return Collections.emptyList();

      return ContainerUtil.mapNotNull(actionPlacesClass.getFields(), field -> {
        if (!field.hasModifierProperty(PsiModifier.PUBLIC)) return null;

        final PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
          return Pair.getSecond(StringExpressionHelper.evaluateExpression(initializer));
        }
        return null;
      });
    }
  }
}
