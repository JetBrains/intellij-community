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
package org.jetbrains.plugins.groovy.codeInsight;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.AllOverridingMethodsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableDeclarationBase;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.*;

/**
 * @author ilyas
 * Same logic as for Java LMP
 */
public class GroovyLineMarkerProvider extends JavaLineMarkerProvider {
  private static final Logger LOG = Logger.getInstance(GroovyLineMarkerProvider.class);


  private static final MarkerType OVERRIDING_PROPERTY_TYPE = new MarkerType(new Function<PsiElement, String>() {
    @Nullable
    @Override
    public String fun(PsiElement psiElement) {
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
    }
  }, new LineMarkerNavigator() {
    public void browse(MouseEvent e, PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof GrField)) return;
      final GrField field = (GrField)parent;
      final List<GrAccessorMethod> accessors = GroovyPropertyUtils.getFieldAccessors(field);
      final ArrayList<PsiMethod> superMethods = new ArrayList<PsiMethod>();
      for (GrAccessorMethod method : accessors) {
        Collections.addAll(superMethods, method.findSuperMethods(false));
      }
      if (superMethods.size() == 0) return;
      final PsiMethod[] supers = ContainerUtil.toArray(superMethods, new PsiMethod[superMethods.size()]);
      boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(supers);
      PsiElementListNavigator.openTargets(e, supers, DaemonBundle.message("navigation.title.super.method", field.getName()), new MethodCellRenderer(showMethodNames));
    }
  });

  private static final MarkerType OVERRIDEN_PROPERTY_TYPE = new MarkerType(new Function<PsiElement, String>() {
    @Nullable
    @Override
    public String fun(PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof GrField)) return null;
      final List<GrAccessorMethod> accessors = GroovyPropertyUtils.getFieldAccessors((GrField)parent);

      PsiElementProcessor.CollectElementsWithLimit<PsiMethod> processor = new PsiElementProcessor.CollectElementsWithLimit<PsiMethod>(5);

      for (GrAccessorMethod method : accessors) {
        OverridingMethodsSearch.search(method, method.getUseScope(), true).forEach(new PsiElementProcessorAdapter<PsiMethod>(processor));
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
    }
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


      final CommonProcessors.CollectProcessor<PsiMethod> collectProcessor = new CommonProcessors.CollectProcessor<PsiMethod>(new THashSet<PsiMethod>());
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          for (GrAccessorMethod method : GroovyPropertyUtils.getFieldAccessors(field)) {
            OverridingMethodsSearch.search(method, method.getUseScope(), true).forEach(collectProcessor);
          }
        }
      }, "Searching for overriding methods", true, field.getProject(), (JComponent)e.getComponent())) {
        return;
      }

      PsiMethod[] overridings = collectProcessor.toArray(PsiMethod.EMPTY_ARRAY);
      if (overridings.length == 0) return;
      String title = DaemonBundle.message("navigation.title.overrider.method", field.getName(), overridings.length);
      boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(overridings);
      MethodCellRenderer renderer = new MethodCellRenderer(showMethodNames);
      Arrays.sort(overridings, renderer.getComparator());
      PsiElementListNavigator.openTargets(e, overridings, title, renderer);
    }
  }
  );


  private static StringBuilder composeText(@NotNull PsiElement[] elements, final String pattern, StringBuilder result) {
    Set<String> names = new LinkedHashSet<String>();
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


  public GroovyLineMarkerProvider(DaemonCodeAnalyzerSettings daemonSettings, EditorColorsManager colorsManager) {
    super(daemonSettings, colorsManager);
  }

  @Override
  public LineMarkerInfo getLineMarkerInfo(final PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiNameIdentifierOwner) {
      if (parent instanceof GrField) {
        for (GrAccessorMethod method : GroovyPropertyUtils.getFieldAccessors((GrField)parent)) {
          MethodSignatureBackedByPsiMethod superSignature = null;
          try {
            superSignature = SuperMethodsSearch.search(method, null, true, false).findFirst();
          }
          catch (IndexNotReadyException e) {
            //some searchers (EJB) require indices. What shall we do?
          }
          if (superSignature != null) {
            boolean overrides = method.hasModifierProperty(PsiModifier.ABSTRACT) == superSignature.getMethod().hasModifierProperty(PsiModifier.ABSTRACT);
            final Icon icon = overrides ? OVERRIDING_METHOD_ICON : IMPLEMENTING_METHOD_ICON;
            final MarkerType type = OVERRIDING_PROPERTY_TYPE;
            return new LineMarkerInfo<PsiElement>(element, element.getTextRange(), icon, Pass.UPDATE_ALL, type.getTooltip(), type.getNavigationHandler(),
                                                  GutterIconRenderer.Alignment.LEFT);
          }
        }
      }

      final ASTNode node = element.getNode();
      if (node != null && TokenSets.PROPERTY_NAMES.contains(node.getElementType())) {
        return super.getLineMarkerInfo(((PsiNameIdentifierOwner)parent).getNameIdentifier());
      }
    }
    //need to draw method separator above docComment
    if (myDaemonSettings.SHOW_METHOD_SEPARATORS && element.getFirstChild() == null) {
      PsiElement element1 = element;
      boolean isMember = false;
      while (element1 != null && !(element1 instanceof PsiFile) && element1.getPrevSibling() == null) {
        element1 = element1.getParent();
        if (element1 instanceof PsiMember || element1 instanceof GrVariableDeclarationBase) {
          isMember = true;
          break;
        }
      }
      if (isMember && !(element1 instanceof PsiAnonymousClass || element1.getParent() instanceof PsiAnonymousClass)) {
        boolean drawSeparator = false;
        int category = getGroovyCategory(element1);
        for (PsiElement child = element1.getPrevSibling(); child != null; child = child.getPrevSibling()) {
          int category1 = getGroovyCategory(child);
          if (category1 == 0) continue;
          drawSeparator = category != 1 || category1 != 1;
          break;
        }

        if (drawSeparator) {
          GrDocComment comment = null;
          if (element1 instanceof GrDocCommentOwner) {
            comment = ((GrDocCommentOwner)element1).getDocComment();
          }
          LineMarkerInfo info =
            new LineMarkerInfo<PsiElement>(element, comment != null ? comment.getTextRange() : element.getTextRange(), null,
                                           Pass.UPDATE_ALL, FunctionUtil.<Object, String>nullConstant(), null,
                                           GutterIconRenderer.Alignment.RIGHT);
          EditorColorsScheme scheme = myColorsManager.getGlobalScheme();
          info.separatorColor = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
          info.separatorPlacement = SeparatorPlacement.TOP;
          return info;
        }
      }
    }

    return super.getLineMarkerInfo(element);
  }

  private static int getGroovyCategory(PsiElement element) {
    if (element instanceof GrVariableDeclarationBase) {
      GrVariable[] variables = ((GrVariableDeclarationBase)element).getVariables();
      if (variables.length == 1 && variables[0] instanceof GrField && variables[0].getInitializerGroovy() instanceof GrClosableBlock) {
        return 2;
      }
    }

    return JavaLineMarkerProvider.getCategory(element);
  }

  @Override
  public void collectSlowLineMarkers(final List<PsiElement> elements, final Collection<LineMarkerInfo> result) {
    List<GrField> fields = new ArrayList<GrField>();
    for (PsiElement element : elements) {
      if (!(element instanceof GrField)) continue;
      fields.add((GrField)element);
    }
    collectOverridingMethods(fields, result);

    super.collectSlowLineMarkers(elements, result);
  }

  private static void collectOverridingMethods(final List<GrField> fields, Collection<LineMarkerInfo> result) {
    final Set<GrField> overridden = new HashSet<GrField>();
    final HashSet<GrAccessorMethod> accessors = new HashSet<GrAccessorMethod>();
    Set<PsiClass> classes = new THashSet<PsiClass>();
    for (GrField field : fields) {
      ProgressManager.checkCanceled();
      final PsiClass parentClass = field.getContainingClass();
      if (!"java.lang.Object".equals(parentClass.getQualifiedName())) {
        classes.add(parentClass);
      }
      accessors.addAll(GroovyPropertyUtils.getFieldAccessors(field));
    }

    for (final PsiClass aClass : classes) {
      try {
        AllOverridingMethodsSearch.search(aClass).forEach(new Processor<Pair<PsiMethod, PsiMethod>>() {
          public boolean process(final Pair<PsiMethod, PsiMethod> pair) {
            ProgressManager.checkCanceled();

            final PsiMethod superMethod = pair.getFirst();
            if (isCorrectTarget(superMethod) && isCorrectTarget(pair.getSecond())) {
              if (accessors.remove(superMethod)) {
                LOG.assertTrue(superMethod instanceof GrAccessorMethod);
                overridden.add(((GrAccessorMethod)superMethod).getProperty());
              }
            }
            return !fields.isEmpty();
          }
        });
      }
      catch (IndexNotReadyException ignored) {
      }
    }

    for (GrField field : overridden) {
      final Icon icon = OVERRIDEN_METHOD_MARKER_RENDERER;
      PsiElement range;

      range = field.getNameIdentifierGroovy();
      final MarkerType type = OVERRIDEN_PROPERTY_TYPE;
      LineMarkerInfo info = new LineMarkerInfo<PsiElement>(range, range.getTextRange(), icon, Pass.UPDATE_OVERRIDEN_MARKERS, type.getTooltip(), type.getNavigationHandler(),
                                                           GutterIconRenderer.Alignment.RIGHT);
      result.add(info);
    }
  }

  private static boolean isCorrectTarget(PsiMethod superMethod) {
    final PsiElement navigationElement = superMethod.getNavigationElement();
    return superMethod.isPhysical() || navigationElement.isPhysical() && !(navigationElement instanceof PsiClass);
  }
}
