/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.util.GradleNameElementUtil;
import com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.ModificationAware;
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import java.util.stream.Collectors;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.DERIVED;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.isNonExpressionPropertiesElement;
import static com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement.*;

public abstract class GradleDslElementImpl implements GradleDslElement, ModificationAware {
  @NotNull protected GradleNameElement myName;

  @Nullable protected GradleDslElement myParent;

  @NotNull protected List<GradlePropertiesDslElement> myHolders = new ArrayList<>();

  @NotNull private final GradleDslFile myDslFile;

  @Nullable private PsiElement myPsiElement;

  @Nullable private GradleDslClosure myClosureElement;
  @Nullable private GradleDslClosure myUnsavedClosure;

  private long myLastCommittedModificationCount;
  private long myModificationCount;

  // Whether or not that DslElement should be represented with the assignment syntax i.e "name = 'value'" or
  // the method call syntax i.e "name 'value'". This is needed since on some element types as we do not carry
  // the information to make this distinction. GradleDslElement will set this to a default of false.
  protected boolean myUseAssignment;

  @NotNull private PropertyType myElementType;

  @NotNull protected final List<GradleReferenceInjection> myDependencies = new ArrayList<>();
  @NotNull protected final List<GradleReferenceInjection> myDependents = new ArrayList<>();

  @Nullable private ModelEffectDescription myModelEffectDescription;

  /**
   * Creates an instance of a {@link GradleDslElement}
   *
   * @param parent     the parent {@link GradleDslElement} of this element. The parent element should always be a not-null value except if
   *                   this element is the root element, i.e a {@link GradleDslFile}.
   * @param psiElement the {@link PsiElement} of this dsl element.
   * @param name       the name of this element.
   */
  protected GradleDslElementImpl(@Nullable GradleDslElement parent, @Nullable PsiElement psiElement, @NotNull GradleNameElement name) {
    assert parent != null || this instanceof GradleDslFile;

    myParent = parent;
    myPsiElement = psiElement;
    myName = name;


    if (parent == null) {
      myDslFile = (GradleDslFile)this;
    }
    else {
      myDslFile = parent.getDslFile();
    }

    myUseAssignment = false;
    // Default to DERIVED, this is overwritten in the parser if required for the given element type.
    myElementType = DERIVED;
  }

  @Override
  public void setParsedClosureElement(@NotNull GradleDslClosure closureElement) {
    myClosureElement = closureElement;
  }

  @Override
  public void setNewClosureElement(@Nullable GradleDslClosure closureElement) {
    myUnsavedClosure = closureElement;
    setModified();
  }

  @Override
  @Nullable
  public GradleDslClosure getUnsavedClosure() {
    return myUnsavedClosure;
  }

  @Override
  @Nullable
  public GradleDslClosure getClosureElement() {
    return myUnsavedClosure == null ? myClosureElement : myUnsavedClosure;
  }

  @Override
  @NotNull
  public String getName() {
    return myModelEffectDescription == null ? myName.name() : myModelEffectDescription.property.name;
  }

  @Override
  @NotNull
  public String getQualifiedName() {
    // Don't include the name of the parent if this element is a direct child of the file.
    if (myParent == null || myParent instanceof GradleDslFile) {
      return GradleNameElementUtil.escape(getName());
    }

    String ourName = getName();
    return myParent.getQualifiedName() + (ourName.isEmpty() ? "" : "." + GradleNameElementUtil.escape(ourName));
  }

  @Override
  @NotNull
  public String getFullName() {
    if (myModelEffectDescription == null) {
      return myName.fullName();
    }
    else {
      List<String> parts = myName.qualifyingParts();
      parts.add(getName());
      return GradleNameElement.createNameFromParts(parts);
    }
  }

  @Override
  @NotNull
  public GradleNameElement getNameElement() {
    return myName;
  }

  @Override
  public void rename(@NotNull String newName) {
    rename(Arrays.asList(newName));
  }

  @Override
  public void rename(@NotNull List<String> hierarchicalName) {
    myName.rename(hierarchicalName);
    setModified();

    // If we are a GradleDslSimpleExpression we need to ensure our dependencies are correct.
    if (!(this instanceof GradleDslSimpleExpression)) {
      return;
    }

    List<GradleReferenceInjection> dependents = getDependents();
    unregisterAllDependants();

    reorder();

    // The property we renamed could have been shadowing another one. Attempt to re-resolve all dependents.
    dependents.forEach(e -> e.getOriginElement().resolve());

    // The new name could also create new dependencies, we need to make sure to resolve them.
    getDslFile().getContext().getDependencyManager().resolveWith(this);
  }

  @Override
  @Nullable
  public GradleDslElement getParent() {
    return myParent;
  }

  @Override
  public void setParent(@NotNull GradleDslElement parent) {
    myParent = parent;
  }

  @Override
  @NotNull
  public List<GradlePropertiesDslElement> getHolders() {
    return myHolders;
  }

  @Override
  public void addHolder(@NotNull GradlePropertiesDslElement holder) {
    myHolders.add(holder);
  }

  @Override
  @Nullable
  public PsiElement getPsiElement() {
    return myPsiElement;
  }

  @Override
  public void setPsiElement(@Nullable PsiElement psiElement) {
    myPsiElement = psiElement;
  }

  @Override
  public boolean shouldUseAssignment() {
    return myUseAssignment;
  }

  @Override
  public void setUseAssignment(boolean useAssignment) {
    myUseAssignment = useAssignment;
  }

  @Override
  @NotNull
  public PropertyType getElementType() {
    return myElementType;
  }

  @Override
  public void setElementType(@NotNull PropertyType propertyType) {
    myElementType = propertyType;
  }

  @Override
  @NotNull
  public GradleDslFile getDslFile() {
    return myDslFile;
  }

  @Override
  @NotNull
  public List<GradleReferenceInjection> getResolvedVariables() {
    ImmutableList.Builder<GradleReferenceInjection> resultBuilder = ImmutableList.builder();
    for (GradleDslElement child : getChildren()) {
      resultBuilder.addAll(child.getResolvedVariables());
    }
    return resultBuilder.build();
  }

  @Override
  @Nullable
  public GradleDslElement requestAnchor(@NotNull GradleDslElement element) {
    return null;
  }

  @Override
  @Nullable
  public GradleDslElement getAnchor() {
    return myParent == null ? null : myParent.requestAnchor(this);
  }

  @Override
  @Nullable
  public PsiElement create() {
    return myDslFile.getWriter().createDslElement(this);
  }

  @Override
  @Nullable
  public PsiElement move() {
    return myDslFile.getWriter().moveDslElement(this);
  }

  @Override
  public void delete() {
    for (GradleDslElement element : getChildren()) {
      element.delete();
    }

    this.getDslFile().getWriter().deleteDslElement(this);
  }

  @Override
  public void setModified() {
    modify();
    if (myParent != null) {
      myParent.setModified();
    }
  }

  @Override
  public boolean isModified() {
    return getLastCommittedModificationCount() != getModificationCount();
  }

  @Override
  public boolean isBlockElement() {
    return false;
  }

  @Override
  public boolean isInsignificantIfEmpty() {
    return true;
  }

  @Override
  @NotNull
  public abstract Collection<GradleDslElement> getChildren();

  @Override
  public final void applyChanges() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    apply();
    commit();
  }

  protected abstract void apply();

  @Override
  public final void resetState() {
    reset();
    commit();
  }

  protected abstract void reset();

  @Override
  @NotNull
  public List<GradleDslElement> getContainedElements(boolean includeProperties) {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Map<String, GradleDslElement> getInScopeElements() {
    Map<String, GradleDslElement> results = new LinkedHashMap<>();

    if (isNonExpressionPropertiesElement(this)) {
      GradlePropertiesDslElement thisElement = (GradlePropertiesDslElement)this;
      results.putAll(thisElement.getVariableElements());
    }

    // Trace parents finding any variable elements present.
    GradleDslElement currentElement = this;
    while (currentElement != null && currentElement.getParent() != null) {
      currentElement = currentElement.getParent();
      if (isNonExpressionPropertiesElement(currentElement)) {
        GradlePropertiesDslElement element = (GradlePropertiesDslElement)currentElement;
        results.putAll(element.getVariableElements());
      }
    }

    // Get Ext properties from the GradleDslFile, and the EXT properties from the buildscript.
    if (currentElement instanceof GradleDslFile) {
      GradleDslFile file = (GradleDslFile)currentElement;
      while (file != null) {
        ExtDslElement ext = file.getPropertyElement(ExtDslElement.EXT);
        if (ext != null) {
          results.putAll(ext.getPropertyElements());
        }
        // Add properties files properties
        GradleDslFile propertiesFile = file.getSiblingDslFile();
        if (propertiesFile != null) {
          // Only properties with no qualifier are picked up by build scripts.
          Map<String, GradleDslElement> filteredProperties =
            propertiesFile.getPropertyElements().entrySet().stream().filter(entry -> !entry.getKey().contains("."))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
          results.putAll(filteredProperties);
        }
        // Add BuildScriptExt properties.
        BuildScriptDslElement buildScriptElement = file.getPropertyElement(BUILDSCRIPT);
        if (buildScriptElement != null) {
          ExtDslElement buildScriptExt = buildScriptElement.getPropertyElement(ExtDslElement.EXT);
          if (buildScriptExt != null) {
            results.putAll(buildScriptExt.getPropertyElements());
          }
        }

        file = file.getParentModuleDslFile();
      }
    }

    return results;
  }

  @Override
  @NotNull
  public <T extends BuildModelNotification> T notification(@NotNull NotificationTypeReference<T> type) {
    return getDslFile().getContext().getNotificationForType(myDslFile, type);
  }

  @Override
  public void registerDependent(@NotNull GradleReferenceInjection injection) {
    assert injection.isResolved() && injection.getToBeInjected() == this;
    myDependents.add(injection);
  }

  @Override
  public void unregisterDependent(@NotNull GradleReferenceInjection injection) {
    assert injection.isResolved() && injection.getToBeInjected() == this;
    assert myDependents.contains(injection);
    myDependents.remove(injection);
  }

  @Override
  public void unregisterAllDependants() {
    // We need to create a new array to avoid concurrent modification exceptions.
    myDependents.forEach(e -> {
      // Break the dependency.
      e.resolveWith(null);
      // Register with DependencyManager
      getDslFile().getContext().getDependencyManager().registerUnresolvedReference(e);
    });
    myDependents.clear();
  }

  @Override
  @NotNull
  public List<GradleReferenceInjection> getDependents() {
    return new ArrayList<>(myDependents);
  }

  @Override
  @NotNull
  public List<GradleReferenceInjection> getDependencies() {
    return new ArrayList<>(myDependencies);
  }

  @Override
  public void updateDependenciesOnAddElement(@NotNull GradleDslElement newElement) {
    newElement.resolve();
    newElement.getDslFile().getContext().getDependencyManager().resolveWith(newElement);
  }

  @Override
  public void updateDependenciesOnReplaceElement(@NotNull GradleDslElement oldElement, @NotNull GradleDslElement newElement) {
    // Switch dependents to point to the new element.
    List<GradleReferenceInjection> injections = oldElement.getDependents();
    oldElement.unregisterAllDependants();
    injections.forEach(e -> e.resolveWith(newElement));
    // Register all the dependents with this new element.
    injections.forEach(newElement::registerDependent);

    // Go though our dependencies and unregister us as a dependent.
    oldElement.getResolvedVariables().forEach(e -> {
      GradleDslElement toBeInjected = e.getToBeInjected();
      if (toBeInjected != null) {
        toBeInjected.unregisterDependent(e);
      }
    });
  }

  @Override
  public void updateDependenciesOnRemoveElement(@NotNull GradleDslElement oldElement) {
    List<GradleReferenceInjection> dependents = oldElement.getDependents();
    oldElement.unregisterAllDependants();

    // The property we remove could have been shadowing another one. Attempt to re-resolve all dependents.
    dependents.forEach(e -> e.getOriginElement().resolve());

    // Go though our dependencies and unregister us as a dependent.
    oldElement.getResolvedVariables().forEach(e -> {
      GradleDslElement toBeInjected = e.getToBeInjected();
      if (toBeInjected != null) {
        toBeInjected.unregisterDependent(e);
      }
    });
  }

  @Override
  public void resolve() {
  }

  protected void reorder() {
    if (myParent instanceof ExtDslElement) {
      ((ExtDslElement)myParent).reorderAndMaybeGetNewIndex(this);
    }
  }

  @Override
  public long getModificationCount() {
    return myModificationCount;
  }

  public long getLastCommittedModificationCount() {
    return myLastCommittedModificationCount;
  }

  @Override
  public void modify() {
    myModificationCount++;
    myDependents.forEach(e -> e.getOriginElement().modify());
  }

  public void commit() {
    myLastCommittedModificationCount = myModificationCount;
  }

  @Nullable
  public static String getPsiText(@NotNull PsiElement psiElement) {
    return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> psiElement.getText());
  }

  @Override
  public boolean isNewEmptyBlockElement() {
    if (myPsiElement != null) {
      return false;
    }

    if (!isBlockElement() || !isInsignificantIfEmpty()) {
      return false;
    }

    Collection<GradleDslElement> children = getContainedElements(true);
    if (children.isEmpty()) {
      return true;
    }

    for (GradleDslElement child : children) {
      if (!child.isNewEmptyBlockElement()) {
        return false;
      }
    }

    return true;
  }

  @Override
  @NotNull
  public ImmutableMap<Pair<String, Integer>, ModelEffectDescription> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return ImmutableMap.of();
  }

  @Nullable
  @Override
  public ModelEffectDescription getModelEffect() {
    return myModelEffectDescription;
  }

  @Override
  public void setModelEffect(@Nullable ModelEffectDescription effect) {
    myModelEffectDescription = effect;
  }

  @Nullable
  @Override
  public ModelPropertyDescription getModelProperty() {
    return myModelEffectDescription == null ? null : myModelEffectDescription.property;
  }
}
