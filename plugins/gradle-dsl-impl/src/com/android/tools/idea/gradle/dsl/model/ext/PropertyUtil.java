/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.model.ext;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.REFERENCE;
import static com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement.convertNameToKey;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelSemanticsDescription.CREATE_WITH_VALUE;

import com.android.tools.idea.gradle.dsl.model.ext.transforms.DefaultTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.FileTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.PropertyTransform;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.elements.FakeElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSettableExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.intellij.psi.PsiElement;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PropertyUtil {
  @NonNls public static final String FILE_METHOD_NAME = "file";
  @NonNls public static final String FILE_CONSTRUCTOR_NAME = "File";

  @NotNull
  public static GradleDslSimpleExpression createOrReplaceBasicExpression(@NotNull GradleDslElement parent,
                                                                         @Nullable GradleDslElement oldElement,
                                                                         @NotNull Object value,
                                                                         @NotNull GradleNameElement name,
                                                                         @Nullable ModelPropertyDescription propertyDescription) {
    // Check if we can reuse the element.
    ModelEffectDescription effect = null;
    if (oldElement instanceof GradleDslLiteral) {
      GradleDslSimpleExpression expression = (GradleDslSimpleExpression)oldElement;
      expression.setValue(value);
      return expression;
    }
    else {
      if (oldElement != null) {
        name = oldElement.getNameElement();
        effect = oldElement.getModelEffect();
      } else if (propertyDescription != null) {
        effect = new ModelEffectDescription(propertyDescription, CREATE_WITH_VALUE);
      }

      GradleDslSimpleExpression expression = createBasicExpression(parent, value, name);
      expression.setModelEffect(effect);
      return expression;
    }
  }

  @NotNull
  public static GradleDslSimpleExpression createBasicExpression(@NotNull GradleDslElement parent,
                                                                @NotNull Object value,
                                                                @NotNull GradleNameElement name) {
    GradleDslSimpleExpression newElement = new GradleDslLiteral(parent, name);
    newElement.setValue(value);
    return newElement;
  }

  public static void replaceElement(@NotNull GradleDslElement holder,
                                    @Nullable GradleDslElement oldElement,
                                    @NotNull GradleDslElement newElement) {
    if (holder instanceof GradlePropertiesDslElement) {
      if (oldElement != null) {
        ((GradlePropertiesDslElement)holder).replaceElement(oldElement, newElement);
      }
      else {
        ((GradlePropertiesDslElement)holder).setNewElement(newElement);
      }
    }
    else if (holder instanceof GradleDslMethodCall) {
      assert newElement instanceof GradleDslExpression;
      GradleDslMethodCall methodCall = (GradleDslMethodCall)holder;
      if (oldElement != null) {
        assert oldElement instanceof GradleDslExpression;
        methodCall.replaceArgument((GradleDslExpression)oldElement, (GradleDslExpression)newElement);
      }
      else {
        methodCall.addNewArgument((GradleDslExpression)newElement);
      }
    }
    else {
      throw new IllegalStateException("Property holder has unknown type, " + holder);
    }
  }

  public static void removeElement(@NotNull GradleDslElement element) {
    GradleDslElement holder = element.getParent();
    if (holder == null) {
      // Element is already attached.
      return;
    }

    if (holder instanceof GradlePropertiesDslElement) {
      ((GradlePropertiesDslElement)holder).removeProperty(element);
    }
    else if (holder instanceof GradleDslMethodCall) {
      GradleDslMethodCall methodCall = (GradleDslMethodCall)holder;
      if (element == methodCall.getArgumentsElement()) {
        // In such case we want to delete all the arguments of the methodCall, and since these cannot be null, this implies the methodCall
        // should be deleted as well.
        removeElement(methodCall);
      }
      methodCall.remove(element);
    }
    else {
      throw new IllegalStateException("Property holder has unknown type, " + holder);
    }
  }

  @NotNull
  public static final PropertyTransform DEFAULT_TRANSFORM = new DefaultTransform();
  @NotNull
  public static final PropertyTransform FILE_TRANSFORM = new FileTransform();

  @NotNull
  public static GradlePropertyModelImpl resolveModel(@NotNull GradlePropertyModelImpl model) {
    Set<String> seenModels = new HashSet<>();

    while (model.getValueType() == REFERENCE && !seenModels.contains(model.getFullyQualifiedName())) {
      if (model.getDependencies().isEmpty()) {
        return model;
      }
      seenModels.add(model.getFullyQualifiedName());
      model = model.dependencies().get(0);
    }
    return model;
  }

  /**
   * Follows references as the DslElement level to obtain the resulting element. Note: This only works
   * on GradleDslSimpleExpressions, for any expressions please use {@link #followElement(GradleDslSimpleExpression)}
   *
   * @param expression expression to start at
   * @return resolved expression
   */
  @NotNull
  public static GradleDslSimpleExpression resolveElement(@NotNull GradleDslSimpleExpression expression) {
    while (expression instanceof GradleDslLiteral && expression.isReference() && !expression.hasCycle()) {
      GradleReferenceInjection injection = ((GradleDslLiteral)expression).getReferenceInjection();
      if (injection == null) {
        return expression;
      }
      GradleDslSimpleExpression next = injection.getToBeInjectedExpression();
      if (next == null) {
        return expression;
      }
      expression = next;
    }
    return expression;
  }

  @Nullable
  public static GradleDslElement followElement(@NotNull GradleDslSimpleExpression expression) {
    GradleDslElement element = expression;
    while (element instanceof GradleDslLiteral && ((GradleDslLiteral)element).isReference() && !((GradleDslLiteral)element).hasCycle()) {
      GradleReferenceInjection injection = ((GradleDslLiteral)element).getReferenceInjection();
      if (injection == null) {
        return null;
      }
      GradleDslElement next = injection.getToBeInjected();
      if (next == null) {
        return null;
      }
      element = next;
    }
    return element;
  }

  @Nullable
  public static String getFileValue(@NotNull GradleDslMethodCall methodCall) {
    if (!(methodCall.getMethodName().equals(FILE_METHOD_NAME) && !methodCall.isConstructor() ||
          methodCall.getMethodName().equals(FILE_CONSTRUCTOR_NAME) && methodCall.isConstructor())) {
      return null;
    }

    StringBuilder builder = new StringBuilder();
    for (GradleDslExpression expression : methodCall.getArguments()) {
      if (expression instanceof GradleDslSimpleExpression) {
        String value = ((GradleDslSimpleExpression)expression).getValue(String.class);
        if (value != null) {
          if (builder.length() != 0) {
            builder.append("/");
          }
          builder.append(value);
        }
      }
    }
    String result = builder.toString();
    return result.isEmpty() ? null : result;
  }

  public static boolean isNonExpressionPropertiesElement(@Nullable GradleDslElement e) {
    return e instanceof GradlePropertiesDslElement && !(e instanceof GradleDslExpression);
  }

  public static boolean isPropertiesElementOrMap(@Nullable GradleDslElement e) {
    return e instanceof GradlePropertiesDslElement && !(e instanceof GradleDslExpressionList);
  }

  /**
   * Requires READ_ACCESS.
   */
  public static boolean isElementModified(@NotNull GradleDslElement oldElement, @NotNull GradleDslElement newElement) {
    return checkForModifiedValue(oldElement, newElement) || checkForModifiedName(oldElement, newElement);
  }

  /**
   * Requires READ_ACCESS.
   */
  @Nullable
  public static GradleDslElement findOriginalElement(@NotNull GradleDslElement parent, @NotNull GradleDslElement element) {
    GradlePropertiesDslElement holder = parent instanceof GradleDslMethodCall ? ((GradleDslMethodCall)parent).getArgumentsElement() :
                                        (GradlePropertiesDslElement)parent;

    if (holder instanceof GradleDslExpressionList) {
      List<GradleDslElement> elements = holder.getAllPropertyElements();
      List<GradleDslElement> originalElement = holder.getOriginalElements();
      int index = elements.indexOf(element);
      return index >= 0 && index < originalElement.size() ? originalElement.get(index) : null;
    }
    else {
      return holder.getOriginalElementForNameAndType(element.getName(), element.getElementType());
    }
  }

  /**
   * Requires READ_ACCESS.
   */
  public static boolean isFakeElementModified(@NotNull FakeElement element) {
    GradleDslElement realExpression = element.getRealExpression();
    GradleDslElement realParent = realExpression.getParent();
    GradleDslElement oldRealExpression = realParent == null ? null : findOriginalElement(realParent, realExpression);
    return oldRealExpression == null || isElementModified(oldRealExpression, realExpression);
  }

  /**
   * Requires READ_ACCESS.
   */
  private static boolean checkForModifiedValue(@NotNull GradleDslElement oldElement, @NotNull GradleDslElement newElement) {
    if (!(oldElement.getClass().equals(newElement.getClass()))) {
      return true;
    }

    if (oldElement instanceof GradleDslSettableExpression) {
      GradleDslSettableExpression oExpression = (GradleDslSettableExpression)oldElement;
      GradleDslSettableExpression nExpression = (GradleDslSettableExpression)newElement;
      String newText = null;
      String oldText = null;
      if (nExpression.getUnsavedValue() != null) {
        newText = nExpression.getUnsavedValue().getText();
      }
      else if (nExpression.getExpression() != null) {
        newText = nExpression.getExpression().getText();
      }

      if (oExpression.getExpression() != null) {
        oldText = oExpression.getExpression().getText();
      }
      else if (oExpression.getUnsavedValue() != null) {
        oldText = oExpression.getUnsavedValue().getText();
      }

      return !(newText == null && oldText == null) && !Objects.equals(newText, oldText);
    }

    if (oldElement instanceof GradlePropertiesDslElement) {
      GradlePropertiesDslElement oListOrMap = (GradlePropertiesDslElement)oldElement;
      GradlePropertiesDslElement nListOrMap = (GradlePropertiesDslElement)newElement;
      List<GradleDslElement> originalElements = oListOrMap.getOriginalElements();
      List<GradleDslElement> newElements = nListOrMap.getCurrentElements();
      if (originalElements.size() != newElements.size()) {
        return true;
      }
      BiFunction<GradleDslElement, GradleDslElement, Boolean> func = (oldElement instanceof GradleDslExpressionList) ?
                                                                     PropertyUtil::checkForModifiedValue :
                                                                     PropertyUtil::isElementModified;
      for (int i = 0; i < originalElements.size(); i++) {
        if (func.apply(originalElements.get(i), newElements.get(i))) {
          return true;
        }
      }
      return false;
    }

    if (oldElement instanceof GradleDslMethodCall) {
      GradleDslMethodCall oMethodCall = (GradleDslMethodCall)oldElement;
      GradleDslMethodCall nMethodCall = (GradleDslMethodCall)newElement;
      if (!oMethodCall.getMethodName().equals(nMethodCall.getMethodName())) {
        return false;
      }
      return checkForModifiedValue(oMethodCall.getArgumentsElement(), nMethodCall.getArgumentsElement());
    }

    PsiElement oldPsi = oldElement.getPsiElement();
    PsiElement newPsi = newElement.getPsiElement();
    return oldPsi == null || newPsi == null || !Objects.equals(oldPsi.getText(), newPsi.getText());
  }

  /**
   * Requires READ_ACCESS.
   */
  private static boolean checkForModifiedName(@NotNull GradleDslElement originalElement, @NotNull GradleDslElement newElement) {
    ModelEffectDescription oEffect = originalElement.getModelEffect();
    ModelEffectDescription nEffect = newElement.getModelEffect();
    if (oEffect != null && nEffect != null && Objects.equals(oEffect.property, nEffect.property)) {
      return false;
    }
    GradleNameElement oNameElement = originalElement.getNameElement();
    String oldName = oNameElement.getOriginalName();
    if (oldName == null) {
      return false;
    }

    GradleNameElement nNameElement = newElement.getNameElement();
    String newName = nNameElement.getLocalName();
    if (newName == null) {
      return false;
    }

    return !Objects.equals(convertNameToKey(newName), convertNameToKey(oldName));
  }
}
