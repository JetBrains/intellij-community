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

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import org.intellij.plugins.intelliLang.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Gregory.Shrago
 */
public class TemporaryPlacesRegistry {
  private final Project myProject;
  private final List<TempPlace> myTempPlaces = ContainerUtil.createLockFreeCopyOnWriteList();

  private final PsiModificationTracker myModificationTracker;
  private volatile long myPsiModificationCounter;

  private final LanguageInjectionSupport myInjectorSupport = new AbstractLanguageInjectionSupport() {
    @NotNull
    @Override
    public String getId() {
      return "temp";
    }

    @Override
    public boolean isApplicableTo(PsiLanguageInjectionHost host) {
      return true;
    }

    @NotNull
    @Override
    public Class[] getPatternClasses() {
      return ArrayUtil.EMPTY_CLASS_ARRAY;
    }

    @Override
    public boolean addInjectionInPlace(Language language, PsiLanguageInjectionHost host) {
      addHostWithUndo(host, InjectedLanguage.create(language.getID()));
      return true;
    }

    @Override
    public boolean removeInjectionInPlace(PsiLanguageInjectionHost psiElement) {
      return removeHostWithUndo(myProject, psiElement);
    }
  };

  public static TemporaryPlacesRegistry getInstance(final Project project) {
    return ServiceManager.getService(project, TemporaryPlacesRegistry.class);
  }

  public TemporaryPlacesRegistry(Project project, PsiModificationTracker modificationTracker) {
    myProject = project;
    myModificationTracker = modificationTracker;
  }

  private List<TempPlace> getInjectionPlacesSafe() {
    long modificationCount = myModificationTracker.getModificationCount();
    if (myPsiModificationCounter == modificationCount) return myTempPlaces;
    myPsiModificationCounter = modificationCount;
    final List<TempPlace> placesToRemove = ContainerUtil.findAll(myTempPlaces, place -> {
      PsiLanguageInjectionHost element = place.elementPointer.getElement();
      if (element == null) {
        return true;
      }
      else {
        element.putUserData(LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE, place.language);
        return false;
      }
    });
    if (!placesToRemove.isEmpty()) {
      myTempPlaces.removeAll(placesToRemove);
    }
    return myTempPlaces;
  }

  private void addInjectionPlace(TempPlace place) {
    PsiLanguageInjectionHost host = place.elementPointer.getElement();
    if (host == null) return;

    Set<PsiLanguageInjectionHost> hosts = new SmartHashSet<>();

    InjectedLanguageManager.getInstance(myProject).enumerate(host, (injectedPsi, places) -> {
      injectedPsi.putUserData(LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE, place.language);
      for (PsiLanguageInjectionHost.Shred shred: places) {
        hosts.add(shred.getHost());
      }
    });

    List<TempPlace> injectionPoints = getInjectionPlacesSafe();
    for (TempPlace tempPlace : injectionPoints) {
      if (hosts.contains(tempPlace.elementPointer.getElement())) {
        injectionPoints.remove(tempPlace);
        break;
      }
    }
    if (place.language != null) {
      injectionPoints.add(place);
    }
    host.putUserData(LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE, place.language);
  }

  public boolean removeHostWithUndo(final Project project, final PsiLanguageInjectionHost host) {
    InjectedLanguage prevLanguage = host.getUserData(LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE);
    if (prevLanguage == null) return false;
    SmartPointerManager manager = SmartPointerManager.getInstance(myProject);
    SmartPsiElementPointer<PsiLanguageInjectionHost> pointer = manager.createSmartPsiElementPointer(host);
    TempPlace place = new TempPlace(prevLanguage, pointer);
    TempPlace nextPlace = new TempPlace(null, pointer);
    Configuration.replaceInjectionsWithUndo(
      project, nextPlace, place, Collections.emptyList(),
      (add, remove) -> {
        addInjectionPlace(add);
        return true;
      });
    return true;
  }

  public void addHostWithUndo(final PsiLanguageInjectionHost host, final InjectedLanguage language) {
    InjectedLanguage prevLanguage = host.getUserData(LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE);
    SmartPointerManager manager = SmartPointerManager.getInstance(myProject);
    SmartPsiElementPointer<PsiLanguageInjectionHost> pointer = manager.createSmartPsiElementPointer(host);
    TempPlace prevPlace = new TempPlace(prevLanguage, pointer);
    TempPlace place = new TempPlace(language, pointer);
    Configuration.replaceInjectionsWithUndo(
      myProject, place, prevPlace, Collections.emptyList(),
      (add, remove) -> {
        addInjectionPlace(add);
        return true;
      });
  }

  public LanguageInjectionSupport getLanguageInjectionSupport() {
    return myInjectorSupport;
  }

  @Nullable
  public InjectedLanguage getLanguageFor(@NotNull PsiLanguageInjectionHost host, PsiFile containingFile) {
    PsiLanguageInjectionHost originalHost = CompletionUtilCoreImpl.getOriginalElement(host, containingFile);
    PsiLanguageInjectionHost injectionHost = originalHost == null ? host : originalHost;
    getInjectionPlacesSafe();
    return injectionHost.getUserData(LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE);
  }

  private static class TempPlace {
    public final InjectedLanguage language;
    public final SmartPsiElementPointer<PsiLanguageInjectionHost> elementPointer;

    public TempPlace(InjectedLanguage language, SmartPsiElementPointer<PsiLanguageInjectionHost> elementPointer) {
      this.language = language;
      this.elementPointer = elementPointer;
    }
  }
}
