// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.dsl.model.ext;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.util.GradleNameElementUtil;
import com.android.tools.idea.gradle.dsl.api.util.TypeReference;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.PropertyTransform;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.*;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.FAKE;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.*;

public class GradlePropertyModelImpl implements GradlePropertyModel {
  @Nullable protected GradleDslElement myElement;
  @Nullable protected GradleDslElement myDefaultElement;
  @NotNull protected GradleDslElement myPropertyHolder;

  // The list of transforms to be checked for this property model. Only the first transform that has its PropertyTransform#condition
  // return true will be used.
  @NotNull
  private List<PropertyTransform> myTransforms = new ArrayList<>();

  // The following properties should always be kept up to date with the values given by myElement.getElementType(), myElement.getName()
  // and myElement.getModelProperty().
  @NotNull private final PropertyType myPropertyType;
  @NotNull protected String myName;
  @Nullable protected ModelPropertyDescription myPropertyDescription;

  public GradlePropertyModelImpl(@NotNull GradleDslElement element) {
    myElement = element;
    myTransforms.add(DEFAULT_TRANSFORM);

    GradleDslElement parent = element.getParent();
    assert (parent instanceof GradlePropertiesDslElement ||
            parent instanceof GradleDslMethodCall) : "Property found to be invalid, this should never happen!";
    myPropertyHolder = parent;

    myPropertyType = myElement.getElementType();
    myName = myElement.getName();
    myPropertyDescription = myElement.getModelProperty();
  }

  // Used to create an empty property with no backing element.
  public GradlePropertyModelImpl(@NotNull GradleDslElement holder, @NotNull PropertyType type, @NotNull String name) {
    myPropertyHolder = holder;
    myPropertyType = type;
    myName = name;
    myTransforms.add(DEFAULT_TRANSFORM);
    myPropertyDescription = null; // TODO(xof): this does not actually mean (yet, during transition) that this is not a model property
  }

  public GradlePropertyModelImpl(@NotNull GradleDslElement holder, @NotNull PropertyType type, @NotNull ModelPropertyDescription description) {
    this(holder, type, description.name);
    myPropertyDescription = description;
  }

  public void addTransform(@NotNull PropertyTransform transform) {
    myTransforms.add(0, transform);
  }

  @Nullable
  public GradleDslElement getDefaultElement() {
    return myDefaultElement;
  }

  public void setDefaultElement(@NotNull GradleDslElement defaultElement) {
    myDefaultElement = defaultElement;
  }

  @Override
  @NotNull
  public ValueType getValueType() {
    return extractAndGetValueType(getElement());
  }

  @Override
  @NotNull
  public PropertyType getPropertyType() {
    GradleDslElement element = getElement();
    return element == null ? myPropertyType : element.getElementType();
  }

  @Override
  @Nullable
  public <T> T getValue(@NotNull TypeReference<T> typeReference) {
    return extractValue(typeReference, true);
  }

  @Override
  public <T> T getRawValue(@NotNull TypeReference<T> typeReference) {
    return extractValue(typeReference, false);
  }

  @Nullable
  private static GradleDslElement maybeGetInnerReferenceModel(@NotNull GradleDslElement element) {
    if (extractAndGetValueType(element) == LIST && element instanceof GradleDslExpressionList) {
      GradleDslExpressionList list = (GradleDslExpressionList)element;
      if (list.getExpressions().size() == 1) {
        GradleDslExpression expression = list.getElementAt(0);
        if (expression instanceof GradleDslLiteral && ((GradleDslLiteral)expression).isReference()) {
          GradleDslLiteral reference = (GradleDslLiteral)expression;
          GradleReferenceInjection injection = reference.getReferenceInjection();
          if (injection != null) {
            return injection.getToBeInjected();
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private Map<String, GradlePropertyModel> getMap(boolean resolved) {
    GradleDslElement element = getElement();
    if (element == null) {
      return ImmutableMap.of();
    }

    GradleDslExpressionMap map;
    GradleDslElement innerElement = maybeGetInnerReferenceModel(element);
    // If we have a single reference it will be parsed as a list with one element.
    // we need to make sure that this actually gets resolved to the correct map.
    if (resolved && innerElement instanceof GradleDslExpressionMap) {
      map = (GradleDslExpressionMap)innerElement;
    }
    else {
      assert element instanceof GradleDslExpressionMap;
      map = (GradleDslExpressionMap)element;
    }

    return map.getPropertyElements(GradleDslExpression.class).stream()
      .collect(Collectors.toMap(e -> e.getName() ,e -> new GradlePropertyModelImpl(e), (u, v) -> v, LinkedHashMap::new));
  }

  @NotNull
  private List<GradlePropertyModel> getList(boolean resolved) {
    GradleDslElement element = getElement();
    if (element == null) {
      return ImmutableList.of();
    }

    assert element instanceof GradleDslExpressionList;

    GradleDslExpressionList list = (GradleDslExpressionList)element;
    // If the list contains a single reference, that is also to a list. Follow it and return the
    // resulting list. Only do this if the resolved value is requested.
    if (resolved) {
      GradleDslElement innerElement = maybeGetInnerReferenceModel(element);
      if (innerElement instanceof GradleDslExpressionList) {
        list = (GradleDslExpressionList)innerElement;
      }
    }

    return ContainerUtil.map(list.getExpressions(), e -> new GradlePropertyModelImpl(e));
  }

  @Override
  @NotNull
  public String getName() {
    GradleDslElement element = getElement();

    if (element != null && element.getParent() instanceof GradleDslExpressionList) {
      GradleDslExpressionList list = (GradleDslExpressionList)element.getParent();
      int index = list.findIndexOf(element);
      if (index != -1) {
        // This is the case if the element is a FakeElement
        return String.valueOf(index);
      }
    }

    return element == null ? myName : element.getName();
  }

  @Override
  @NotNull
  public List<GradlePropertyModel> getDependencies() {
    return new ArrayList<>(dependencies());
  }

  @Override
  @NotNull
  public String getFullyQualifiedName() {
    GradleDslElement element = getRawElement();

    if (element != null && element.getParent() instanceof GradleDslExpressionList) {
      GradleDslExpressionList list = (GradleDslExpressionList)element.getParent();
      return element.getParent().getQualifiedName() + "[" + String.valueOf(list.findIndexOf(element)) + "]";
    }

    return element == null ? myPropertyHolder.getQualifiedName() + "." + getName() : element.getQualifiedName();
  }

  @Override
  @NotNull
  public VirtualFile getGradleFile() {
    return myPropertyHolder.getDslFile().getFile();
  }

  @Override
  public void setValue(@NotNull Object value) {
    GradleDslExpression newElement;
    if (myPropertyDescription == null) {
      newElement = getTransform().bind(myPropertyHolder, myElement, value, getName());
    }
    else {
      newElement = getTransform().bind(myPropertyHolder, myElement, value, myPropertyDescription);
    }
    bindToNewElement(newElement);
  }

  @Override
  @NotNull
  public GradlePropertyModel convertToEmptyMap() {
    makeEmptyMap();
    return this;
  }

  @Override
  @NotNull
  public GradlePropertyModel getMapValue(@NotNull String key) {
    ValueType valueType = getValueType();
    if (valueType != MAP && valueType != NONE) {
      throw new IllegalStateException("Can't add map value to type: " + valueType + ". " +
                                      "Please call GradlePropertyModel#convertToMap before trying to add values");
    }

    if (valueType == NONE || myElement == null) {
      makeEmptyMap();
    }


    GradleDslElement element = getTransform().transform(myElement);
    assert element instanceof GradleDslExpressionMap;

    // Does the element already exist?
    GradleDslExpressionMap map = (GradleDslExpressionMap)element;
    GradleDslElement arg = map.getPropertyElement(key);

    return arg == null ? new GradlePropertyModelImpl(element, PropertyType.DERIVED, key) : new GradlePropertyModelImpl(arg);
  }

  @Override
  @NotNull
  public GradlePropertyModel convertToEmptyList() {
    makeEmptyList();
    return this;
  }

  @Override
  @NotNull
  public GradlePropertyModel addListValue() {
    ValueType valueType = getValueType();
    if (valueType != LIST && valueType != NONE) {
      throw new IllegalStateException("Can't add list value to type: " + valueType + ". " +
                                      "Please call GradlePropertyModel#convertToList before trying to add values");
    }

    if (valueType == NONE || myElement == null) {
      makeEmptyList();
    }

    GradleDslElement element = getTransform().transform(myElement);
    assert element instanceof GradleDslExpressionList;

    return addListValueAt(((GradleDslExpressionList)element).getExpressions().size());
  }

  @Override
  @NotNull
  public GradlePropertyModel addListValueAt(int index) {
    ValueType valueType = getValueType();
    if (valueType != LIST && valueType != NONE) {
      throw new IllegalStateException("Can't add list value to type: " + valueType + ". " +
                                      "Please call GradlePropertyModel#convertToList before trying to add values");
    }

    if (valueType == NONE || myElement == null) {
      makeEmptyList();
    }

    GradleDslElement element = getTransform().transform(myElement);
    assert element instanceof GradleDslExpressionList;

    // Unlike maps, we don't create a placeholder element. This is since we need to retain and update order in the list.
    // This would be hard to create an intuitive api to do this, so instead we always create an empty string as the new item.
    GradleDslLiteral literal = new GradleDslLiteral(element, GradleNameElement.empty());
    literal.setValue("");

    GradleDslExpressionList list = (GradleDslExpressionList)element;
    list.addNewExpression(literal, index);

    return new GradlePropertyModelImpl(literal);
  }

  @Override
  @Nullable
  public GradlePropertyModel getListValue(@NotNull Object value) {
    ValueType valueType = getValueType();
    if (valueType != LIST && valueType != NONE) {
      throw new IllegalStateException("Can't get list value on type: " + valueType + ". " +
                                      "Please call GradlePropertyModel#convertToList before trying to get values");
    }

    List<GradlePropertyModel> list = getValue(LIST_TYPE);
    if (list == null) {
      return null;
    }
    return list.stream().filter(e -> {
      Object v = e.getValue(OBJECT_TYPE);
      return v != null && v.equals(value);
    }).findFirst().orElse(null);
  }

  @Override
  public void delete() {
    GradleDslElement element = getElement();
    if (element == null || myElement == null) {
      // Nothing to delete.
      return;
    }

    myElement = getTransform().delete(myPropertyHolder, myElement, element);
  }

  @Override
  @NotNull
  public ResolvedPropertyModelImpl resolve() {
    return new ResolvedPropertyModelImpl(this);
  }

  @NotNull
  @Override
  public GradlePropertyModel getUnresolvedModel() {
    return this;
  }

  @Nullable
  @Override
  public PsiElement getPsiElement() {
    GradleDslElement element = getElement();
    if (element == null) {
      return null;
    }
    return element.getPsiElement();
  }

  @Nullable
  @Override
  public PsiElement getExpressionPsiElement() {
    return getExpressionPsiElement(false);
  }

  @Nullable
  @Override
  public PsiElement getFullExpressionPsiElement() {
    return getExpressionPsiElement(true);
  }

  @Nullable
  private PsiElement getExpressionPsiElement(boolean fullExpression) {
    // We don't use the transform here
    GradleDslElement element = fullExpression ? myElement : getElement();
    if (element instanceof GradleDslExpression) {
      return ((GradleDslExpression)element).getExpression();
    }

    return element == null ? null : element.getPsiElement();
  }

  @Override
  public void rename(@NotNull String name) {
    rename(Arrays.asList(name));
  }

  @Override
  public void rename(@NotNull List<String> name) {
    // If we have no backing element then just alter the name that we will change.
    if (myElement == null) {
      myName = GradleNameElementUtil.join(name);
      return;
    }

    GradleDslElement element = getElement();
    if (element == null) {
      return;
    }

    GradleDslElement parent = element.getParent();

    // Check that the element should actually be renamed.
    if (parent instanceof GradleDslExpressionList || parent instanceof GradleDslMethodCall) {
      throw new UnsupportedOperationException("Can't rename list values!");
    }

    element.rename(name);
    // myName needs to be consistent with the elements name.
    myName = myElement.getName();
  }

  @Override
  public boolean isModified() {
    GradleDslElement element = myElement;
    if (element != null) {
      if (element instanceof FakeElement) {
        // FakeElements need special handling as they are not connected to the tree doe findOriginalElement will be null.
        return isFakeElementModified((FakeElement)element);
      }

      GradleDslElement originalElement = findOriginalElement(myPropertyHolder, element);
      return originalElement == null || isElementModified(originalElement, element);
    }

    GradlePropertiesDslElement holder;
    if (myPropertyHolder instanceof GradleDslMethodCall) {
      holder = ((GradleDslMethodCall)myPropertyHolder).getArgumentsElement();
    }
    else {
      holder = (GradlePropertiesDslElement)myPropertyHolder;
    }

    GradleDslElement originalElement = holder.getOriginalElementForNameAndType(getName(), myPropertyType);
    GradleDslElement holderOriginalElement = findOriginalElement(holder.getParent(), holder);
    // For a property element to be modified : it should either be under a modified state itself, or should have made a modification
    // to the original state of the dsl tree.
    return originalElement != null && (originalElement.isModified() || isElementModified(holderOriginalElement, holder));
  }

  @Override
  public String toString() {
    return getValue(STRING_TYPE);
  }

  @Nullable
  @Override
  public String valueAsString() {
    return getValue(STRING_TYPE);
  }

  @NotNull
  @Override
  public String forceString() {
    String s = toString();
    assert s != null;
    return s;
  }

  @Nullable
  @Override
  public Integer toInt() {
    return getValue(INTEGER_TYPE);
  }

  @Nullable
  @Override
  public BigDecimal toBigDecimal() {
    return getValue(BIG_DECIMAL_TYPE);
  }

  @Nullable
  @Override
  public Boolean toBoolean() {
    return getValue(BOOLEAN_TYPE);
  }

  @Nullable
  @Override
  public List<GradlePropertyModel> toList() {
    return getValue(LIST_TYPE);
  }

  @Nullable
  @Override
  public Map<String, GradlePropertyModel> toMap() {
    return getValue(MAP_TYPE);
  }

  private static ValueType extractAndGetValueType(@Nullable GradleDslElement element) {
    if (element == null) {
      return NONE;
    }

    if (element instanceof GradleDslExpressionMap) {
      return MAP;
    }
    else if (element instanceof GradleDslExpressionList) {
      return LIST;
    }
    else if (element instanceof GradleDslSimpleExpression && ((GradleDslSimpleExpression)element).isReference()) {
      return REFERENCE;
    }
    else if ((element instanceof GradleDslMethodCall &&
              (element.shouldUseAssignment() || element.getElementType() == PropertyType.DERIVED)) ||
             element instanceof GradleDslUnknownElement) {
      // This check ensures that methods we care about, i.e targetSdkVersion(12) are not classed as unknown.
      return UNKNOWN;
    }
    else if (element instanceof GradleDslSimpleExpression) {
      GradleDslSimpleExpression expression = (GradleDslSimpleExpression)element;
      Object value = expression.getValue();
      if (value instanceof Boolean) {
        return BOOLEAN;
      }
      else if (value instanceof Integer) {
        return INTEGER;
      }
      else if (value instanceof String) {
        return STRING;
      }
      else if (value instanceof BigDecimal) {
        return BIG_DECIMAL;
      }
      else if (value == null) {
        return NONE;
      }
      else {
        return UNKNOWN;
      }
    }
    else {
      // We should not be trying to create properties based of other elements.
      return UNKNOWN;
    }
  }

  @Nullable
  private <T> T extractValue(@NotNull TypeReference<T> typeReference, boolean resolved) {
    GradleDslElement element = getElement();
    // If we don't have an element, no value has yet been set, but we might have a default.
    if (element == null) {
      element = getDefaultElement();
    }
    // If we still don't have an element, we have no value.
    if (element == null) {
      return null;
    }

    ValueType valueType = getValueType();
    Object value;
    if (valueType == MAP) {
      value = getMap(resolved);
    }
    else if (valueType == LIST) {
      value = getList(resolved);
    }
    else if (valueType == REFERENCE) {
      // For references only display the reference text for both resolved and unresolved values.
      // Users should follow the reference to obtain the value.
      GradleDslSimpleExpression ref = (GradleDslSimpleExpression)element;
      String refText = ref.getReferenceText();
      if (typeReference.getType() == Object.class || typeReference.getType() == ReferenceTo.class) {
        value = refText == null ? null : typeReference.castTo(new ReferenceTo(refText));
      }
      else {
        value = refText == null ? null : typeReference.castTo(refText);
      }
    }
    else if (valueType == UNKNOWN) {
      // If its a GradleDslBlockElement use the name, otherwise use the psi text. This prevents is dumping the whole
      // elements block as a string value.
      if (!(element instanceof GradleDslBlockElement)) {
        PsiElement psiElement = element instanceof GradleDslSettableExpression
                                ? ((GradleDslSettableExpression)element).getCurrentElement()
                                : element.getPsiElement();
        if (psiElement == null) {
          return null;
        }
        value = GradleDslElementImpl.getPsiText(psiElement);
      }
      else {
        value = element.getFullName();
      }
    }
    else {
      GradleDslSimpleExpression expression = (GradleDslSimpleExpression)element;

      value = resolved ? expression.getValue() : expression.getUnresolvedValue();
    }

    if (value == null) {
      return null;
    }

    T result = typeReference.castTo(value);
    // Attempt to cast to a string if requested. But only do this for unresolved values.
    if (result == null && typeReference.getType().equals(String.class)) {
      result = typeReference.castTo(value.toString());
    }

    return result;
  }

  private void makeEmptyMap() {
    if (myPropertyDescription == null) {
      bindToNewElement(getTransform().bindMap(myPropertyHolder, myElement, getName(), false));
    }
    else {
      bindToNewElement(getTransform().bindMap(myPropertyHolder, myElement, myPropertyDescription, false));
    }
  }

  private void makeEmptyList() {
    if (myPropertyDescription == null) {
      bindToNewElement(getTransform().bindList(myPropertyHolder, myElement, getName(), false));
    }
    else {
      bindToNewElement(getTransform().bindList(myPropertyHolder, myElement, myPropertyDescription, false));
    }
  }

  private void bindToNewElement(@NotNull GradleDslExpression newElement) {
    if (newElement == myElement) {
      // No need to bind
      return;
    }

    if (myElement != null && myElement.getElementType() == FAKE) {
      throw new UnsupportedOperationException("Can't bind from a fake element!");
    }

    GradleDslElement element = getTransform().replace(myPropertyHolder, myElement, newElement, getName());
    element.setElementType(myPropertyType);
    if (myElement != null) {
      element.setUseAssignment(myElement.shouldUseAssignment());
    }
    // TODO(b/...): this is necessary until models store the properties they're associated with: for now, the models have only names
    //  while the Dsl elements are annotated with model effect / properties.
    if (myElement != null) {
      element.setModelEffect(myElement.getModelEffect());
    }
    // We need to ensure the parent will be modified so this change takes effect.
    element.setModified();
    myElement = element;
  }

  /**
   * This method has package visibility so that subclasses of {@link ResolvedPropertyModelImpl} can access the element to
   * extract custom types.
   */
  @Nullable
  public GradleDslElement getElement() {
    return getTransform().transform(myElement);
  }

  @Override
  @Nullable
  public GradleDslElement getRawElement() {
    return myElement;
  }

  @NotNull
  protected PropertyTransform getTransform() {
    for (PropertyTransform transform : myTransforms) {
      if (transform.test(myElement, myPropertyHolder)) {
        return transform;
      }
    }
    throw new IllegalStateException("No transforms found for this property model!");
  }

  @NotNull
  List<GradlePropertyModelImpl> dependencies() {
    GradleDslElement element = getElement();
    if (element == null) {
      return Collections.emptyList();
    }

    return element.getResolvedVariables().stream()
      .map(injection -> {
        GradleDslElement injected = injection.getToBeInjected();
        return injected != null ? new GradlePropertyModelImpl(injected) : null;
      }).filter(Objects::nonNull).collect(
        Collectors.toList());
  }
}
