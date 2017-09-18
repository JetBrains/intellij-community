/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.tree;

import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.lang.reflect.Array;
import java.util.function.Function;

public class TreePathUtil {
  /**
   * @param parent    the parent path or {@code null} to indicate the root
   * @param component the last path component
   * @return a tree path with all the parent components plus the given component
   */
  @NotNull
  public static TreePath createTreePath(TreePath parent, @NotNull Object component) {
    return parent != null
           ? parent.pathByAddingChild(component)
           : new TreePath(component);
  }

  /**
   * @param path a tree path to convert
   * @return an array with the string representations of path components or {@code null}
   * if the specified path is wrong
   * or a path component is {@code null}
   * or its string representation is {@code null}
   */
  public static String[] convertTreePathToStrings(@NotNull TreePath path) {
    return convertTreePathToArray(path, Object::toString, String.class);
  }

  /**
   * @param path a tree path to convert
   * @return an array with the same path components or {@code null}
   * if the specified path is wrong
   * or a path component is {@code null}
   */
  public static Object[] convertTreePathToArray(@NotNull TreePath path) {
    return convertTreePathToArray(path, Function.identity(), Object.class);
  }

  /**
   * @param path     a tree path to convert
   * @param function a function to convert path components
   * @return an array with the converted path components or {@code null}
   * if the specified path is wrong
   * or a path component is {@code null}
   * or a path component is converted to {@code null}
   */
  public static Object[] convertTreePathToArray(@NotNull TreePath path, @NotNull Function<Object, Object> converter) {
    return convertTreePathToArray(path, converter, Object.class);
  }

  /**
   * @param path     a tree path to convert
   * @param function a function to convert path components
   * @param type     a type of components of the new array
   * @return an array of the specified type with the converted path components or {@code null}
   * if the specified path is wrong
   * or a path component is {@code null}
   * or a path component is converted to {@code null}
   */
  public static <T> T[] convertTreePathToArray(@NotNull TreePath path, @NotNull Function<Object, T> converter, @NotNull Class<T> type) {
    int count = path.getPathCount();
    if (count <= 0) return null;
    //noinspection unchecked
    T[] array = (T[])Array.newInstance(type, count);
    while (path != null && count > 0) {
      Object component = path.getLastPathComponent();
      if (component == null) return null;
      T object = convert(component, converter);
      if (object == null) return null;
      array[--count] = object;
      path = path.getParentPath();
    }
    return path != null || count > 0 ? null : array;
  }

  /**
   * @param array an array of path components to convert
   * @return a tree path with the same path components or {@code null}
   * if the specified array is empty
   * or a path component is {@code null}
   * or a path component is converted to {@code null}
   */
  public static <T> TreePath convertArrayToTreePath(@NotNull T... array) {
    return convertArrayToTreePath(array, Function.identity());
  }

  /**
   * @param array    an array of path components to convert
   * @param function a function to convert path components
   * @return a tree path with the converted path components or {@code null}
   * if the specified array is empty
   * or a path component is {@code null}
   * or a path component is converted to {@code null}
   */
  public static <T> TreePath convertArrayToTreePath(@NotNull T[] array, @NotNull Function<T, Object> converter) {
    int count = array.length;
    if (count <= 0) return null;
    TreePath path = null;
    for (T object : array) {
      Object component = convert(object, converter);
      if (component == null) return null;
      path = createTreePath(path, component);
    }
    return path;
  }

  private static <I, O> O convert(I object, @NotNull Function<I, O> converter) {
    return object == null ? null : converter.apply(object);
  }
}
