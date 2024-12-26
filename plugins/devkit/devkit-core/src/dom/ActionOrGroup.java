// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupResolveConverter;

import java.util.List;
import java.util.function.Function;

public interface ActionOrGroup extends DomElement {

  @NotNull
  @NameValue
  @Stubbed
  @Required(false)
  GenericAttributeValue<String> getId();

  @NotNull
  @Stubbed
  @Required(false)
  GenericAttributeValue<String> getIcon();

  @NotNull
  @Stubbed
  GenericAttributeValue<Boolean> getPopup();

  /**
   * @see TextType#TEXT
   */
  @NotNull
  @Stubbed
  @Required(false)
  GenericAttributeValue<String> getText();

  /**
   * @see TextType#DESCRIPTION
   */
  @NotNull
  @Stubbed
  @Required(false)
  GenericAttributeValue<String> getDescription();

  @NotNull
  @Convert(ActionOrGroupResolveConverter.OnlyActions.class)
  GenericAttributeValue<ActionOrGroup> getUseShortcutOf();

  @NotNull
  List<OverrideText> getOverrideTexts();

  OverrideText addOverrideText();


  enum TextType {

    /**
     * @see #getText()
     */
    TEXT(ActionOrGroup::getText, Nls.Capitalization.Title, ".text", actionOrGroup -> {
      if (!(actionOrGroup instanceof Action)) return false;
      final PsiClass actionClass = ((Action)actionOrGroup).getClazz().getValue();
      return actionClass == null || actionClass.getConstructors().length == 0;
    }),

    /**
     * @see #getDescription()
     */
    DESCRIPTION(ActionOrGroup::getDescription, Nls.Capitalization.Sentence, ".description", actionOrGroup -> false);

    private final Function<ActionOrGroup, GenericDomValue<String>> myDomValueGetter;
    private final Nls.Capitalization myCapitalization;
    private final String myPropertyKeySuffix;
    private final Function<ActionOrGroup, Boolean> myRequired;

    TextType(Function<ActionOrGroup, GenericDomValue<String>> domValueGetter,
             Nls.Capitalization capitalization, String propertyKeySuffix,
             Function<ActionOrGroup, Boolean> required) {
      myDomValueGetter = domValueGetter;
      myCapitalization = capitalization;
      myPropertyKeySuffix = propertyKeySuffix;
      myRequired = required;
    }

    public GenericDomValue<String> getDomValue(ActionOrGroup actionOrGroup) {
      return myDomValueGetter.apply(actionOrGroup);
    }

    public Nls.Capitalization getCapitalization() {
      return myCapitalization;
    }

    public boolean isRequired(ActionOrGroup actionOrGroup) {
      return myRequired.apply(actionOrGroup);
    }

    public String getMessageKey(ActionOrGroup actionOrGroup) {
      return getMessageKeyPrefix(actionOrGroup) + actionOrGroup.getId().getStringValue() +
             myPropertyKeySuffix;
    }

    public String getMessageKey(ActionOrGroup actionOrGroup, @NotNull OverrideText overrideText) {
      return getMessageKeyPrefix(actionOrGroup) + actionOrGroup.getId().getStringValue() +
             "." + overrideText.getPlace().getStringValue() +
             myPropertyKeySuffix;
    }

    private static @NotNull String getMessageKeyPrefix(ActionOrGroup actionOrGroup) {
      return actionOrGroup instanceof Action ? "action." : "group.";
    }
  }
}
