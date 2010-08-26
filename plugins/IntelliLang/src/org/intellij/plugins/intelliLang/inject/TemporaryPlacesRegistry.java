/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.intellij.plugins.intelliLang.inject;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.intelliLang.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class TemporaryPlacesRegistry {
  private final Project myProject;
  private final List<TemporaryPlace> myTempPlaces = ContainerUtil.createEmptyCOWList();

  public static TemporaryPlacesRegistry getInstance(final Project project) {
    return ServiceManager.getService(project, TemporaryPlacesRegistry.class);
  }

  public TemporaryPlacesRegistry(final Project project) {
    myProject = project;
  }

  public List<TemporaryPlace> getTempInjectionsSafe() {
    final List<TemporaryPlace> placesToRemove = ContainerUtil.findAll(myTempPlaces, new Condition<TemporaryPlace>() {
      public boolean value(final TemporaryPlace place) {
        return place.elementPointer.getElement() == null;
      }
    });
    if (!placesToRemove.isEmpty()) {
      myTempPlaces.removeAll(placesToRemove);
    }
    return myTempPlaces;
  }

  public List<TemporaryPlace> getTempInjectionsSafe(final PsiLanguageInjectionHost host) {
    return ContainerUtil.findAll(getTempInjectionsSafe(), new Condition<TemporaryPlace>() {
      public boolean value(final TemporaryPlace pair) {
        return pair.elementPointer.getElement() == host;
      }
    });
  }

  public void removeHostWithUndo(final Project project, final PsiLanguageInjectionHost host) {
    final List<TemporaryPlace> places = getTempInjectionsSafe(host);
    if (places.isEmpty()) return;
    Configuration.replaceInjectionsWithUndo(project, Collections.<TemporaryPlace>emptyList(), places, Collections.<PsiElement>emptyList(), new PairProcessor<List<TemporaryPlace>, List<TemporaryPlace>>() {
      public boolean process(final List<TemporaryPlace> add,
                             final List<TemporaryPlace> remove) {
        myTempPlaces.addAll(add);
        myTempPlaces.removeAll(remove);
        return true;
      }
    });
  }

  public void addHostWithUndo(final PsiLanguageInjectionHost host, final InjectedLanguage language) {
    final SmartPointerManager manager = SmartPointerManager.getInstance(myProject);
    final SmartPsiElementPointer<PsiLanguageInjectionHost> pointer = manager.createSmartPsiElementPointer(host);
    final TemporaryPlace place = new TemporaryPlace(language, pointer);
    if (myTempPlaces.contains(place)) return;
    Configuration.replaceInjectionsWithUndo(myProject, Collections.singletonList(place), Collections.<TemporaryPlace>emptyList(), Collections.<PsiElement>emptyList(), new PairProcessor<List<TemporaryPlace>, List<TemporaryPlace>>() {
      public boolean process(final List<TemporaryPlace> add, final List<TemporaryPlace> remove) {
        myTempPlaces.addAll(add);
        myTempPlaces.removeAll(remove);
        return true;
      }
    });
  }

  public static class TemporaryPlace {
    final InjectedLanguage language;
    final SmartPsiElementPointer<PsiLanguageInjectionHost> elementPointer;

    public TemporaryPlace(final InjectedLanguage language, final SmartPsiElementPointer<PsiLanguageInjectionHost> elementPointer) {
      this.language = language;
      this.elementPointer = elementPointer;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final TemporaryPlace place = (TemporaryPlace)o;

      if (!elementPointer.equals(place.elementPointer)) return false;
      if (!language.equals(place.language)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = language.hashCode();
      result = 31 * result + elementPointer.hashCode();
      return result;
    }
  }
}
