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
package org.jetbrains.plugins.groovy.codeInsight;

import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.impl.GutterIconTooltipHelper;
import com.intellij.codeInsight.daemon.impl.LineMarkerNavigator;
import com.intellij.codeInsight.daemon.impl.MarkerType;
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator;
import com.intellij.codeInsight.navigation.ListBackgroundUpdaterTask;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.NullableFunction;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.*;

/**
 * @author Max Medvedev
 */
public class GroovyMarkerTypes {
  static final MarkerType OVERRIDING_PROPERTY_TYPE = new MarkerType("OVERRIDING_PROPERTY_TYPE", psiElement -> {
    final PsiElement parent = psiElement.getParent();
    if (!(parent instanceof GrField)) return null;
    final GrField field = (GrField)parent;

    final List<GrAccessorMethod> accessors = GroovyPropertyUtils.getFieldAccessors(field);
    StringBuilder builder = new StringBuilder();
    builder.append("<html><body>");
    int count = 0;
    String sep = "";
    for (GrAccessorMethod method : accessors) {
      PsiMethod[] superMethods = method.findSuperMethods(false);
      count += superMethods.length;
      if (superMethods.length == 0) continue;
      PsiMethod superMethod = superMethods[0];
      boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
      boolean isSuperAbstract = superMethod.hasModifierProperty(PsiModifier.ABSTRACT);

      @NonNls final String key;
      if (isSuperAbstract && !isAbstract) {
        key = "method.implements.in";
      }
      else {
        key = "method.overrides.in";
      }
      builder.append(sep);
      sep = "<br>";
      composeText(superMethods, DaemonBundle.message(key), builder);
    }
    if (count == 0) return null;
    builder.append("</html></body>");
    return builder.toString();
  }, new LineMarkerNavigator() {
    @Override
    public void browse(MouseEvent e, PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof GrField)) return;
      final GrField field = (GrField)parent;
      final List<GrAccessorMethod> accessors = GroovyPropertyUtils.getFieldAccessors(field);
      final ArrayList<PsiMethod> superMethods = new ArrayList<>();
      for (GrAccessorMethod method : accessors) {
        Collections.addAll(superMethods, method.findSuperMethods(false));
      }
      if (superMethods.isEmpty()) return;
      final PsiMethod[] supers = ContainerUtil.toArray(superMethods, new PsiMethod[superMethods.size()]);
      boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(supers);
      PsiElementListNavigator.openTargets(e, supers, 
                                          DaemonBundle.message("navigation.title.super.method", field.getName()),
                                          DaemonBundle.message("navigation.findUsages.title.super.method", field.getName()),
                                          new MethodCellRenderer(showMethodNames));
    }
  });
  static final MarkerType OVERRIDEN_PROPERTY_TYPE = new MarkerType("OVERRIDEN_PROPERTY_TYPE", element -> {
    PsiElement parent = element.getParent();
    if (!(parent instanceof GrField)) return null;
    final List<GrAccessorMethod> accessors = GroovyPropertyUtils.getFieldAccessors((GrField)parent);

    PsiElementProcessor.CollectElementsWithLimit<PsiMethod> processor = new PsiElementProcessor.CollectElementsWithLimit<>(5);

    for (GrAccessorMethod method : accessors) {
      OverridingMethodsSearch.search(method).forEach(new PsiElementProcessorAdapter<>(processor));
    }
    if (processor.isOverflow()) {
      return DaemonBundle.message("method.is.overridden.too.many");
    }

    PsiMethod[] overridings = processor.toArray(new PsiMethod[processor.getCollection().size()]);
    if (overridings.length == 0) return null;

    Comparator<PsiMethod> comparator = new MethodCellRenderer(false).getComparator();
    Arrays.sort(overridings, comparator);

    String start = DaemonBundle.message("method.is.overriden.header");
    @NonNls String pattern = "&nbsp;&nbsp;&nbsp;&nbsp;{1}";
    return GutterIconTooltipHelper.composeText(overridings, start, pattern);
  }, new LineMarkerNavigator() {
    @Override
    public void browse(MouseEvent e, PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof GrField)) return;
      if (DumbService.isDumb(element.getProject())) {
        DumbService.getInstance(element.getProject()).showDumbModeNotification("Navigation to overriding classes is not possible during index update");
        return;
      }

      final GrField field = (GrField)parent;


      Set<PsiMethod> result = new THashSet<>();
      Processor<PsiMethod> collectProcessor = Processors.cancelableCollectProcessor(result);
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> {
        for (GrAccessorMethod method : GroovyPropertyUtils.getFieldAccessors(field)) {
          OverridingMethodsSearch.search(method).forEach(collectProcessor);
        }
      }), "Searching for overriding methods", true, field.getProject(), (JComponent)e.getComponent())) {
        return;
      }

      PsiMethod[] overridings = result.toArray(PsiMethod.EMPTY_ARRAY);
      if (overridings.length == 0) return;
      String title = DaemonBundle.message("navigation.title.overrider.method", field.getName(), overridings.length);
      boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(overridings);
      MethodCellRenderer renderer = new MethodCellRenderer(showMethodNames);
      Arrays.sort(overridings, renderer.getComparator());
      PsiElementListNavigator.openTargets(e, overridings, title, "Overriding Methods of " + field.getName(), renderer);
    }
  }
  );
  public static final MarkerType GR_OVERRIDING_METHOD = new MarkerType("GR_OVERRIDING_METHOD",
                                                                       (NullableFunction<PsiElement, String>)element -> {
                                                                         PsiElement parent = element.getParent();
                                                                         if (!(parent instanceof GrMethod)) return null;
                                                                         GrMethod method = (GrMethod)parent;

                                                                         Set<PsiMethod> superMethods = collectSuperMethods(method);
                                                                         if (superMethods.isEmpty()) return null;

                                                                         PsiMethod superMethod = superMethods.iterator().next();
                                                                         boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
                                                                         boolean isSuperAbstract = superMethod.hasModifierProperty(PsiModifier.ABSTRACT);

                                                                         final boolean sameSignature = superMethod.getSignature(PsiSubstitutor.EMPTY).equals(method.getSignature(PsiSubstitutor.EMPTY));
                                                                         @NonNls final String key;
                                                                         if (isSuperAbstract && !isAbstract){
                                                                           key = sameSignature ? "method.implements" : "method.implements.in";
                                                                         }
                                                                         else{
                                                                           key = sameSignature ? "method.overrides" : "method.overrides.in";
                                                                         }
                                                                         return GutterIconTooltipHelper.composeText(superMethods, "", DaemonBundle.message(key));
                                                                       }, new LineMarkerNavigator(){
      @Override
      public void browse(MouseEvent e, PsiElement element) {
        PsiElement parent = element.getParent();
        if (!(parent instanceof GrMethod)) return;
        GrMethod method = (GrMethod)parent;

        Set<PsiMethod> superMethods = collectSuperMethods(method);
        if (superMethods.isEmpty()) return;
        PsiElementListNavigator.openTargets(e, superMethods.toArray(new NavigatablePsiElement[superMethods.size()]),
                    DaemonBundle.message("navigation.title.super.method", method.getName()),
                    DaemonBundle.message("navigation.findUsages.title.super.method", method.getName()),
                    new MethodCellRenderer(true));

      }
    });
  public static final MarkerType GR_OVERRIDEN_METHOD = new MarkerType("GR_OVERRIDEN_METHOD",
                                                                      (NullableFunction<PsiElement, String>)element -> {
                                                                        PsiElement parent = element.getParent();
                                                                        if (!(parent instanceof GrMethod)) return null;
                                                                        GrMethod method = (GrMethod)parent;

                                                                        final PsiElementProcessor.CollectElementsWithLimit<PsiMethod> processor =
                                                                          new PsiElementProcessor.CollectElementsWithLimit<>(5);

                                                                        for (GrMethod m : PsiImplUtil.getMethodOrReflectedMethods(method)) {
                                                                          OverridingMethodsSearch.search(m).forEach(new ReadActionProcessor<PsiMethod>() {
                                                                            @Override
                                                                            public boolean processInReadAction(PsiMethod method) {
                                                                              if (method instanceof GrTraitMethod) {
                                                                                return true;
                                                                              }
                                                                              return processor.execute(method);
                                                                            }
                                                                          });
                                                                        }

                                                                        boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

                                                                        if (processor.isOverflow()){
                                                                          return isAbstract ? DaemonBundle.message("method.is.implemented.too.many") : DaemonBundle.message("method.is.overridden.too.many");
                                                                        }

                                                                        PsiMethod[] overridings = processor.toArray(new PsiMethod[processor.getCollection().size()]);
                                                                        if (overridings.length == 0) return null;

                                                                        Comparator<PsiMethod> comparator = new MethodCellRenderer(false).getComparator();
                                                                        Arrays.sort(overridings, comparator);

                                                                        String start = isAbstract ? DaemonBundle.message("method.is.implemented.header") : DaemonBundle.message("method.is.overriden.header");
                                                                        @NonNls String pattern = "&nbsp;&nbsp;&nbsp;&nbsp;{1}";
                                                                        return GutterIconTooltipHelper.composeText(overridings, start, pattern);
                                                                      }, new LineMarkerNavigator(){
      @Override
      public void browse(MouseEvent e, PsiElement element) {
        PsiElement parent = element.getParent();
        if (!(parent instanceof GrMethod)) return;
        if (DumbService.isDumb(element.getProject())) {
          DumbService.getInstance(element.getProject()).showDumbModeNotification("Navigation to overriding classes is not possible during index update");
          return;
        }


        //collect all overrings (including fields with implicit accessors and method with default parameters)
        final GrMethod method = (GrMethod)parent;
        final PsiElementProcessor.CollectElementsWithLimit<PsiMethod> collectProcessor =
          new PsiElementProcessor.CollectElementsWithLimit<>(2, new THashSet<>());
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> {
          for (GrMethod m : PsiImplUtil.getMethodOrReflectedMethods(method)) {
            OverridingMethodsSearch.search(m).forEach(new ReadActionProcessor<PsiMethod>() {
              @Override
              public boolean processInReadAction(PsiMethod psiMethod) {
                if (psiMethod instanceof GrReflectedMethod) {
                  psiMethod = ((GrReflectedMethod)psiMethod).getBaseMethod();
                }
                return collectProcessor.execute(psiMethod);
              }
            });
          }
        }), MarkerType.SEARCHING_FOR_OVERRIDING_METHODS, true, method.getProject(), (JComponent)e.getComponent())) {
          return;
        }

        PsiMethod[] overridings = collectProcessor.toArray(PsiMethod.EMPTY_ARRAY);
        if (overridings.length == 0) return;

        PsiElementListCellRenderer<PsiMethod> renderer = new MethodCellRenderer(!PsiUtil.allMethodsHaveSameSignature(overridings));
        Arrays.sort(overridings, renderer.getComparator());
        final OverridingMethodsUpdater methodsUpdater = new OverridingMethodsUpdater(method, renderer);
        PsiElementListNavigator.openTargets(e, overridings, methodsUpdater.getCaption(overridings.length),
                                            "Overriding Methods of " + method.getName(),
                                            renderer, methodsUpdater);
        
      }
    });

  private GroovyMarkerTypes() {
  }

  private static Set<PsiMethod> collectSuperMethods(GrMethod method) {
    Set<PsiMethod> superMethods = new HashSet<>();
    for (GrMethod m : PsiImplUtil.getMethodOrReflectedMethods(method)) {
      for (PsiMethod superMethod : m.findSuperMethods(false)) {
        if (superMethod instanceof GrReflectedMethod) {
          superMethod = ((GrReflectedMethod)superMethod).getBaseMethod();
        }

        superMethods.add(superMethod);
      }
    }
    return superMethods;
  }

  private static StringBuilder composeText(@NotNull PsiElement[] elements, final String pattern, StringBuilder result) {
    Set<String> names = new LinkedHashSet<>();
    for (PsiElement element : elements) {
      String methodName = ((PsiMethod)element).getName();
      PsiClass aClass = ((PsiMethod)element).getContainingClass();
      String className = aClass == null ? "" : ClassPresentationUtil.getNameForClass(aClass, true);
      names.add(MessageFormat.format(pattern, methodName, className));
    }

    @NonNls String sep = "";
    for (String name : names) {
      result.append(sep);
      sep = "<br>";
      result.append(name);
    }
    return result;
  }

  private static class OverridingMethodsUpdater extends ListBackgroundUpdaterTask {
    private final GrMethod myMethod;

    public OverridingMethodsUpdater(GrMethod method, PsiElementListCellRenderer renderer) {
      super(method.getProject(), MarkerType.SEARCHING_FOR_OVERRIDING_METHODS, createComparatorWrapper(renderer.getComparator()));
      myMethod = method;
    }

    @Override
    public String getCaption(int size) {
      return myMethod.hasModifierProperty(PsiModifier.ABSTRACT) ?
             DaemonBundle.message("navigation.title.implementation.method", myMethod.getName(), size) :
             DaemonBundle.message("navigation.title.overrider.method", myMethod.getName(), size);
    }

    @Override
    public void run(@NotNull final ProgressIndicator indicator) {
      super.run(indicator);
      for (PsiMethod method : PsiImplUtil.getMethodOrReflectedMethods(myMethod)) {
        OverridingMethodsSearch.search(method).forEach(
          new CommonProcessors.CollectProcessor<PsiMethod>() {
            @Override
            public boolean process(PsiMethod psiMethod) {
              if (!updateComponent(com.intellij.psi.impl.PsiImplUtil.handleMirror(psiMethod))) {
                indicator.cancel();
              }
              indicator.checkCanceled();
              return true;
            }
          });
      }
    }
  }
}
