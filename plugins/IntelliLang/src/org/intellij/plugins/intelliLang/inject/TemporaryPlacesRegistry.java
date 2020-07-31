// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.source.tree.injected.changesHandler.CommonInjectedFileChangesHandlerKt;
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

@Service
public final class TemporaryPlacesRegistry {
  private final Project myProject;
  private final List<TempPlace> myTempPlaces = ContainerUtil.createLockFreeCopyOnWriteList();

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

    @Override
    public Class<?> @NotNull [] getPatternClasses() {
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

  public static TemporaryPlacesRegistry getInstance(@NotNull Project project) {
    return project.getService(TemporaryPlacesRegistry.class);
  }

  public TemporaryPlacesRegistry(@NotNull Project project) {
    myProject = project;
  }

  private List<TempPlace> getInjectionPlacesSafe() {
    long modificationCount = PsiModificationTracker.SERVICE.getInstance(myProject).getModificationCount();
    if (myPsiModificationCounter == modificationCount) {
      return myTempPlaces;
    }

    myPsiModificationCounter = modificationCount;
    final List<TempPlace> placesToRemove = ContainerUtil.findAll(myTempPlaces, place -> {
      PsiLanguageInjectionHost element = place.elementPointer.getElement();

      if (element == null) {
        Segment range = place.elementPointer.getRange();
        if (range == null) return true;
        PsiFile file = place.elementPointer.getContainingFile();
        if (file == null) return true;
        PsiLanguageInjectionHost newHost = CommonInjectedFileChangesHandlerKt.getInjectionHostAtRange(file, range);
        if (newHost == null) return true;

        newHost.putUserData(LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE, place.language);
        place.elementPointer = SmartPointerManager.createPointer(newHost);
      }
      else if (!element.isValidHost()) {
        element.putUserData(LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE, null);
        return true;
      }
      else {
        element.putUserData(LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE, place.language);
      }
      return false;
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
    hosts.add(host); // because `enumerate` doesn't handle reference injections

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
    host.getManager().dropPsiCaches();
  }

  public boolean removeHostWithUndo(final Project project, final PsiLanguageInjectionHost host) {
    InjectedLanguage prevLanguage = host.getUserData(LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE);
    if (prevLanguage == null) return false;
    SmartPointerManager manager = SmartPointerManager.getInstance(myProject);
    SmartPsiElementPointer<PsiLanguageInjectionHost> pointer = manager.createSmartPsiElementPointer(host);
    TempPlace place = new TempPlace(prevLanguage, pointer);
    TempPlace nextPlace = new TempPlace(null, pointer);
    Configuration.replaceInjectionsWithUndo(
      project, host.getContainingFile(), nextPlace, place, false, Collections.emptyList(),
      (add, remove) -> {
        addInjectionPlace(add);
        return true;
      });
    return true;
  }

  public void addHostWithUndo(final PsiLanguageInjectionHost host, final InjectedLanguage language) {
    InjectedLanguage prevLanguage = host.getUserData(LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE);
    SmartPsiElementPointer<PsiLanguageInjectionHost> pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(host);
    TempPlace prevPlace = new TempPlace(prevLanguage, pointer);
    TempPlace place = new TempPlace(language, pointer);
    Configuration.replaceInjectionsWithUndo(
      myProject, host.getContainingFile(), place, prevPlace, false, Collections.emptyList(),
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
    final InjectedLanguage language;
    SmartPsiElementPointer<PsiLanguageInjectionHost> elementPointer;

    TempPlace(InjectedLanguage language, SmartPsiElementPointer<PsiLanguageInjectionHost> elementPointer) {
      this.language = language;
      this.elementPointer = elementPointer;
    }
  }
}
