// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

/**
 * Root types that can be queried from OrderEntry.
 *
 * @see OrderEntry
 */
public class OrderRootType {
  private final String myName;

  public static final ExtensionPointName<OrderRootType> EP_NAME = ExtensionPointName.create("com.intellij.orderRootType");

  protected OrderRootType(@NotNull String name) {
    myName = name;
  }

  /**
   * Classpath without output directories for modules. Includes:
   * <ul>
   * <li>classes roots for libraries and jdk</li>
   * <li>recursively for module dependencies: only exported items</li>
   * </ul>
   */
  public static final OrderRootType CLASSES = new PersistentOrderRootType("CLASSES", "classPath", null, "classPathEntry");

  /**
   * Sources. Includes:
   * <ul>
   * <li>production and test source roots for modules</li>
   * <li>source roots for libraries and jdk</li>
   * <li>recursively for module dependencies: only exported items</li>
   * </ul>
   */
  public static final OrderRootType SOURCES = new PersistentOrderRootType("SOURCES", "sourcePath", null, "sourcePathEntry");

  /**
   * Exactly two built-in persistent root types: {@link #CLASSES} and {@link #SOURCES}.
   * <p>
   * This list is fixed for compatibility: these fields are public static constants and
   * cannot be turned into EP registrations without breaking third‑party binaries.
   * Do not add more built-ins. New persistent types must be contributed via
   * {@link #EP_NAME} and are combined with this list in {@link #getAllPersistentTypesList()}.
   */
  private static final @NotNull @Unmodifiable List<PersistentOrderRootType> PREDEFINED_PERSISTENT_TYPES =
    List.of((PersistentOrderRootType)CLASSES, (PersistentOrderRootType)SOURCES);

  /**
   * Generic documentation order root type
   */
  public static final OrderRootType DOCUMENTATION = new DocumentationRootType();

  /**
   * A temporary solution to exclude DOCUMENTATION from getAllTypes() and handle it only in special
   * cases if supported by LibraryType.
   */
  public static class DocumentationRootType extends OrderRootType {
    public DocumentationRootType() {
      super("DOCUMENTATION");
    }

    @Override
    public boolean skipWriteIfEmpty() {
      return true;
    }
  }

  public @NotNull String name() {
    return myName;
  }

  /**
   * Whether this root type should be skipped when writing a Library if the root type doesn't contain
   * any roots.
   *
   * @return true if empty root type should be skipped, false otherwise.
   */
  @ApiStatus.Internal
  public boolean skipWriteIfEmpty() {
    return false;
  }

  /**
   * <h3>Obsolescence notice</h3>
   * Returns the same elements as {@link #getAllPersistentTypesList()}, but as an array.
   * Kept for compatibility; prefer {@link #getAllPersistentTypesList()}.
   */
  @ApiStatus.Obsolete
  public static OrderRootType @NotNull [] getAllTypes() {
    return getAllPersistentTypes();
  }

  /**
   * <h3>Obsolescence notice</h3>
   * Returns the same elements as {@link #getAllPersistentTypesList()}, but as an array.
   * Kept for compatibility; prefer {@link #getAllPersistentTypesList()}.
   */
  @ApiStatus.Obsolete
  public static PersistentOrderRootType @NotNull [] getAllPersistentTypes() {
    return getAllPersistentTypesList().toArray(new PersistentOrderRootType[0]);
  }

  public static @NotNull @Unmodifiable List<PersistentOrderRootType> getAllPersistentTypesList() {
    return ContainerUtil.concat(PREDEFINED_PERSISTENT_TYPES,
                                ContainerUtil.filterIsInstance(EP_NAME.getExtensionList(), PersistentOrderRootType.class));
  }

  public static @NotNull @Unmodifiable List<PersistentOrderRootType> getSortedRootTypes() {
    List<PersistentOrderRootType> allTypes = new ArrayList<>(getAllPersistentTypesList());
    allTypes.sort((o1, o2) -> o1.name().compareToIgnoreCase(o2.name()));
    return allTypes;
  }

  protected static @NotNull <T> T getOrderRootType(@NotNull Class<? extends T> orderRootTypeClass) {
    List<OrderRootType> rootTypes = EP_NAME.getExtensionList();
    for (OrderRootType rootType : rootTypes) {
      if (orderRootTypeClass.isInstance(rootType)) {
        @SuppressWarnings("unchecked") T t = (T)rootType;
        return t;
      }
    }
    assert false : "Root type " + orderRootTypeClass + " not found. All roots: " + rootTypes;
    return null;
  }

  @Override
  public final int hashCode() {
    return super.hashCode();
  }

  @Override
  public final boolean equals(Object obj) {
    return super.equals(obj);
  }

  @Override
  public String toString() {
    return "Root " + name();
  }
}