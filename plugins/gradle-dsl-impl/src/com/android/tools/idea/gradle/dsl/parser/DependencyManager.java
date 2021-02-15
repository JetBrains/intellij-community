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
package com.android.tools.idea.gradle.dsl.parser;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.intellij.util.containers.HashSetQueue;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Queue;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Class to manage unresolved dependencies.
 */
public final class DependencyManager {
  @NotNull private final LinkedHashMap<GradleDslFile, List<GradleReferenceInjection>> myUnresolvedReferences = new LinkedHashMap<>();

  public static DependencyManager create() {
    return new DependencyManager();
  }

  private DependencyManager() {
  }

  /**
   * Registers a new unresolved dependency.
   */
  public void registerUnresolvedReference(@NotNull GradleReferenceInjection injection) {
    // Make sure the reference is not resolved.
    assert !injection.isResolved();
    GradleDslFile originFile = injection.getOriginElement().getDslFile();
    List<GradleReferenceInjection> injections = myUnresolvedReferences.getOrDefault(originFile, new ArrayList<>());
    injections.add(injection);
    myUnresolvedReferences.put(originFile, injections);
  }

  /**
   * Unregisters an unresolved dependency.
   */
  public void unregisterUnresolvedReference(@NotNull GradleReferenceInjection injection) {
    // Make sure the reference is not resolved.
    assert !injection.isResolved();
    GradleDslFile originFile = injection.getOriginElement().getDslFile();
    List<GradleReferenceInjection> injections = myUnresolvedReferences.get(originFile); // should always be present
    injections.remove(injection);
  }

  /**
   * Attempt to resolve dependencies related to a change in a given element.
   *
   * @param element the element that has triggered the attempted resolve.
   */
  public void resolveWith(@NotNull GradleDslElement element) {
    Queue<GradleDslFile> queue = new HashSetQueue<>();
    Set<GradleDslFile> seen = new HashSet<>();
    GradleDslElement thisFile = element.getDslFile();

    // attempt re-resolution on the element's file, and all descendants of that file
    queue.add(element.getDslFile());
    GradleDslFile dslFile;
    while ((dslFile = queue.peek()) != null) {
      queue.remove();
      if (!seen.contains(dslFile)) {
        seen.add(dslFile);
        resolveAllIn(dslFile, true);
        queue.addAll(dslFile.getChildModuleDslFiles());
      }
    }

    // the element's file might be applied from any arbitrary project build file

    // TODO(xof): since altering an element can in principle change resolution of all build files which apply the file which contain
    //  this element, for correctness we have to check all those files.  At the moment this is a loop over all the files to check
    //  whether they apply the file containing the element, which scales badly; information about which files are applied where could
    //  probably be cached and updated at parse-time.
    for (GradleDslFile buildFile : myUnresolvedReferences.keySet()) {
      if (!seen.contains(buildFile)) {
        if (buildFile.getApplyDslElement().contains(thisFile)) {
          resolveAllIn(buildFile, true);
        }
      }
    }
  }

  public void resolveAllIn(@NotNull GradleDslFile dslFile, boolean appliedFiles) {
    List<GradleReferenceInjection> injections = myUnresolvedReferences.getOrDefault(dslFile, new ArrayList<>());
    for (Iterator<GradleReferenceInjection> it = injections.iterator(); it.hasNext(); ) {
      GradleReferenceInjection injection = it.next();
      GradleDslElement newElement = injection.getOriginElement().resolveExternalSyntaxReference(injection.getName(), true);
      if (newElement != null) {
        injection.resolveWith(newElement);
        newElement.registerDependent(injection);
        it.remove();
      }
    }
    if (injections.isEmpty()) {
      myUnresolvedReferences.remove(dslFile);
    }
    if (appliedFiles) {
      for (GradleDslFile appliedFile : dslFile.getApplyDslElement()) {
        resolveAllIn(appliedFile, true);
      }
    }
  }

  /**
   * Attempt to resolve all of the current unresolved dependencies.
   */
  public void resolveAll() {
    for (GradleDslFile dslFile : myUnresolvedReferences.keySet()) {
      resolveAllIn(dslFile, false);
    }
  }
}
