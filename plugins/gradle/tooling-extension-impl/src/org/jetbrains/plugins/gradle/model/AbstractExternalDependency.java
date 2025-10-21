// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.DefaultExternalDependencyId;
import org.jetbrains.plugins.gradle.ExternalDependencyId;
import org.jetbrains.plugins.gradle.tooling.util.BiFunction;
import org.jetbrains.plugins.gradle.tooling.util.BooleanBiFunction;
import org.jetbrains.plugins.gradle.tooling.util.GradleContainerUtil;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public abstract class AbstractExternalDependency implements ExternalDependency {
  private static final long serialVersionUID = 1L;

  private final @NotNull DefaultExternalDependencyId myId;
  private String myScope;
  private @NotNull Collection<? extends ExternalDependency> myDependencies;
  private String mySelectionReason;
  private int myClasspathOrder;
  private boolean myExported;

  public AbstractExternalDependency() {
    this(new DefaultExternalDependencyId());
  }

  public AbstractExternalDependency(ExternalDependencyId id) {
    this(id, null, null);
  }

  public AbstractExternalDependency(
    ExternalDependencyId id,
    String selectionReason,
    Collection<? extends ExternalDependency> dependencies
  ) {
    this(id, selectionReason, dependencies, null, 0, false);
  }

  public AbstractExternalDependency(
    ExternalDependencyId id,
    String selectionReason,
    Collection<? extends ExternalDependency> dependencies,
    String scope,
    int classpathOrder,
    boolean exported
  ) {
    myId = new DefaultExternalDependencyId(id);
    mySelectionReason = selectionReason;
    myDependencies = ModelFactory.createCopy(dependencies);
    myScope = scope;
    myClasspathOrder = classpathOrder;
    myExported = exported;
  }

  public AbstractExternalDependency(ExternalDependency dependency) {
    this(
      dependency.getId(),
      dependency.getSelectionReason(),
      dependency.getDependencies(),
      dependency.getScope(),
      dependency.getClasspathOrder(),
      dependency.getExported()
    );
  }

  @Override
  public @NotNull ExternalDependencyId getId() {
    return myId;
  }

  @Override
  public String getName() {
    return myId.getName();
  }

  public void setName(String name) {
    myId.setName(name);
  }

  @Override
  public String getGroup() {
    return myId.getGroup();
  }

  public void setGroup(String group) {
    myId.setGroup(group);
  }

  @Override
  public String getVersion() {
    return myId.getVersion();
  }

  public void setVersion(String version) {
    myId.setVersion(version);
  }

  @Override
  public @NotNull String getPackaging() {
    return myId.getPackaging();
  }

  public void setPackaging(@NotNull String packaging) {
    myId.setPackaging(packaging);
  }

  @Override
  public @Nullable String getClassifier() {
    return myId.getClassifier();
  }

  public void setClassifier(@Nullable String classifier) {
    myId.setClassifier(classifier);
  }

  @Override
  public @Nullable String getSelectionReason() {
    return mySelectionReason;
  }

  public void setSelectionReason(String selectionReason) {
    this.mySelectionReason = selectionReason;
  }

  @Override
  public int getClasspathOrder() {
    return myClasspathOrder;
  }

  public void setClasspathOrder(int order) {
    myClasspathOrder = order;
  }

  @Override
  public String getScope() {
    return myScope;
  }

  public void setScope(String scope) {
    this.myScope = scope;
  }

  @Override
  public @NotNull Collection<? extends ExternalDependency> getDependencies() {
    return myDependencies;
  }

  public void setDependencies(@NotNull Collection<? extends ExternalDependency> dependencies) {
    myDependencies = dependencies;
  }

  @Override
  public boolean getExported() {
    return myExported;
  }

  public void setExported(boolean exported) {
    myExported = exported;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AbstractExternalDependency)) return false;
    AbstractExternalDependency that = (AbstractExternalDependency)o;
    return Objects.equal(myId, that.myId) &&
           Objects.equal(myScope, that.myScope) &&
           myClasspathOrder == that.myClasspathOrder &&
           equal(myDependencies, that.myDependencies);
  }

  private static boolean equal(@NotNull Collection<? extends ExternalDependency> dependencies1,
                               @NotNull Collection<? extends ExternalDependency> dependencies2) {
    final DependenciesIterator iterator1 = new DependenciesIterator(dependencies1);
    final DependenciesIterator iterator2 = new DependenciesIterator(dependencies2);
    return GradleContainerUtil.match(iterator1, iterator2, new BooleanBiFunction<AbstractExternalDependency, AbstractExternalDependency>() {
      @Override
      public Boolean fun(AbstractExternalDependency o1, AbstractExternalDependency o2) {
        if (!Objects.equal(iterator1.myProcessedStructure, iterator2.myProcessedStructure)) return false;
        return Objects.equal(o1.myId, o2.myId) && Objects.equal(o1.myScope, o2.myScope);
      }
    });
  }

  @Override
  public int hashCode() {
    return 31 + Objects.hashCode(myId, myScope, myClasspathOrder);
  }

  protected static int calcFilesPathsHashCode(@NotNull Iterable<File> iterable) {
    return GradleContainerUtil.reduce(iterable, 0, new BiFunction<Integer, Integer, File>() {
      @Override
      public Integer fun(Integer currentResult, File item) {
        return 31 * currentResult + (item == null ? 0 : item.getPath().hashCode());
      }
    });
  }

  private static final class DependenciesIterator implements Iterator<AbstractExternalDependency> {
    private final Set<AbstractExternalDependency> mySeenDependencies;
    private final ArrayDeque<ExternalDependency> myToProcess;
    private final ArrayList<Integer> myProcessedStructure;

    private DependenciesIterator(@NotNull Collection<? extends ExternalDependency> dependencies) {
      mySeenDependencies = Collections.newSetFromMap(new IdentityHashMap<>());
      myToProcess = new ArrayDeque<>(dependencies);
      myProcessedStructure = new ArrayList<>();
    }

    @Override
    public boolean hasNext() {
      AbstractExternalDependency dependency = (AbstractExternalDependency)myToProcess.peekFirst();
      if (dependency == null) return false;
      if (mySeenDependencies.contains(dependency)) {
        myToProcess.removeFirst();
        return hasNext();
      }
      return true;
    }

    @Override
    public AbstractExternalDependency next() {
      AbstractExternalDependency dependency = (AbstractExternalDependency)myToProcess.removeFirst();
      if (mySeenDependencies.add(dependency)) {
        myToProcess.addAll(dependency.myDependencies);
        myProcessedStructure.add(dependency.myDependencies.size());
        return dependency;
      }
      else {
        return next();
      }
    }
  }
}

